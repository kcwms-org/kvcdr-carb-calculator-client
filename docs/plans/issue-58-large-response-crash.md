# Issue #58 — app crashes with large request or response

## Summary

The app crashes when a carb-API exchange is large. The issue's only content is an
attached raw HTTP response (`large_response.txt`) from `POST /analyze`. Its JSON body holds
the usual `items` / `total_carbs_grams` fields, plus an `images[].data` field with the
full photo as a base64-encoded JPEG (a 1280×960 image, so roughly 1 MB+ of text). The app
stores that whole body as debug text, keeps it in memory, saves it to Room, and shows,
copies, and exports it. Several of those paths fail once the text is around 1–2 MB.

## Root cause / context

`CarbApiCaptureInterceptor` already swaps request image parts for an `[image data: N bytes]`
placeholder. The response body, though, is captured whole with
`response.peekBody(Long.MAX_VALUE)`. That string then flows through
`CarbApiCapture` → `AnalyzeFoodResult.responseBody` → `AnalysisResultCache` →
`SubmissionLogRepository` → `submission_logs.responseBody` (Room, DB version 7).

**Confirmed by the reporter (issue comment, 2026-11-06):** the crash happens only on the
History screen when expanding the Request or Response section, and not every time.
Device: moto g (2025), Android 16. This points to crash point 3 below.
`HttpSection` (`HistoryScreen.kt` ~L485) puts the full stored string in one monospace
`Text` with no `maxLines` or length limit, inside a `LazyColumn` item. For entries from
photo submissions, the response holds the base64 image (1 MB+), which wraps to tens of
thousands of lines. Measuring that `Text` can go past Compose's `Constraints` size limit
(`IllegalArgumentException: Can't represent a size of … in Constraints`) or run out of
memory. Text-only submissions have small responses, which explains why it happens only
sometimes. No stack trace yet, since the reporter can't easily capture one, so the exact
exception is unconfirmed.

Other candidate crash points (still worth fixing, since the same large text reaches them):

1. **Room / SQLite CursorWindow (~2 MB per row).** Reading back a `submission_logs` row
   whose `responseBody` is over the window size throws `SQLiteBlobTooBigException`
   ("Row too big to fit into CursorWindow"). The History screen collects these rows through
   `getByParentId` / `getOrphanedErrorLogs` Flows, so History crashes on open, and keeps
   crashing because the bad row stays in the DB.
2. **Clipboard copy.** `copyToClipboard` in `HistoryScreen.kt` (Response "Copy") sends
   the full text through Binder. Over ~1 MB this throws `TransactionTooLargeException`.
3. **Compose `Text` rendering (confirmed location).** The expanded Request/Response
   `HttpSection` lays out the full multi-MB string. That causes the crash, or jank, ANRs,
   or OOM.
4. **`HttpLoggingInterceptor.Level.BODY`.** It is shared by the carb and Dexcom clients
   and on in every build type, so it buffers and logs the full body to logcat as well. That
   is extra memory pressure and leaks data in release builds.
5. **Export.** The backup/export JSON (`HistoryScreen.kt` ~L634) includes `responseBody`,
   so exports grow huge and may OOM.

`CarbRepository` still needs `response.images[0].data` to decode `imageData`. That path is
fine: the redaction applies only to the **captured debug text**, not to what Retrofit/Moshi
parses.

## Proposed approach

0. **Priority: fix the expand crash first** (`ui/history/HistoryScreen.kt`, `HttpSection`).
   Step 4 below is the direct fix for the reported crash. It must also handle rows already
   stored, so it can't rely only on the redaction in step 1. Limit the displayed text
   (e.g. first 20k chars plus a "… truncated (N chars total)" note) before passing it to
   `Text`. Also redact base64 runs in the displayed text so old entries are readable. Add
   a unit test for the display-truncation helper.

1. **Redact base64 image data in the captured response body**
   (`data/remote/carbapi/CarbApiCaptureInterceptor.kt`).
   - After peeking, if the content type is JSON, parse it with `org.json` (or a small
     Moshi `JsonReader` pass) and replace every `images[].data` value with
     `"[base64 image data: N chars]"`, keeping `mime_type`.
   - Fallback for invalid or unknown JSON: a regex that swaps any long base64 run (e.g.
     `[A-Za-z0-9+/=]{1000,}`) for a placeholder.
   - Put the logic in a small pure helper (e.g. `CapturedBodySanitizer`) so it can be
     unit-tested.
2. **Hard cap on captured text size.**
   - In `CarbApiCapture.appendOperation`, truncate each body / header block to a constant
     (e.g. `MAX_CAPTURE_CHARS = 64_000`) with a `… [truncated, N chars total]` suffix.
     This covers unexpected large text responses (e.g. HTML error pages).
   - Peek with a bounded byte count (e.g. `peekBody(MAX_PEEK_BYTES)`) instead of
     `Long.MAX_VALUE`.
3. **Room migration 7 → 8 to clean existing rows** (`CarbCalculatorDatabase.kt`).
   - Pure SQL so huge rows are never loaded into a cursor:
     `UPDATE submission_logs SET responseBody = substr(responseBody, 1, 64000) || '… [truncated by migration]' WHERE length(responseBody) > 64000;`
     Do the same for `requestHeaders` and `responseHeaders`.
   - Register `MIGRATION_7_8` in `DatabaseModule`. This fixes users who already have a
     poisoned row and cannot open History.
4. **Safer UI** (`ui/history/HistoryScreen.kt`).
   - The `Text("Response body: …", maxLines = 3)` in the orphan-error card: pass
     `body.take(500)`, because `maxLines` still measures the whole string.
   - `HttpSection` expanded content: show at most ~20k chars with a "truncated" note.
   - `copyToClipboard`: cap the text (e.g. 500k chars) and wrap `setPrimaryClip` in
     `try/catch` with a Toast on failure.
5. **Logging** (`di/NetworkModule.kt`).
   - Use `Level.BODY` only when `BuildConfig.DEBUG`, `Level.NONE` (or `BASIC`) otherwise.
   - For the carb client, use `Level.HEADERS` even in debug, since the capture interceptor
     already records the body.
6. **Export.** Export uses the stored (now sanitized and capped) value, so no extra change
   should be needed. Verify an export with a large-response log completes.
7. **Changelog.** Add a patch version entry in `AboutScreen.kt`.
8. **Tests** (unit, JVM):
   - Sanitizer: JSON with `images[].data` is redacted and `items`/totals are kept; non-JSON
     long base64 is redacted; small bodies are unchanged.
   - `CarbApiCapture` truncation at the cap.
   - Interceptor still lets Retrofit read the full body (`imageData` is still decoded in
     `CarbRepository`).

## Risks & open questions

- The reporter narrowed the crash to expanding Request/Response in History, but there is
  still no stack trace. If the crash remains after the fix, ask for
  `adb logcat -b crash -d` output.
- Expanding **Request** was also reported to crash. Request image parts are already
  replaced by a placeholder, so verify what makes a stored `requestHeaders` large: for
  example, captures that pile up across retries in `CarbApiCapture.appendOperation`,
  where the capture is never cleared between calls. Or the reporter may have tapped
  Response. The display limit in step 0 covers both either way.
- Optional follow-up: a small in-app crash logger (`Thread.setDefaultUncaughtExceptionHandler`
  writes the last crash to `filesDir`, with a copy button on the About screen). The
  reporter could then share traces without adb.
- "Large **request**": request images are already replaced by a placeholder, and uploads
  are capped at `MAX_UPLOAD_BYTES = 900_000` in `CarbRepository`. The text part and
  headers are not capped. The size cap in step 2 covers them.
- The attachment is exactly 20,000 bytes, so it was probably truncated when it was copied
  out of the app or uploaded. That does not change the approach.
- Backend option: the server could return image data only when asked (e.g. an
  `include_images` flag), which would shrink responses. This is not required for this fix.
  File it separately in `kvcdr-carb-calculator` if wanted.
- The migration must not load the large rows. Keep it to `UPDATE … substr(…)` SQL only.

## Acceptance criteria

- Analyzing a photo whose response contains base64 image data does not crash at capture,
  result, save, or History.
- Stored `submission_logs.responseBody` contains a placeholder instead of base64 image
  data and never exceeds the cap.
- After upgrading, a device with existing oversized rows can open History without
  crashing.
- Expanding the Request and Response sections in History never crashes, including for
  entries saved before the fix. Large content is shown truncated with a note.
- Copying the Response section works for large entries.
- The result image (`imageData`) still shows correctly from the API response.
- Release builds do not log full HTTP bodies.
- Unit tests for the sanitizer and the capture truncation pass in CI.
