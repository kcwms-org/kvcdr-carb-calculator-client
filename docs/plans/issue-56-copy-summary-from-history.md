# Issue #56 — Allow user to copy summary from history

## Summary

On the History screen, each submission inside an expanded log card has a copy icon (top-right of the
submission block, next to the status chip). Today it copies the raw submission as JSON. The user wants
to also copy the human-readable summary they see on screen: the description, each item with its carbs,
and the total. The copy icon becomes a dropdown with two choices:

- **Copy Text** — copies the visible summary as plain text (new)
- **Copy Data** — copies the JSON (current behavior)

A new setting, **Default Copy Behavior**, decides what a plain tap on the icon does. Default: **Copy Text**.

## Root cause / context

- The circled area in the issue screenshot is `SubmissionDetail` in
  `app/src/main/java/com/kevcoder/carbcalculator/ui/history/HistoryScreen.kt`. Its `IconButton` calls
  `copyToClipboard(context, "submission_<id>", submission.toJsonString())`.
- The Request/Response copy icons (`HttpSection`) are out of scope and stay unchanged.
- Settings go through `AppPreferencesDataStore` → `SettingsRepository` → `AppSettings` (in
  `domain/model/Models.kt`) → `SettingsViewModel` / `SettingsScreen`. Settings are also included in
  backup export/import via `SettingsSnapshot` in `data/util/BackupModels.kt`.

## Proposed approach

1. **Preference**
   - `AppPreferencesDataStore`: add `DEFAULT_COPY_BEHAVIOR = stringPreferencesKey("default_copy_behavior")`,
     constants `COPY_BEHAVIOR_TEXT = "text"`, `COPY_BEHAVIOR_DATA = "data"`, default `COPY_BEHAVIOR_TEXT`,
     a `defaultCopyBehavior: Flow<String>` and `saveDefaultCopyBehavior(value)`.
   - `AppSettings`: add `defaultCopyBehavior: String = COPY_BEHAVIOR_TEXT`.
   - `SettingsRepository`: map the new flow into `AppSettings`; add the setter.
   - `SettingsSnapshot`: add `defaultCopyBehavior: String? = null` (nullable so older backups still
     import; fall back to Copy Text on null). Include it in export and import.

2. **Settings UI**
   - `SettingsViewModel`: expose the value in UI state and add `onDefaultCopyBehaviorChange`.
   - `SettingsScreen`: add a "Default Copy Behavior" option (radio buttons or a dropdown, matching the
     existing purge-interval control) with "Copy Text" / "Copy Data", near the existing
     "expand submissions by default" toggle in the History section.

3. **History UI**
   - `HistoryViewModel`: expose `defaultCopyBehavior: StateFlow<String>` from `SettingsRepository`.
   - `HistoryScreen` / `SubmissionDetail`: pass the behavior in. Replace the single copy `IconButton`
     with a split control:
     - Tap → runs the default action.
     - Long-press or a small dropdown arrow → `DropdownMenu` with "Copy Text" and "Copy Data".
     (Pick whichever reads cleanest; a copy icon plus a trailing `ArrowDropDown` that opens the menu is
     the simplest discoverable option.)
   - Add `SubmissionLog.toSummaryText()` that builds plain text matching what is shown:
     ```
     Nov 6, 2026 7:02 AM
     The oatmeal is plain and the coffee is black with no sugar
     Bacon strips (3 strips, cooked) - 0.5g
     Fried egg (over easy/sunny side) - 0.5g
     Plain oatmeal - 27.0g
     Black coffee - 0.0g
     Total carbs: 28.0g
     ```
     Skip lines whose source field is null. For error submissions, include the error message instead
     of items.
   - Show a short Toast/snackbar ("Copied summary" / "Copied data") if one isn't already shown by the
     system clipboard UI (Android 13+ shows its own; only toast on API < 33).

4. **Tests**
   - Unit test for `toSummaryText()` (move it to a non-private, testable location such as a
     `SubmissionLogFormatters.kt` file in `ui/history`).
   - `HistoryViewModelTest`: default copy behavior flows from settings (default = text).
   - Backup round-trip test (if one exists) covers the new field and a missing field.

5. **Changelog** — add a new entry at the top of `AboutScreen.kt` (current top is 1.8.2 → **1.9.0**,
   minor since it's a new feature).

## Risks & open questions

- Exact UI affordance for the dropdown (arrow vs. long-press) isn't specified; going with a visible
  dropdown arrow for discoverability. Easy to change.
- Text format isn't specified; the format above mirrors the on-screen layout. Adjust if the user wants
  a different shape (e.g. no timestamp).
- Old backups lack the new field — must default gracefully.

## Acceptance criteria

- In History, each submission's copy control offers "Copy Text" and "Copy Data".
- "Copy Text" puts the visible summary (description, items with carbs, total) on the clipboard.
- "Copy Data" puts the same JSON on the clipboard as today.
- A plain tap on the icon does whatever "Default Copy Behavior" is set to.
- Settings has a "Default Copy Behavior" option; fresh installs default to Copy Text.
- The setting survives app restart and is included in settings export/import; importing an older
  backup without it works and uses Copy Text.
- Request/Response copy icons are unchanged.
- Build and unit tests pass; About screen changelog updated.
