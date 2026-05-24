# Issue #49 — User should be able to edit results before and after saving

**Status:** Done — merged via PR #50

## Summary

Users need the ability to edit carb analysis results. Currently, once analysis is performed (on ResultScreen) or a log is saved (in HistoryScreen), the data is immutable. The feature should allow users to click an edit button that takes them back to a submission form with all data pre-filled, so they can modify food items/carbs and re-submit. The original request/log should be deleted when re-submitting.

## Root cause / context

The current flow is:
1. **CaptureScreen** → take photo → send to backend
2. **ResultScreen** → display analysis (read-only) → Save or Discard
3. **HistoryScreen** → view saved logs (read-only) → Delete only

There is no way to correct analysis if the backend's estimates are wrong or if the user wants to adjust portion sizes. Users must discard and re-capture from scratch.

## Proposed approach

### 1. Create an EditableResultsCache
Add a new singleton cache (similar to `AnalysisResultCache`) to store the original log/result data when entering edit mode:
- File: `data/repository/EditableResultsCache.kt`
- Store: the `CarbLog` (when editing from History), the original photo path, and a flag indicating edit mode
- On navigation back without saving, clear the cache

### 2. Extend ResultScreen to support edit mode
- **Capture flow** (existing): CaptureScreen → ResultScreen (new analysis)
- **Edit flow** (new): HistoryScreen → ResultScreen (via EditableResultsCache)
- Add a back button (already exists) and an edit button to ResultScreen
- When edit is clicked: save the result to `EditableResultsCache` and navigate to a new screen

### 3. Create EditSubmissionScreen
New screen that wraps the food items for editing (similar to a form):
- File: `ui/edit/EditSubmissionScreen.kt` + `EditSubmissionViewModel.kt`
- Load data from `EditableResultsCache`
- Allow user to:
  - Edit food item names and carb values (inline editable fields)
  - Add/remove food items
  - Update food description
- On "Save Changes" button:
  - Call `submitEditedResult()` in ViewModel
  - Make API request to backend (treat as new submission)
  - Save new log to database
  - Delete the old log (via `CarbRepository.deleteLog(originalLogId)`)
  - Clear `EditableResultsCache` and navigate to success state
- On back/discard:
  - Clear `EditableResultsCache`
  - Return to previous screen

### 4. Update CarbLogDao with an update method
- Add `@Update suspend fun updateLog(log: CarbLogEntity): Int` for potential future use
- Not strictly necessary now but useful for integrity

### 5. Update ResultScreen navigation
- Add edit button (Material icon: `Icons.Default.Edit`)
- On edit click: populate `EditableResultsCache` with current result → navigate to `EditSubmissionScreen`

### 6. Update HistoryScreen navigation
- Add edit button (or repurpose existing action) on each CarbLogCard
- On edit click: populate `EditableResultsCache` with the log data → navigate to `EditSubmissionScreen`

### 7. Update NavGraph
- Add route for `EditSubmissionScreen`
- Composition: `CaptureScreen` → `ResultScreen` → `EditSubmissionScreen` → back to `ResultScreen` (success) or discard

## Risks & open questions

- **Validation:** When editing, should we validate that food items have names and positive carbs? (Yes, recommend simple non-null/non-empty checks)
- **Original image handling:** When re-submitting an edit, do we re-use the original image or allow the user to take a new photo? Current plan re-uses the original image. If new photo is desired, that's a separate scope.
- **Race conditions:** If user edits the same log twice concurrently, the second deletion could fail if the first already deleted it. Recommend handling gracefully (log the error, don't crash).
- **Submission logging:** Should we log both the original and edited submissions separately (yes—one marked as replaced/superseded, one as success). This is already done via `submissionLogRepository.logRequest()`.

## Acceptance criteria

1. ✅ User can click "Edit" button on ResultScreen → navigates to editable form with pre-filled data
2. ✅ User can click "Edit" button on HistoryScreen (CarbLogCard) → navigates to editable form with pre-filled data
3. ✅ EditSubmissionScreen displays food items in editable fields (name + carbs)
4. ✅ User can modify food item names, carb values, and food description
5. ✅ User can add new food items and remove existing ones
6. ✅ "Save Changes" button submits the edited result to the API (as a new submission)
7. ✅ On successful re-submission: original log is deleted, new log is saved, user is navigated to success state
8. ✅ On discard/back: EditableResultsCache is cleared, user returns to previous screen
9. ✅ All tests pass; no regressions in existing screens (ResultScreen, HistoryScreen, CaptureScreen)
