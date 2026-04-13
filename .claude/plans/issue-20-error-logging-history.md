# Plan: Issue #20 — Error Logging in History Screen

## Context

Currently, the app only saves **successful** submissions to the database. Failed API requests are shown as error messages in the UI but are never persisted. Users cannot see their failed submissions in the history screen, making it impossible to debug issues or understand what happened.

**Requirements:**
1. Log errors (failed submissions) to the database alongside successful submissions
2. Display errors on the history screen with success/failure distinction
3. Allow errors to be collapsed/expanded by default (configurable)
4. Add filtering by:
   - Submission status (FAILED or SUCCEEDED)
   - HTTP status codes
   - Date/time range
   - With/without images

## Current State

**Database:**
- `SubmissionLogEntity` already exists with all required fields: `status`, `errorMessage`, `requestHeaders`, `responseHeaders`, `responseBody`
- Schema supports both success and error logging
- Two migrations (v3, v4) already added ForeignKey + error fields

**UI:**
- `HistoryScreen` displays logs with expandable submission details (success path only)
- `StatusChip` already color-codes "success" vs "error"
- Full HTTP request/response headers visible in expandable sections
- No filtering UI yet (date range, status, etc.)

**Error Handling Gap:**
- `CaptureViewModel.onAnalyze()` catches exceptions, shows message, but doesn't save submission log
- `CarbRepository.analyzeFood()` throws on storage upload failure — not caught/logged
- Errors never reach the database

## Implementation Plan

### Phase 1: Capture & Log Errors in CaptureViewModel
**File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/capture/CaptureViewModel.kt`

When `onAnalyze()` catches an exception (line 75):
1. Inject `SubmissionLogRepository`
2. Call `submissionLogRepository.logRequest()` with:
   - `carbLogId` = null (error happened before save)
   - `status` = "error"
   - `errorMessage` = exception message
   - `requestHeaders`/`responseHeaders`/`responseBody` from cache if available
3. Keep the error message display in UI unchanged

**Design Decision:** Log errors WITHOUT a parent CarbLogEntity ID. The history screen will need to handle orphaned submission logs (errors with carbLogId = null).

### Phase 2: Add Database Schema for Orphaned Error Logs
**File:** `app/src/main/java/com/kevcoder/carbcalculator/data/local/db/CarbCalculatorDatabase.kt`

Currently, `SubmissionLogEntity.carbLogId` is a **non-nullable Foreign Key**. To log errors before a CarbLogEntity is created:
1. Make `carbLogId` **nullable** (Long?)
2. Add migration (v6) to alter the column

**Impact:** Orphaned submission logs will have `carbLogId = null` and won't cascade-delete with a parent.

### Phase 3: Extract HTTP Status Code
**File:** `app/src/main/java/com/kevcoder/carbcalculator/data/repository/SubmissionLogRepository.kt`

Add a helper function to parse HTTP status code from response headers string:
```kotlin
private fun extractStatusCode(responseHeaders: String?): Int? {
  // Parse "HTTP/1.1 400 Bad Request" → 400
}
```

Update `SubmissionLogEntity` to add optional `httpStatusCode: Int?` field with a new migration (v7).

### Phase 4: Update History ViewModel for Filtering
**File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/history/HistoryViewModel.kt`

Add state for filters:
```kotlin
data class FilterState(
  val statusFilter: String? = null,  // "success", "error", or null
  val httpStatusCode: Int? = null,
  val startDate: Long? = null,
  val endDate: Long? = null,
  val hasImageFilter: Boolean? = null,  // null = show all, true = only with images, false = only without
)
```

Implement filtering logic:
1. When a filter changes, apply to the displayed logs
2. Both successful CarbLogs AND orphaned SubmissionLogs appear in the list
3. For orphaned logs, show minimal info: error message, timestamp, HTTP status, request headers

### Phase 5: Update History Screen UI
**File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/history/HistoryScreen.kt`

1. **Unified Log Display:** Mix successful and failed submissions chronologically in the same list
   - Successful submissions: show as before (with food image and analysis)
   - Failed submissions: show as collapsed cards with red outline, timestamp, and error message visible
   
2. **Card Styling for Errors:**
   - Add red border/outline to indicate error status
   - Display error icon or badge
   - Show error message (HTTP status code if available) as subtitle

3. **Expandable Details:**
   - When user taps expand chevron on error card, show:
     - Full error message text
     - HTTP status code (if available)
     - Request/response headers in expandable HttpSection
   - Collapsed by default (per settings toggle in Phase 6)

4. **Add FilterBar:** Date range picker, status chips (All/Success/Error), HTTP status dropdown, image toggle
   - Filtering applies to both successful and failed logs together

5. **Delete Button:** Both successful logs and orphaned error logs should have delete button (already implemented)

### Phase 6: Add Expansion Configuration to Settings
**File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/settings/SettingsScreen.kt`

Add setting: "Default submission expansion"
- Options:
  - "Collapsed" (default) — both success and error submissions start collapsed
  - "Expanded" — both start expanded
  - Alternatively, separate toggles for success vs. error expansion

Store in `AppPreferencesDataStore`. Update `HistoryViewModel` to read this preference and apply it when loading logs.

### Phase 7: Handle ResultViewModel Save Errors
**File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/result/ResultViewModel.kt`

If the save operation itself fails (DB error, etc.), log it:
1. Catch exception in `onSave()`
2. Call `submissionLogRepository.logRequest()` with status="error"
3. Show error message in UI (already done at line 96)

---

## Critical Files to Modify

| Priority | File | Change |
|----------|------|--------|
| 1 | `CarbCalculatorDatabase.kt` | Add migrations v6 (nullable carbLogId), v7 (add httpStatusCode field) |
| 1 | `SubmissionLogEntity.kt` | Make `carbLogId` nullable; add `httpStatusCode: Int?` |
| 2 | `CaptureViewModel.kt` | Inject SubmissionLogRepository; log errors on exception |
| 2 | `HistoryViewModel.kt` | Add filter state; implement filtering logic |
| 3 | `HistoryScreen.kt` | Add FilterBar UI; display orphaned error logs; update CarbLogCard |
| 3 | `SettingsScreen.kt` | Add toggle for default expansion state of submissions |
| 3 | `SubmissionLogRepository.kt` | Add HTTP status code extraction helper |
| 4 | `ResultViewModel.kt` | Log DB save errors (optional, low priority) |

---

## Verification

### Testing Checklist:
1. **Trigger an error:** Disable network → tap Analyze → verify error saved to DB
2. **Check history:** Open history screen → see failed submission with error message and HTTP status (if available)
3. **Expand error:** Click expand chevron → see request/response headers
4. **Filter by status:** Click "Error" chip → only error logs shown
5. **Filter by date:** Select date range → verify correct logs shown
6. **Filter by HTTP status:** Select 403 → only 403 errors shown
7. **Toggle image filter:** Select "With Images" → only logs with images shown
8. **Delete error:** Tap trash icon on orphaned error log → deleted from DB
9. **Settings toggle:** Set "Collapse failed submissions by default" → restart app → verify UI state

### Build & Test:
```bash
docker compose run --rm build ./gradlew build        # Compile
docker compose run --rm build ./gradlew test         # Unit tests
docker compose run --rm build ./gradlew lint         # Lint
```

Manual testing: Build debug APK, install on emulator, simulate errors via network toggle or invalid API URL.

---

## Design Decisions (Finalized)

✅ **Error Display:** Orphaned error logs and successful submissions are mixed chronologically in the same list, with error cards outlined in red and collapsed by default.

✅ **Default Expansion:** Configurable in Settings (single toggle for both success and error submissions).

✅ **Image Filter:** Checks both parent CarbLog's imageData AND SubmissionLog's imagePath.

✅ **HTTP Status Code:** Parse and store the numeric code (e.g., 403) in a dedicated field for easier filtering.
