# Issue #17 — Data persistence across app installs/updates

Data is lost on uninstall because the Room database lives in internal app storage (`data/data/<pkg>/databases/`), which Android wipes on uninstall. `allowBackup="true"` is already set in the manifest but no backup rules are configured, so the database is not actually included in Auto Backup.

The issue requests two strategies:
1. **On-device persistence** — survive uninstall via Android Auto Backup to Google Drive (no extra cloud infra needed)
2. **Cloud backup (stretch)** — upload/download to a remote service

This plan implements strategy 1 (Auto Backup) as the immediate fix, and lays groundwork for strategy 2 as a follow-up.

---

## Root Cause

Android Auto Backup (API 23+) backs up the Room SQLite file by default **only** when `allowBackup="true"` and no `dataExtractionRules` / `fullBackupContent` is specified — but in practice the default exclusion list (`EncryptedSharedPreferences` files, large cache files) can interfere. The real gap here is that thumbnails stored in `filesDir/thumbnails/` are **not** covered by Auto Backup by default, only `databases/` and `shared_prefs/` are. So thumbnails are always lost.

The fix has two parts:
- Explicitly include the database and thumbnail directory in backup rules
- Exclude `EncryptedSharedPreferences` (OAuth tokens — must not roam to new devices)

---

## Implementation Plan

### Step 1 — Add backup rules XML

Create `app/src/main/res/xml/backup_rules.xml` (API 31+ format) and
`app/src/main/res/xml/backup_rules_legacy.xml` (API 23–30 `fullBackupContent` format).

**`backup_rules.xml`** (Android 12+):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="database" path="carb_calculator.db" />
        <include domain="file" path="thumbnails/" />
        <exclude domain="sharedpref" path="androidx.security.crypto.EncryptedSharedPreferences" />
    </cloud-backup>
    <device-transfer>
        <include domain="database" path="carb_calculator.db" />
        <include domain="file" path="thumbnails/" />
        <exclude domain="sharedpref" path="androidx.security.crypto.EncryptedSharedPreferences" />
    </device-transfer>
</data-extraction-rules>
```

**`backup_rules_legacy.xml`** (API 23–30):
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="database" path="carb_calculator.db" />
    <include domain="file" path="thumbnails/" />
    <exclude domain="sharedpref" path="androidx.security.crypto.EncryptedSharedPreferences" />
</full-backup-content>
```

### Step 2 — Wire rules into AndroidManifest.xml

```xml
<application
    ...
    android:allowBackup="true"
    android:dataExtractionRules="@xml/backup_rules"
    android:fullBackupContent="@xml/backup_rules_legacy"
    ...>
```

### Step 3 — Verify thumbnail path is under filesDir

`CarbLogEntity.thumbnailPath` stores absolute paths to `filesDir/thumbnails/<timestamp>.jpg`.  
The `file` domain in backup rules maps to `Context.getFilesDir()`, so `thumbnails/` inside it is covered. No code change needed — just verify the save path in `CaptureViewModel` or `CarbRepository`.

### Step 4 — Validate Room WAL mode doesn't block backup

Room uses WAL (Write-Ahead Logging) by default. Android's backup framework handles WAL checkpointing for `domain="database"` automatically since API 24. No extra configuration needed.

### Step 5 — Exportable CSV/JSON (future / cloud stretch)

Add a Settings screen option: **Export data** → writes a JSON file to the user-chosen location via `ActivityResultContracts.CreateDocument`. This gives users a manual escape hatch independent of Google account backup.

This is a separate issue/PR and is not implemented here.

---

## Files to Touch

| File | Change |
|---|---|
| `app/src/main/res/xml/backup_rules.xml` | Create (new) |
| `app/src/main/res/xml/backup_rules_legacy.xml` | Create (new) |
| `app/src/main/AndroidManifest.xml` | Add `dataExtractionRules` + `fullBackupContent` attributes |

---

## Testing

1. Install app, add several carb logs with photos.
2. Run `adb shell bmgr backupnow com.kevcoder.carbcalculator` to force a backup.
3. Uninstall the app.
4. Reinstall the same APK.
5. On first launch, Auto Backup should restore the DB and thumbnails — history should be intact.

For debug builds, use `com.kevcoder.carbcalculator.debug` as the package name in the `bmgr` command.

---

## Non-goals

- Syncing data across multiple devices (would require a cloud backend)
- EncryptedSharedPreferences (OAuth tokens) intentionally excluded — users re-authorize Dexcom on new devices
