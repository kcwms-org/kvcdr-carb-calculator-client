# Issue #19 — Storage Upload Failed: HTTP 403

**Status:** Open

## Context

When analyzing a food photo, the PUT upload to DigitalOcean Spaces (S3-compatible presigned URL) returns HTTP 403. The user sees "Storage upload failed: HTTP 403" on the Capture screen with the Analyze button disabled.

The upload flow in `CarbRepository.analyzeFood()` (`data/repository/CarbRepository.kt:79–115`):
1. **GET /presign** → returns `uploadUrl`, `imageUrl`, `key`, `requiredHeaders`
2. **PUT {uploadUrl}** → uploads JPEG bytes directly to object storage — **this step 403s**
3. **POST /analyze** → backend analyzes by image URL
4. **DELETE /upload/{key}** → cleanup

PR #23 (merged) added `CarbApiCaptureInterceptor` to `storageHttpClient` so the PUT exchange is now captured in the in-app submission log. However, the S3 XML error response body — which contains the machine-readable error code (`SignatureDoesNotMatch`, `AccessDenied`, `RequestTimeTooSkewed`) — is not surfaced in the thrown error message. It is captured in `carbApiCapture.responseBody`, but the error is thrown before `analyzeFood()` returns.

Related backend issue: `kcwms-org/kvcdr-carb-calculator#31`.

## Files to Change

- `app/src/main/java/com/kevcoder/carbcalculator/data/repository/CarbRepository.kt`

## Phase 1 — Diagnosis: Surface the S3 XML Error Body

### Step 1 — Read the response body before closing (`CarbRepository.kt:93–97`)

Currently:

```kotlin
if (!putResponse.isSuccessful) {
    val code = putResponse.code
    putResponse.close()
    error("Storage upload failed: HTTP $code")
}
```

The response body is closed without being read into the error message. The interceptor has already called `peekBody()` and deposited the XML into `carbApiCapture.responseBody` — read from there before throwing:

```kotlin
if (!putResponse.isSuccessful) {
    val code = putResponse.code
    putResponse.close()
    val errorSnippet = carbApiCapture.responseBody
        ?.lines()
        ?.lastOrNull { it.isNotBlank() }
        ?.take(200)
        ?: "(no body)"
    error("Storage upload failed: HTTP $code — $errorSnippet")
}
```

This surfaces the S3 XML (truncated to 200 chars) in:
- The `CaptureUiState.Error` message on the Capture screen
- The `errorMessage` written to the submission log in the catch block of `CaptureViewModel.onAnalyze()`

The full XML is already accessible via the History screen's "Response" section (the `HttpSection` expand button and copy-to-clipboard). No UI changes needed.

## Phase 2 — Root Cause Analysis by Error Code

Once the XML is visible, match the `<Code>` element:

### `SignatureDoesNotMatch`

The PUT request's signature doesn't match what the server expected. Most likely cause: the `Content-Type` in the PUT body doesn't exactly match what the backend signed for.

Currently `CarbRepository.kt:89` hardcodes `"image/jpeg"`:

```kotlin
.put(imageBytes.toRequestBody("image/jpeg".toMediaType()))
```

If `presign.requiredHeaders` contains a `Content-Type` key, use that value instead:

```kotlin
val contentType = presign.requiredHeaders["Content-Type"] ?: "image/jpeg"
.put(imageBytes.toRequestBody(contentType.toMediaType()))
```

### `RequestTimeTooSkewed`

The presigned URL has expired between GET /presign and the PUT (AWS/DO Spaces max skew: 15 minutes; short TTLs can be much less).

**Client fix:** None — requires backend to increase the presigned URL TTL. Coordinate via `kcwms-org/kvcdr-carb-calculator#31`.

### `AccessDenied` or empty body

IAM/bucket policy issue — no write permission, or a CDN/WAF is stripping auth query params.

**Client fix:** None — infrastructure change needed on the backend. Report to `kcwms-org/kvcdr-carb-calculator#31`.

## Coordination with Backend

After surfacing the XML, share the `<Code>` and `<Message>` from the History log (copy-to-clipboard on the Response section) in a comment on `kcwms-org/kvcdr-carb-calculator#31`.

| S3 `<Code>` | Client action | Backend action |
|---|---|---|
| `SignatureDoesNotMatch` | Use `Content-Type` from `requiredHeaders` | Confirm what Content-Type was signed for; include it in `requiredHeaders` |
| `RequestTimeTooSkewed` | None | Increase presigned URL TTL to ≥ 5 min |
| `AccessDenied` | None | Fix IAM role / bucket policy |
| Empty body | None | Check CDN / WAF / bucket ACL |

## Verification

1. Deploy the `CarbRepository` error message change to a test device.
2. Reproduce the 403: take a photo, tap Analyze. The error message on the Capture screen should now show the S3 XML snippet.
3. Open History → find the error log card → expand "Response" → copy the full XML. Identify `<Code>`.
4. Apply the appropriate fix (client or backend per the table above).
5. Verify success: a successful PUT returns HTTP 200 or 204. `analyzeFood()` must complete without throwing.
6. Confirm the full flow: POST /analyze receives `imageUrl` and returns results; DELETE cleanup fires.
7. Regression: submit a second image immediately after — confirm no presigned URL replay/caching issue.
8. Check the submission log in History: `status = "success"` with all three intercepted calls logged (GET /presign, PUT storage, POST /analyze).
