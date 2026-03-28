# CLAUDE.md — kvcdr-carb-calculator-client

Android app that photographs food, sends it to the `kvcdr-carb-calculator` backend for carb analysis, reads the user's Dexcom G7 glucose, and persists logs locally.

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| Local DB | Room (SQLite) |
| Settings | DataStore (Preferences) |
| HTTP | Retrofit + OkHttp + Moshi |
| Camera | CameraX |
| OAuth tokens | EncryptedSharedPreferences |
| Image loading | Coil |

## Project Structure

```
app/src/main/java/com/kevcoder/carbcalculator/
├── auth/dexcom/       DexcomTokenManager (OAuth2 token lifecycle)
├── camera/            CameraManager (CameraX wrapper)
├── data/
│   ├── local/db/      Room: CarbLogEntity, CarbLogDao, CarbCalculatorDatabase
│   ├── local/datastore/ AppPreferencesDataStore (API URL, Dexcom env)
│   ├── remote/carbapi/  CarbApiService + models
│   ├── remote/dexcom/   DexcomApiService + models
│   └── repository/    CarbRepository, DexcomRepository, SettingsRepository, AnalysisResultCache
├── di/                DatabaseModule, NetworkModule
├── domain/model/      CarbLog, GlucoseReading, AnalysisResult, FoodItem, AppSettings
├── ui/
│   ├── capture/       CaptureScreen + CaptureViewModel
│   ├── result/        ResultScreen + ResultViewModel
│   ├── history/       HistoryScreen + HistoryViewModel
│   ├── settings/      SettingsScreen + SettingsViewModel
│   └── theme/         CarbCalculatorTheme (Material3)
├── CarbCalculatorApp.kt   @HiltAndroidApp
└── MainActivity.kt        NavHost + OAuth redirect handling
```

## Development Environment

**All builds run inside the Docker container** — do not run Gradle commands directly on the host. Use the `docker exec` pattern below, or exec into the container first.

```bash
# Exec into the container
docker exec -it <container_name> bash

# Or run commands directly from the host
docker exec <container_name> ./gradlew assembleDebug
```

## Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (device/emulator required)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

## Key Configuration

**Carb Calculator API**: URL is user-configurable via Settings screen. Default: `http://143.244.174.42:3000`. Dynamic base URL is handled via an OkHttp interceptor in `NetworkModule`.

**Dexcom OAuth2**:
- Register at developer.dexcom.com → get `client_id` + `client_secret`
- Update `DexcomTokenManager.CLIENT_ID` and `CLIENT_SECRET`
- Redirect URI: `kvcdr-carb://oauth2callback` (registered in AndroidManifest)
- Dexcom API v3 is **read-only** — no write endpoints for carb/meal data exist

**Dexcom token storage**: `EncryptedSharedPreferences` (on-device). Dexcom policy prefers backend token storage — revisit with a proxy server if needed.

## Architecture Notes

- Single Activity with Compose Navigation
- `AnalysisResultCache` (@Singleton) passes `AnalysisResult` from `CaptureViewModel` → `ResultViewModel` without nav argument serialization
- Room `CarbLogEntity.foodItemsJson` stores food items as JSON string (Moshi-serialized)
- Photo thumbnails saved to `filesDir/thumbnails/<timestamp>.jpg` on log save; temp capture files written to `cacheDir`
