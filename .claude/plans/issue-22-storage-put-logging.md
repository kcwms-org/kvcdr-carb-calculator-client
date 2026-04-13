# Plan: Log S3 PUT Request/Response Headers

**Context:** When image upload fails with HTTP 403, the app shows the error to the user but logs contain no details about the S3 PUT itself — only the presign GET headers are logged (or nothing). This is because the `@Named("storage")` OkHttpClient used for the S3 PUT has no capture interceptor, and the PUT response is closed and discarded before `error()` is thrown. The user wants all HTTP operations logged, including the S3 PUT.

**Related issue:** kcwms-org/kvcdr-carb-calculator-client#19

---

## Approach

Capture the S3 PUT request/response headers inline in `CarbRepository` (local variables, not shared singleton). On failure, throw a typed `StorageUploadException` carrying those headers. Extend `AnalyzeFoodResult` and `AnalysisResultCache` with PUT capture fields so the success path is also covered. In `CaptureViewModel`, combine presign + PUT headers into labelled sections before writing the submission log. No DB schema changes.

---

## Implementation Steps

### 1. `CarbRepository.kt` — add `StorageUploadException` + capture PUT inline

Add nested class alongside `AnalyzeFoodResult`:
```kotlin
class StorageUploadException(
    message: String,
    val putRequestHeaders: String?,
    val putResponseHeaders: String?,
    val putResponseBody: String?,
    val httpStatusCode: Int,
) : Exception(message)
```

Extend `AnalyzeFoodResult` with four new nullable fields:
```kotlin
data class AnalyzeFoodResult(
    val analysisResult: AnalysisResult,
    val requestHeaders: String?,
    val responseHeaders: String?,
    val responseBody: String?,
    val putRequestHeaders: String?,    // S3 PUT — null for text-only path
    val putResponseHeaders: String?,
    val putResponseBody: String?,
    val putHttpStatusCode: Int?,
)
```

Replace lines 86–98 (the raw PUT block) with inline capture before executing:
```kotlin
// Capture PUT request headers
val putReqHdr = buildString {
    append("${putRequest.method} ${putRequest.url}\n")
    putRequest.headers.forEach { (k, v) -> append("$k: $v\n") }
    val size = putRequest.body?.contentLength() ?: -1L
    append("[image body: ${if (size >= 0) "$size bytes" else "unknown size"}]")
}.trimEnd()

val putResponse = storageHttpClient.newCall(putRequest).execute()
val putRespHdr = buildString {
    append("${putResponse.protocol} ${putResponse.code} ${putResponse.message}\n")
    putResponse.headers.forEach { (k, v) -> append("$k: $v\n") }
}.trimEnd()
val putRespBody = try { putResponse.peekBody(Long.MAX_VALUE).string().takeIf { it.isNotBlank() } } catch (_: Exception) { null }
val putCode = putResponse.code

if (!putResponse.isSuccessful) {
    putResponse.close()
    throw StorageUploadException(
        message = "Storage upload failed: HTTP $putCode",
        putRequestHeaders = putReqHdr,
        putResponseHeaders = putRespHdr,
        putResponseBody = putRespBody,
        httpStatusCode = putCode,
    )
}
putResponse.close()
```

Update the `AnalyzeFoodResult(...)` constructor call at the bottom of `analyzeFood` to populate the four new PUT fields from local variables (null on text-only path).

**File:** `app/src/main/java/com/kevcoder/carbcalculator/data/repository/CarbRepository.kt`

---

### 2. `AnalysisResultCache.kt` — add PUT capture fields

Add four new nullable fields, extend `put()` signature, add getters, clear in `clear()`:
```kotlin
private var putRequestHeaders: String? = null
private var putResponseHeaders: String? = null
private var putResponseBody: String? = null
private var putHttpStatusCode: Int? = null

fun put(result, requestHeaders, responseHeaders, responseBody,
        putRequestHeaders, putResponseHeaders, putResponseBody, putHttpStatusCode)

fun getPutRequestHeaders(): String? = putRequestHeaders
fun getPutResponseHeaders(): String? = putResponseHeaders
fun getPutResponseBody(): String? = putResponseBody
fun getPutHttpStatusCode(): Int? = putHttpStatusCode
```

**File:** `app/src/main/java/com/kevcoder/carbcalculator/data/repository/AnalysisResultCache.kt`

---

### 3. `CaptureViewModel.kt` — pass PUT fields to cache; combine headers on error

**Success path** — update `resultCache.put(...)` call to pass the four new PUT fields from `analyzed`.

**Error path** — add a private helper to combine two labelled header sections:
```kotlin
private fun combineSections(labelA: String, a: String?, labelB: String, b: String?): String? {
    if (a == null && b == null) return null
    return buildString {
        if (a != null) append("=== $labelA ===\n$a")
        if (b != null) { if (a != null) append("\n\n"); append("=== $labelB ===\n$b") }
    }
}
```

Replace the catch block header reads (lines 90–92) with:
```kotlin
val (reqHdr, respHdr, respBody) = when (e) {
    is CarbRepository.StorageUploadException -> Triple(
        combineSections("PRESIGN GET", carbApiCapture.requestHeaders, "S3 PUT", e.putRequestHeaders),
        combineSections("PRESIGN GET", carbApiCapture.responseHeaders, "S3 PUT", e.putResponseHeaders),
        e.putResponseBody,
    )
    else -> Triple(carbApiCapture.requestHeaders, carbApiCapture.responseHeaders, carbApiCapture.responseBody)
}
```
Pass `reqHdr`, `respHdr`, `respBody` to `submissionLogRepository.logRequest(...)`.

**File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/capture/CaptureViewModel.kt`

---

### 4. `ResultViewModel.kt` — include PUT headers from cache in log

Both `logRequest` calls (lines 87–89 and 109–111) use `resultCache.getRequestHeaders()` etc. Update them to use combined presign + PUT sections, using the same `combineSections` helper (either duplicate or extract to a shared utility):
```kotlin
requestHeaders = combineSections("PRESIGN GET", resultCache.getRequestHeaders(), "S3 PUT", resultCache.getPutRequestHeaders()),
responseHeaders = combineSections("PRESIGN GET", resultCache.getResponseHeaders(), "S3 PUT", resultCache.getPutResponseHeaders()),
responseBody = resultCache.getPutResponseBody() ?: resultCache.getResponseBody(),
```

**File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/result/ResultViewModel.kt`

---

### 5. `CaptureViewModelTest.kt` — update `AnalyzeFoodResult` constructions

Two test constructions (lines 81–86 and 126–131) need four new nullable fields added with `null` values. Also add a test for the `StorageUploadException` path verifying that combined headers are passed to `logRequest`.

**File:** `app/src/test/java/com/kevcoder/carbcalculator/ui/capture/CaptureViewModelTest.kt`

---

## Files Modified

| File | Change |
|---|---|
| `data/repository/CarbRepository.kt` | Add `StorageUploadException`; extend `AnalyzeFoodResult`; capture PUT inline; throw typed exception |
| `data/repository/AnalysisResultCache.kt` | Add four PUT capture fields, extend `put()`, add getters, clear in `clear()` |
| `ui/capture/CaptureViewModel.kt` | Pass PUT fields to cache; add `combineSections` helper; use it in catch block |
| `ui/result/ResultViewModel.kt` | Use `combineSections` for both `logRequest` calls |
| `ui/capture/CaptureViewModelTest.kt` | Update `AnalyzeFoodResult` constructions; add `StorageUploadException` test |

No changes to: `NetworkModule.kt`, `CarbApiCapture.kt`, `CarbApiCaptureInterceptor.kt`, `SubmissionLogRepository.kt`, DB schema, or migrations.

---

## Verification

1. `docker compose run --rm build ./gradlew test` — all unit tests pass
2. `docker compose run --rm build ./gradlew assembleDebug` — clean build
3. Manual: trigger an upload with an invalid/expired presigned URL (or temporarily break the PUT URL) → error log in History screen shows **both** `=== PRESIGN GET ===` and `=== S3 PUT ===` sections with full headers
4. Manual: successful upload → submission log in History shows combined presign + PUT headers
