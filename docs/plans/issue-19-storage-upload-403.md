# Issue #19 — Storage Upload Failed: HTTP 403

**Status:** Open

## Context

When analyzing a food photo, the user sees "Storage upload failed: HTTP 403" on the Capture screen with the Analyze button disabled. However, the submission logs show the PUT upload (step 2) succeeds with HTTP 200.

The upload flow in `CarbRepository.analyzeFood()` (`data/repository/CarbRepository.kt:79–115`):
1. **GET /presign** → returns `uploadUrl`, `imageUrl`, `key`, `requiredHeaders` — ✅ 200
2. **PUT {uploadUrl}** → uploads JPEG bytes directly to object storage — ✅ 200
3. **POST /analyze** → backend analyzes by image URL — **this step 403s** ❌
4. **DELETE /upload/{key}** → cleanup

**Key insight:** The 403 is coming from the **POST /analyze** call (step 3), not the storage upload. The error message "Storage upload failed" is misleading — the actual failure is in the analyze step.

The issue is that `CarbRepository.analyzeFood()` throws `error("Storage upload failed: HTTP $code")` at line 96 for **any** non-2xx response code in the presign/PUT flow, but the 403 is actually coming from step 3 (POST /analyze). The error message conflates storage with analysis.

Related backend issue: `kcwms-org/kvcdr-carb-calculator#31`.

## Files to Change

- `app/src/main/java/com/kevcoder/carbcalculator/data/repository/CarbRepository.kt`

## Root Cause

The PUT to S3 is returning HTTP 403 with `x-amz-error-code: SignatureDoesNotMatch`. 

**The problem:** The presigned URL was signed for specific headers: `host;x-amz-acl` (per `X-Amz-SignedHeaders`). However, the client is sending **additional headers** that were not in the signed scope:
- `Content-Type: image/jpeg` — **NOT in the signed headers** ❌
- `x-amz-acl: public-read` — **IS in the signed headers** ✅

When S3/DigitalOcean Spaces receives the PUT, it recomputes the signature with all the headers sent by the client. Since the client sent `Content-Type` but it wasn't in the original signature scope, the computed signature doesn't match. Result: HTTP 403 `SignatureDoesNotMatch`.

**Why is this happening?** At `CarbRepository.kt:89`, the code builds the request body with:
```kotlin
.put(imageBytes.toRequestBody("image/jpeg".toMediaType()))
```

OkHttp automatically adds a `Content-Type` header from the request body's media type. This header is NOT in `presign.requiredHeaders` (which only contains the headers the backend expects to be signed). The backend signed for specific headers, but the client is sending extras.

## Solution: Don't Set Content-Type on the Request Body

The fix is to **remove** the media type from the request body so OkHttp doesn't inject the `Content-Type` header. Then, if `presign.requiredHeaders` contains `Content-Type`, add it explicitly as a header so it's in the signed scope.

**In `CarbRepository.analyzeFood()` (lines 86–90):**

Current code:
```kotlin
val putRequest = Request.Builder()
    .url(presign.uploadUrl)
    .apply { presign.requiredHeaders.forEach { (k, v) -> addHeader(k, v) } }
    .put(imageBytes.toRequestBody("image/jpeg".toMediaType()))
    .build()
```

Fixed code:
```kotlin
val putRequest = Request.Builder()
    .url(presign.uploadUrl)
    .apply { presign.requiredHeaders.forEach { (k, v) -> addHeader(k, v) } }
    .put(imageBytes.toRequestBody())  // No media type → no automatic Content-Type header
    .build()
```

**Why this works:** By calling `toRequestBody()` with no argument, OkHttp creates a `RequestBody` with no media type. OkHttp will NOT inject a `Content-Type` header. Since we're already calling `presign.requiredHeaders.forEach { (k, v) -> addHeader(k, v) }`, any `Content-Type` that the backend signed for is already added explicitly. The signature matches, and S3/Spaces accepts the PUT.

## Verification

1. Make the one-line change in `CarbRepository.analyzeFood()` (remove media type from `toRequestBody()`)
2. Rebuild and deploy to a test device
3. Reproduce the flow: take a photo, tap Analyze
4. Expected result: PUT returns HTTP 200, image uploads successfully, analyze returns 200 with results
5. Verify in History: the log should show successful presign + successful upload with no 403 errors
6. Regression test: submit 2–3 more images to confirm the fix is stable

