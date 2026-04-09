# Plan: History/Submission Log Parent-Child Relationship with Expandable UI

**Status:** Done — merged via PR #15

**GitHub Issue:** [#12](https://github.com/kcwms-org/kvcdr-carb-calculator-client/issues/12)

## Context

Currently `SubmissionLog` and `CarbLog` (History) are parallel, loosely linked via a nullable `savedLogId` column. Orphan submission rows accumulate when the user discards results. The standalone Submissions screen is a separate nav destination that duplicates context already visible from History. The goal is to make `SubmissionLog` a proper child of `CarbLog`, delete orphans on discard, retire the Submissions screen, and expose submission detail inline via an expandable chevron on each History card.

---

## DB Migration: version 3 → 4

- Rename `SubmissionLogEntity.savedLogId` → `carbLogId`
- Add `NOT NULL` FK constraint to `carb_logs.id` with `onDelete = CASCADE`
- Drop all existing `submission_logs` rows where `savedLogId IS NULL` (orphans have no parent)
- SQLite does not support column rename or FK addition on existing tables — migration must:
  1. Create `submission_logs_new` with the new schema
  2. Copy rows where `savedLogId IS NOT NULL` (`carbLogId = savedLogId`)
  3. Drop `submission_logs`
  4. Rename `submission_logs_new` → `submission_logs`

---

## New Files

None.

---

## Modified Files

### `data/local/db/SubmissionLogEntity.kt`
- Replace `val savedLogId: Long?` with `val carbLogId: Long`
- Add `@ForeignKey(entity = CarbLogEntity::class, parentColumns = ["id"], childColumns = ["carbLogId"], onDelete = ForeignKey.CASCADE)`
- Add index on `carbLogId`

### `data/local/db/SubmissionLogDao.kt`
- Remove `markAsSaved()` (no longer needed — the FK is set at insert time)
- Add `getByParentId(carbLogId: Long): Flow<List<SubmissionLogEntity>>`
- Remove `deleteAll()` (submissions are deleted via cascade; keep `deleteOlderThan` for purge worker)

### `data/local/db/CarbCalculatorDatabase.kt`
- Bump version 3 → 4
- Add `MIGRATION_3_4` (recreate `submission_logs` with new schema, copy non-orphan rows, drop old table)

### `di/DatabaseModule.kt`
- Add `.addMigrations(MIGRATION_3_4)`

### `data/repository/SubmissionLogRepository.kt`
- Change `logRequest()` to require `carbLogId: Long` — submission log row is created only after the CarbLog is saved
- Remove `markAsSaved()` — FK is set at insert
- Remove `deleteAll()` — cascades handle cleanup
- Update `toDomain()` mapper to use `carbLogId`

### `domain/model/Models.kt`
- Replace `val savedLogId: Long?` with `val carbLogId: Long` in `SubmissionLog`

### `data/repository/CarbRepository.kt`
- `saveLog()` returns the new `carbLogId`; pass it when creating the submission log row

### `ui/capture/CaptureViewModel.kt`
- **On discard**: delete the in-progress submission log row (currently it is left as an orphan). Since no `CarbLog` exists yet, the row has no valid `carbLogId` — the simplest fix is to not create the submission log row until the user saves (see flow change below)
- **Revised flow**:
  1. Call `analyzeFood()` — no submission log created yet
  2. On success: cache result, call `onSuccess` (navigate to Result screen)
  3. On Save (in `ResultViewModel`): save `CarbLog` → get `carbLogId` → insert `SubmissionLog` row with full request/response data
  4. On Discard: nothing to clean up — no row was ever created

### `ui/result/ResultViewModel.kt`
- `onSave()`: after `carbRepository.saveLog()` returns `savedId`, call `submissionLogRepository.logRequest(carbLogId = savedId, ...)` + `logSuccess(...)` using the captured request/response data stored in `AnalysisResultCache`

### `data/repository/AnalysisResultCache.kt`
- Add fields to carry `requestHeaders`, `responseHeaders`, `responseBody` from `CaptureViewModel` → `ResultViewModel` (currently only `AnalysisResult` and `submissionId` are cached)

### `data/local/db/CarbLogDao.kt`
- No changes needed — Room cascade handles child deletion

### `ui/history/HistoryViewModel.kt`
- Add `expandedLogId: Long?` to `HistoryUiState` (tracks which card is expanded)
- Add `toggleExpand(id: Long)`
- When a card is expanded, load `SubmissionLogRepository.getByParentId(id)` and expose as part of state

### `ui/history/HistoryScreen.kt`
- Add chevron (`Icons.Default.ExpandMore` / `ExpandLess`) at the bottom of each `CarbLogCard`
- On chevron tap: expand inline to show child `SubmissionLog` entries in descending `requestTimestamp` order
- Each expanded submission row shows: status chip, timestamp, food items, total carbs, error message if applicable, expandable HTTP request/response sections with copy buttons (preserve existing `SubmissionsScreen` detail UI)
- Copy-as-JSON action moves to the expanded submission row

### `MainActivity.kt`
- Remove `SUBMISSIONS` route from `Routes` object
- Remove `composable(Routes.SUBMISSIONS) { ... }` from NavHost
- Remove navigation call to SUBMISSIONS from HistoryScreen

### `ui/submissions/SubmissionsScreen.kt` + `SubmissionsViewModel.kt`
- Delete both files

---

## Decision Log

| Question | Decision |
|---|---|
| When to create SubmissionLog row | After user saves (not at analyze time) — eliminates orphan problem entirely |
| Orphan cleanup for existing data | Migration drops all rows where `savedLogId IS NULL` |
| Standalone Submissions screen | Retired — detail moves inline to History card chevron |
| Clear-all submissions action | Removed — user deletes History entries instead |
| Copy-as-JSON | Preserved per submission row in the expanded inline view |

---

## Verification

```bash
# Build
docker compose run --rm build ./gradlew assembleDebug

# Unit tests
docker compose run --rm build ./gradlew test
```

Manual smoke test:
1. Analyze food → tap Discard → go to History → confirm no orphan submission appears
2. Analyze food → tap Save → go to History → tap chevron on new entry → confirm submission detail expands
3. Delete a History entry → confirm its submission log rows are gone (no orphans in DB)
4. Install over existing build (migration path) → confirm existing saved submissions still appear under their parent History entries
