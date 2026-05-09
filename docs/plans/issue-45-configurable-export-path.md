# Issue #45 — Configurable Import/Export Path

**Status:** Done — merged via PR #47

**GitHub Issue:** [#45](https://github.com/kcwms-org/kvcdr-carb-calculator-client/issues/45)

## Summary

When users export settings or history, the destination file path is not visible in full due to snackbar truncation. The feature request is to:
1. Make the export/import directory configurable by the user
2. Place a directory picker control in the Settings screen above the Export/Import buttons
3. Default to the Downloads folder if no custom path is set
4. Display the chosen path clearly so users know where files are going

## Root Cause / Context

Currently:
- `SettingsScreen.kt` has Export and Import buttons but no directory selection UI
- The export/import logic (in `SettingsRepository` or similar) likely uses a hardcoded path (probably `context.getExternalFilesDir()` or similar)
- The snackbar feedback on export/import completion shows the file path but truncates long paths, making it unreadable

The user wants explicit control over where exports land, particularly to use the Downloads folder (accessible to file managers) rather than app-private storage.

## Proposed Approach

### 1. Update `AppPreferencesDataStore` to store export directory path

Add a new preference:
- `EXPORT_DIRECTORY_PATH` — String, defaults to `context.getExternalFilesDir(null)?.absolutePath` (or null, with fallback at runtime)

### 2. Add UI in `SettingsScreen.kt`

Insert a new section above the Export/Import buttons:
- Label: "Export/Import Directory"
- Display the current path (truncated if very long, with a copy button)
- Button: "Choose Directory" → launches a directory picker

### 3. Directory Picker Implementation

Two options:
- **Option A (Simpler):** Use `registerForActivityResult` with `ActivityResultContracts.OpenDocumentTree()` (Android 5.0+). Returns a URI. Store the URI as a String in the preference.
- **Option B (More robust):** Use a third-party library like `Androidx.Activity:activity-ktx` with the built-in `OpenDocumentTree` contract, or implement a custom picker dialog if needed.

**Recommendation: Option A** — `OpenDocumentTree` is built-in, requires no new dependencies, and works on all modern Android versions. The URI can be stored as a String and reused via `ContentResolver.takePersistableUriPermission()`.

### 4. Update `SettingsRepository` or create new export/import utility

Modify the export logic to:
1. Read `EXPORT_DIRECTORY_PATH` from preferences
2. If null or invalid, fall back to `context.getExternalFilesDir(null)` (app private directory)
3. Create the file at the chosen location using `DocumentFile.fromTreeUri()` if a URI, or `File()` if a path string
4. Return the full path/URI for the snackbar message

Ensure read/write permissions:
- `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` are already declared (from prior export/import work)
- When using `OpenDocumentTree`, the URI grant is persistent and doesn't require runtime permissions beyond what's already declared

### 5. Update snackbar messages

When showing the export/import completion snackbar:
- Truncate long paths to fit (e.g., show last 60 chars with "…" prefix)
- Add a "Copy path" action to the snackbar so users can see the full path in their clipboard

### 6. Update `SettingsViewModel` if needed

If the directory picker is launched from `SettingsScreen`, add the corresponding `uiEvent` and handler in `SettingsViewModel`.

## Files to Create

| File | Purpose |
|---|---|
| — | No new files; updates to existing ones |

## Files to Modify

| File | Change |
|---|---|
| `data/local/datastore/AppPreferencesDataStore.kt` | Add `EXPORT_DIRECTORY_PATH` preference and getters/setters |
| `ui/settings/SettingsScreen.kt` | Add directory path display and "Choose Directory" button |
| `ui/settings/SettingsViewModel.kt` | Add `openDirectoryPicker()` handler and `exportDirectoryPath` state |
| `data/repository/SettingsRepository.kt` | Add method to get/set export directory from preferences |
| `data/repository/CarbRepository.kt` or `SettingsRepository.kt` | Update export/import logic to use the chosen directory |
| `AndroidManifest.xml` | Verify `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` are declared (likely already present) |

## Risks & Open Questions

1. **URI vs Path string**: `OpenDocumentTree` returns a `Uri`, not a path. When storing, we convert to `String` and persist in DataStore. On retrieval, we convert back to `Uri` and use `DocumentFile.fromTreeUri()`. Verify this round-trip works across app restarts.
2. **Persistence of URI permission**: After the user picks a directory, we must call `context.contentResolver.takePersistableUriPermission()` to retain access across app restarts. Document this clearly in the implementation.
3. **Fallback behavior**: If the stored URI becomes invalid (e.g., the directory is deleted), gracefully fall back to `getExternalFilesDir(null)` and notify the user.
4. **Downloads folder default**: On most devices, `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)` gives the Downloads folder URI. Use this as the initial suggestion when opening the picker, not a hard-coded default (let the user confirm).
5. **File name conflicts**: If a file with the same name already exists at the chosen location, the export should either skip, overwrite with a warning, or append a timestamp. Check the current behavior and preserve it.

## Acceptance Criteria

- [ ] Settings screen displays the current export/import directory path
- [ ] User can tap "Choose Directory" to open a directory picker
- [ ] Selected directory is persisted and survives app restart
- [ ] Export/import buttons use the selected directory (or fallback to app default)
- [ ] Snackbar messages show the full path on copy, and truncate on display for readability
- [ ] Permissions are maintained across restarts (persistent URI grant)
- [ ] Fallback to app-private directory if stored URI becomes invalid
- [ ] Unit tests cover preference storage/retrieval
- [ ] Manual test: pick Downloads folder → export settings → file visible in file manager
