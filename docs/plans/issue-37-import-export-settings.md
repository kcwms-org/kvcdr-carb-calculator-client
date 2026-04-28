# Issue #37 — Import/Export settings and history

**Status:** Done — merged via PR #38

## Summary

User wants to export all settings and carb log history to a JSON file, and import them later to restore on a new device or after reinstalling. Export writes to Downloads folder; import validates API URL before restoring.

## Requirements (from user feedback)

- **Export format:** JSON
- **Storage:** Downloads folder
- **Scope:** Settings **and** logged meals/history
- **Import validation:** YES — test API URL connectivity before importing
- **UI:** Buttons on Settings screen

## Implementation Plan

### 1. Export data model

Create a data class to hold all exportable data:

```kotlin
@JsonClass(generateAdapter = true)
data class AppBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: SettingsSnapshot,
    val carbLogs: List<CarbLogSnapshot>,
)

@JsonClass(generateAdapter = true)
data class SettingsSnapshot(
    val carbApiUrl: String,
    val dexcomEnv: String,
    val imageQuality: Int,
)

@JsonClass(generateAdapter = true)
data class CarbLogSnapshot(
    val timestamp: Long,
    val foodDescription: String?,
    val foodItemsJson: String,
    val totalCarbs: Float,
    val glucoseMgDl: Int?,
    val glucoseTimestamp: Long?,
)
```

### 2. Export implementation

Add method to `SettingsRepository`:

```kotlin
suspend fun exportBackup(): Result<File> {
    return withContext(Dispatchers.IO) {
        try {
            val settings = SettingsSnapshot(
                carbApiUrl = carbApiUrl.first(),
                dexcomEnv = dexcomEnv.first(),
                imageQuality = imageQuality.first(),
            )
            val logs = carbLogDao.getAllLogs().first().map { entity ->
                CarbLogSnapshot(
                    timestamp = entity.timestamp,
                    foodDescription = entity.foodDescription,
                    foodItemsJson = entity.foodItemsJson,
                    totalCarbs = entity.totalCarbs,
                    glucoseMgDl = entity.glucoseMgDl,
                    glucoseTimestamp = entity.glucoseTimestamp,
                )
            }
            val backup = AppBackup(settings = settings, carbLogs = logs)
            val json = moshi.adapter(AppBackup::class.java).toJson(backup)
            
            val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
            downloadsDir.mkdirs()
            val backupFile = File(downloadsDir, "carb-calculator-backup-${System.currentTimeMillis()}.json")
            backupFile.writeText(json)
            
            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 3. Import implementation

Add method to `SettingsRepository`:

```kotlin
suspend fun importBackup(file: File): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            val json = file.readText()
            val backup = moshi.adapter(AppBackup::class.java).fromJson(json)
                ?: return@withContext Result.failure(Exception("Invalid backup format"))
            
            // Validate API URL is reachable
            val validationResult = validateApiUrl(backup.settings.carbApiUrl)
            if (!validationResult) {
                return@withContext Result.failure(Exception("API URL is not reachable"))
            }
            
            // Restore settings
            updateCarbApiUrl(backup.settings.carbApiUrl)
            updateDexcomEnv(backup.settings.dexcomEnv)
            updateImageQuality(backup.settings.imageQuality)
            
            // Restore logs
            backup.carbLogs.forEach { snapshot ->
                carbLogDao.insert(CarbLogEntity(
                    timestamp = snapshot.timestamp,
                    foodDescription = snapshot.foodDescription,
                    foodItemsJson = snapshot.foodItemsJson,
                    totalCarbs = snapshot.totalCarbs,
                    glucoseMgDl = snapshot.glucoseMgDl,
                    glucoseTimestamp = snapshot.glucoseTimestamp,
                ))
            }
            
            Result.success("Restored ${backup.carbLogs.size} logs")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private suspend fun validateApiUrl(url: String): Boolean {
    return try {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url.trimEnd('/') + "/presign").build()
            val response = carbApiClient.newCall(request).execute()
            response.isSuccessful || response.code == 401 // Accept 401 if auth fails; means URL exists
        }
    } catch (e: Exception) {
        false
    }
}
```

### 4. UI updates

Add buttons to `SettingsScreen`:

```kotlin
Button(
    onClick = { onExport() },
    modifier = Modifier.fillMaxWidth()
) {
    Text("Export Settings & History")
}

Button(
    onClick = { onImport() },
    modifier = Modifier.fillMaxWidth()
) {
    Text("Import Settings & History")
}
```

Add `ViewModel` methods:

```kotlin
fun exportBackup() {
    viewModelScope.launch {
        val result = settingsRepository.exportBackup()
        result.onSuccess { file ->
            _uiState.value = UiState.ExportSuccess(file.absolutePath)
        }
        result.onFailure { e ->
            _uiState.value = UiState.Error(e.message ?: "Export failed")
        }
    }
}

fun importBackup(file: File) {
    viewModelScope.launch {
        val result = settingsRepository.importBackup(file)
        result.onSuccess { message ->
            _uiState.value = UiState.ImportSuccess(message)
        }
        result.onFailure { e ->
            _uiState.value = UiState.Error(e.message ?: "Import failed")
        }
    }
}
```

### 5. File picker

Use `ActivityResultContracts.GetContent()` to let user pick a JSON file for import.

## Risks & Open Questions

- **File permissions:** Ensure app has write permission to Downloads folder (may need runtime permission on Android 6+)
- **Data migration:** What if backup is from an older version? Add version field and handle migrations if schema changes
- **Large history:** If user has thousands of logs, JSON size could be large. Consider compression if needed later
- **Dexcom tokens:** Dexcom OAuth tokens are NOT included in backup (security best practice). User must re-authenticate after import

## Acceptance Criteria

- [ ] Export button writes settings + all carb logs to JSON file in Downloads folder
- [ ] Import button opens file picker, reads JSON, validates API URL
- [ ] API URL validation fails gracefully if unreachable
- [ ] Settings and history restored correctly after import
- [ ] Invalid/corrupted backup files show error message
