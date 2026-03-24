# kvcdr-carb-calculator-client

Android app that photographs food, sends it to the [kvcdr-carb-calculator](https://bitbucket.org/kevcoder1/kvcdr-carb-calculator) backend for AI-powered carb analysis, reads the user's Dexcom G7 glucose via the Dexcom v3 API, and persists logs locally.

> **Note**: The Dexcom API v3 is read-only — there are no write endpoints for carb/meal data. Logs are stored in the app's own local database and displayed in its own UI.

---

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

---

## Project Structure

```
app/src/main/java/com/kevcoder/carbcalculator/
├── auth/dexcom/         DexcomTokenManager (OAuth2 token lifecycle)
├── camera/              CameraManager (CameraX wrapper)
├── data/
│   ├── local/db/        Room: CarbLogEntity, CarbLogDao, CarbCalculatorDatabase
│   ├── local/datastore/ AppPreferencesDataStore (API URL, Dexcom env)
│   ├── remote/carbapi/  CarbApiService + request/response models
│   ├── remote/dexcom/   DexcomApiService + EGV response models
│   └── repository/      CarbRepository, DexcomRepository, SettingsRepository,
│                        AnalysisResultCache
├── di/                  DatabaseModule, NetworkModule (Hilt)
├── domain/model/        CarbLog, GlucoseReading, AnalysisResult, FoodItem, AppSettings
├── ui/
│   ├── capture/         CaptureScreen + CaptureViewModel
│   ├── result/          ResultScreen + ResultViewModel
│   ├── history/         HistoryScreen + HistoryViewModel
│   ├── settings/        SettingsScreen + SettingsViewModel
│   └── theme/           CarbCalculatorTheme (Material3)
├── CarbCalculatorApp.kt @HiltAndroidApp
└── MainActivity.kt      NavHost + Dexcom OAuth redirect handling
```

---

## Building

No Android tooling required on the host. All builds run inside a Docker container using the same image as CI.

### Local build (Docker)

```bash
# Build debug APK → outputs ./app-debug.apk
./build-local.sh

# Run unit tests only
./build-local.sh test

# Lint + unit tests + build APK
./build-local.sh all
```

The APK is copied out of the container to `./app-debug.apk`. Transfer it to your phone and sideload (Settings → Install unknown apps).

### Directly with Gradle (requires Android SDK + JDK 17)

```bash
./gradlew assembleDebug          # build APK
./gradlew test                   # unit tests (JVM)
./gradlew lint                   # static analysis
./gradlew connectedAndroidTest   # instrumented tests (device/emulator required)
```

---

## CI/CD

Bitbucket Pipelines runs on every push to `main` and on all PRs:

1. `lint`
2. `test` (unit tests)
3. `assembleDebug`

The debug APK is stored as a pipeline artifact on `main` builds and is downloadable from the Pipelines UI in Bitbucket for 14 days.

---

## Tests

### Unit tests (`src/test/` — JVM, no device needed)

| Test class | Covers |
|---|---|
| `CaptureViewModelTest` | State transitions: Idle → PhotoTaken → Uploading → Success/Error |
| `ResultViewModelTest` | Cache loading, glucose fetch, save/discard flows |
| `HistoryViewModelTest` | Flow collection, delete delegation |
| `SettingsViewModelTest` | Settings load, URL save, Dexcom env toggle, auth URL event |
| `CarbRepositoryTest` | API response mapping, saveLog entity fields, getLogs, malformed JSON |
| `DexcomRepositoryTest` | isConnected, EGV parsing, empty/error handling, disconnect |
| `DexcomTokenManagerTest` | Token expiry window logic, auth URL sandbox vs. production |

### Instrumented tests (`src/androidTest/` — requires emulator or device)

| Test class | Covers |
|---|---|
| `CarbLogDaoTest` | Insert, getById, getAllLogs ordering, delete, null glucose fields |
| `AppPreferencesDataStoreTest` | Default values, URL and env round-trip read/write |

---

## Configuration

### Carb Calculator API

URL is user-configurable in the Settings screen. Default: `http://143.244.174.42:3000`.

Dynamic base URL is handled via an OkHttp interceptor in `NetworkModule` — changing the URL in Settings takes effect on the next request without restarting the app.

### Dexcom OAuth2

1. Register your app at [developer.dexcom.com](https://developer.dexcom.com) to get `client_id` and `client_secret`
2. Update `DexcomTokenManager.CLIENT_ID` and `CLIENT_SECRET`
3. Redirect URI registered in `AndroidManifest.xml`: `kvcdr-carb://oauth2callback`
4. Toggle Production / Sandbox in the Settings screen

> Dexcom policy recommends storing OAuth tokens on a backend server rather than the device. This app stores them in `EncryptedSharedPreferences` for simplicity. Revisit with a backend proxy if policy compliance becomes a concern.

---

## Architecture Notes

- Single Activity with Compose Navigation
- `AnalysisResultCache` (`@Singleton`) passes `AnalysisResult` from `CaptureViewModel` → `ResultViewModel` without serializing large data through nav arguments
- `CarbLogEntity.foodItemsJson` stores food items as a Moshi-serialized JSON string
- Photo thumbnails are saved to `filesDir/thumbnails/<timestamp>.jpg` on log save; temp capture files go to `cacheDir` and are deleted after saving
