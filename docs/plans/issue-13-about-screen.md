# Plan: About Screen with SemVer+Git Version

**GitHub Issue:** [#13](https://github.com/kcwms-org/kvcdr-carb-calculator-client/issues/13)

## Context

The app has no About screen. Users have no way to see the app version or changelog. Version is currently a bare `1.0.0` string with no build provenance. Goal: add an About screen (accessible from Settings), inject the git short SHA at build time so the version reads `1.0.0+abc1234`, and maintain a hardcoded changelog in the app.

---

## Version String Strategy

- `BuildConfig.VERSION_NAME` already contains `"1.0.0"` (from `versionName` in `app/build.gradle.kts`)
- Add a new `buildConfigField` that captures the git short SHA via a Gradle `exec {}` call during configuration phase
- At runtime: `"${BuildConfig.VERSION_NAME}+${BuildConfig.GIT_COMMIT_SHA}"`

---

## New Files

| File | Purpose |
|---|---|
| `ui/about/AboutScreen.kt` | About screen composable — app name, version, changelog (no ViewModel) |

---

## Modified Files

### `app/build.gradle.kts`
- Add `buildConfigField("String", "GIT_COMMIT_SHA", ...)` inside `defaultConfig` using `exec { git rev-parse --short HEAD }`
- `try/catch` ensures build succeeds when git is unavailable

### `.github/workflows/ci.yml`
- Annotate `actions/checkout@v4` with `fetch-depth: 1` (explicit; shallow clone already has HEAD)

### `MainActivity.kt`
- Add `const val ABOUT = "about"` to `Routes`
- Add `composable(Routes.ABOUT)` block
- Pass `onNavigateToAbout` to `SettingsScreen` call site

### `ui/settings/SettingsScreen.kt`
- Add `onNavigateToAbout: () -> Unit` parameter
- Add `HorizontalDivider` + `TextButton("About")` row with forward chevron after Submission Log section

---

## Changelog Data Model

Hardcoded in `AboutScreen.kt` as a top-level `private val`:

```kotlin
private data class ChangelogEntry(val version: String, val notes: List<String>)

private val changelog = listOf(
    ChangelogEntry(
        version = "1.0.0",
        notes = listOf(
            "Initial release",
            "Camera capture and AI-powered carb estimation",
            "Dexcom CGM integration",
            "History and submission log",
        ),
    ),
    // Add future releases here, newest first.
)
```

---

## Verification

```bash
docker compose run --rm build ./gradlew assembleDebug
docker compose run --rm build ./gradlew test
```

Manual smoke test:
1. Open Settings → "About" row visible at bottom
2. Tap About → screen shows "Carb Calculator" + version like `1.0.0+abc1234`
3. Changelog section shows 1.0.0 release notes
4. Back button returns to Settings
5. On CI: `assembleDebug` injects the HEAD SHA → `BuildConfig.GIT_COMMIT_SHA` is not `"unknown"`
