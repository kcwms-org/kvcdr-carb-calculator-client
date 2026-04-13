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

The 403 is thrown from `CarbApiService.analyze()` (step 3, POST /analyze), not from the storage upload (step 2). However, `CarbRepository.analyzeFood()` has a try-catch at a high level that catches any exception and rethrows it as a generic "Storage upload failed" error, conflating all failures into a misleading message.

Looking at the code structure:
- Lines 83–98: GET presign + PUT storage (wrappeed in `if (imageFile != null)`)
- Lines 102–107: POST /analyze (inside the same `if`)
- Lines 111–113: DELETE cleanup

The `carbApiService.analyze()` call (line 102) can throw if the response is not 2xx. This exception bubbles up and is caught by `CaptureViewModel.onAnalyze()`, which calls `submissionLogRepository.logRequest()` with the captured headers/body.

## Phase 1 — Diagnosis: More Specific Error Messages

### Step 1 — Distinguish between storage and analysis errors

The problem is the blanket "Storage upload failed" message masks which step actually failed. Add more specific error handling:

Instead of a general try-catch, wrap each major step with its own error handler:

**In `CarbRepository.analyzeFood()` (lines 79–124):**

```kotlin
// Step 1: get presigned upload URL
val presign = try {
    carbApiService.presign()
} catch (e: Exception) {
    error("Failed to presign upload: ${e.message}")
}

// Step 2: PUT image directly to object storage
val putRequest = Request.Builder()
    .url(presign.uploadUrl)
    .apply { presign.requiredHeaders.forEach { (k, v) -> addHeader(k, v) } }
    .put(imageBytes.toRequestBody("image/jpeg".toMediaType()))
    .build()

val putResponse = storageHttpClient.newCall(putRequest).execute()
if (!putResponse.isSuccessful) {
    val code = putResponse.code
    putResponse.close()
    error("Storage upload failed: HTTP $code")
}
putResponse.close()

// Step 3: analyze via URL with datetime
val result = try {
    carbApiService.analyze(
        image = null,
        imageUrl = presign.imageUrl.toRequestBody("text/plain".toMediaType()),
        text = textBody,
        datetime = datetimeBody,
    )
} catch (e: Exception) {
    error("Analysis failed: ${e.message}")
}
```

This surfaces which step failed:
- "Failed to presign upload" → step 1
- "Storage upload failed: HTTP 403" → step 2 (e.g., S3 issue)
- "Analysis failed: ..." → step 3 (backend rejection)

The `CaptureViewModel.onAnalyze()` catch block will now receive the specific error message.

## Phase 2 — Root Cause Analysis (once error is specific)

Once the error message says "Analysis failed: HTTP 403" (or with details from the backend), the root cause is in the `POST /analyze` call. This is a **backend issue** — the backend is rejecting the analyze request.

Possible backend causes:
- The `imageUrl` parameter is invalid or the image hasn't been written to storage yet
- The backend authentication/authorization check is failing (e.g., session expired, user not authorized)
- The image format or size doesn't meet backend requirements
- The backend's presign validation failed (e.g., wrong bucket, wrong path)

**Backend action:** Check the backend logs for the `/analyze` endpoint. The 403 response body (XML or JSON) should contain a specific error code or message. File a detailed issue on `kcwms-org/kvcdr-carb-calculator#31` with:
1. The exact HTTP 403 response body
2. Backend logs from the analyze endpoint
3. Whether presign/PUT are succeeding but analyze is being rejected

## Verification

1. Deploy the `CarbRepository` changes (more specific error messages) to a test device.
2. Reproduce the 403: take a photo, tap Analyze. The error message on the Capture screen should now show "Analysis failed: HTTP 403" or more specific details.
3. Open History → find the error log card → expand "Response" → copy the full response body from the POST /analyze call.
4. Share that response with the backend team on `kcwms-org/kvcdr-carb-calculator#31`.
5. Backend team debugs the analyze endpoint and determines why it's rejecting the request (despite successful presign + PUT).
6. Once backend is fixed: redeploy and verify success. A successful analyze returns HTTP 200 with the analysis result.
7. Confirm the full flow: DELETE cleanup fires, and the analysis result is displayed on the Result screen.
8. Regression: submit a second image immediately after — confirm consistent behavior.
