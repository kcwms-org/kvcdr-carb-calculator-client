# Issue #30 — Resize and compress camera images before uploading to /analyze

## Summary

The carb-calculator backend is dropping its server-side presigned upload flow (kcwms-org/kvcdr-carb-calculator#35). Going forward, `POST /analyze` only accepts an inline multipart `image` field, and DigitalOcean App Platform caps the request body at ~1 MB. The Android client must resize and recompress camera photos to stay well under that cap (target 200–500 KB) while preserving enough fidelity for Claude vision to estimate carbs accurately.

## Root cause / context

`CarbRepository.analyzeFood` (`app/src/main/java/com/kevcoder/carbcalculator/data/repository/CarbRepository.kt:64`) currently:

1. Calls `carbApiService.presign()` to get an upload URL.
2. PUTs the JPEG bytes to S3-compatible storage.
3. Calls `analyze(image_url=...)` with the resulting URL.
4. Fires a cleanup `DELETE /upload/{key}`.

The existing `compressImage(file, quality)` (line 233) only re-encodes via `Bitmap.compress(JPEG, q, ...)` — there's no `inSampleSize` decode and no max-edge resize. A modern phone capture (≥ 12 MP) easily exceeds 1 MB even at quality 80, and on the new flow it would be rejected at the App Platform edge (HTTP 413 / 400) before the API ever sees it.

`CarbApiService.analyze` (`app/src/main/java/com/kevcoder/carbcalculator/data/remote/carbapi/CarbApiService.kt:19`) already exposes the `@Part image: MultipartBody.Part?` field, so no Retrofit signature change is needed.

## Proposed approach

**1. Add a downscale + compress helper** in `CarbRepository.kt` (or extract to a new `data/util/ImageProcessor.kt` if it grows). Pipeline:

- First decode pass with `BitmapFactory.Options.inJustDecodeBounds = true` to read source dimensions without allocating pixels.
- Compute `inSampleSize` (a power of 2) so the decoded bitmap's longest edge is ≥ 1280 px but as small as possible — this keeps peak memory low on big photos.
- Second decode pass with that `inSampleSize`.
- If the decoded longest edge is still > 1280 px, do a final `Bitmap.createScaledBitmap` to exactly cap it at 1280 px on the longest edge (preserve aspect ratio).
- `bitmap.compress(JPEG, quality, ByteArrayOutputStream())` with the existing `imageQuality` setting (default 80, user-configurable in Settings).
- Recycle bitmaps; close streams.

**2. Replace the presign/PUT/analyze chain with a direct multipart POST** in `analyzeFood`:

- Delete the `presign()` → `storageHttpClient.put()` → `analyze(image_url=...)` → `deleteUpload(key)` block.
- Instead build a `MultipartBody.Part.createFormData("image", "capture.jpg", processedBytes.toRequestBody("image/jpeg".toMediaType()))` and pass it as the `image` part of `carbApiService.analyze(...)`.
- Keep the text-only branch unchanged (no image → no `image` part).

**3. Clean up obsolete code paths.** Once the presign flow is gone:

- Remove `presign()`, `deleteUpload()`, and `PresignResponse` from `CarbApiService.kt` / `CarbApiModels.kt` if nothing else uses them.
- Remove the `@Named("storage") OkHttpClient` injection from `CarbRepository` and the corresponding provider in `NetworkModule` if it has no other consumers.
- Drop the `applicationScope.launch { deleteUpload(...) }` fire-and-forget block.

**4. Add a size guard.** After compression, log the final byte size and (optionally) refuse to upload if it exceeds 900 KB — surface a friendly error rather than letting the App Platform return 413. This is a belt-and-suspenders check; with ≤ 1280 px / quality 80 we should land in 200–500 KB.

**5. Tests.** Add a unit test for the new helper that verifies:

- A synthetic 4000×3000 bitmap is reduced to longest edge ≤ 1280 px.
- Output bytes are < 1 MB at quality 80.
- A small (e.g. 800×600) bitmap is left at its original dimensions (no upscaling).

Use Robolectric for `Bitmap`/`BitmapFactory` if not already wired up; otherwise extract the math (sample-size calculation, target dimensions) to a pure-Kotlin function and unit-test that piece directly.

## Risks & open questions

- **Memory on very large source files.** Confirm `inSampleSize` is computed before the full decode — without it, decoding a 50 MP image into a `Bitmap` first will OOM on low-RAM devices.
- **EXIF orientation.** `BitmapFactory.decodeFile` ignores EXIF rotation. If the existing flow relied on the storage layer / Claude to handle orientation, we may need to read EXIF (`ExifInterface`) and rotate the bitmap before compression. Worth verifying with a portrait-mode capture during manual testing.
- **Cleanup scope.** The presign endpoints may still be referenced elsewhere (e.g., a future re-upload flow, or tests). Grep before deleting; if there's any consumer, keep the API definitions and only stop calling them from `analyzeFood`.
- **Image quality setting in Settings screen.** The `imageQuality` user setting (default 80) currently affects the JPEG compress quality. Decide whether to also expose max-edge as a setting, or hardcode 1280 per the issue.
- **Backend deploy ordering.** This change assumes kcwms-org/kvcdr-carb-calculator#35 is merged and deployed before the new APK ships. If the client lands first, the old backend will reject multipart `image` (or vice versa). Coordinate the rollout — confirm the backend PR's status before merging this one.

## Acceptance criteria

- [ ] A photo from a modern phone (≥ 12 MP) uploads in well under 1 MB (target 200–500 KB).
- [ ] `POST /analyze` succeeds end-to-end against the production App Platform deployment with a real camera capture.
- [ ] Carb-estimation accuracy is unchanged on a visual sanity check across several meals.
- [ ] No remaining references to the presign / direct-PUT flow in `CarbRepository` (orphan code in `CarbApiService` is acceptable to defer if other consumers exist, but flag it).
- [ ] Unit tests cover the resize/compress math for a representative source size.
