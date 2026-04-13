# Issue #24 — App Update Fails

**Status:** Done — merged via PR #27

## Context

When sideloading the app (no Play Store), updating from one CI-produced APK to a newer one fails with:
> "App not installed as package conflicts with an existing package."

This is Android's signing certificate mismatch error. The CI workflow runs `assembleDebug` on an ephemeral GitHub Actions runner. Android's debug build type auto-generates `~/.android/debug.keystore` on each run, producing a fresh key every time. APK from run A is signed with key A; APK from run B is signed with key B — Android rejects the update.

Secondary issue: `versionCode = 1` is hardcoded. While not the cause of the conflict error, a static version code is sloppy — all CI builds appear identical in version to the package manager.

## Root Cause

- **Primary:** Each CI runner generates a new ephemeral debug keystore → signing key changes every build
- **Secondary:** `versionCode = 1` never increments

## Files to Change

- `.github/workflows/ci.yml`
- `app/build.gradle.kts`

## Implementation Plan

### Step 1 — Generate CI keystore (one-time, developer machine)

```bash
keytool -genkeypair \
  -keystore ci-release.keystore \
  -alias ci-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 36500 \
  -storepass <STORE_PASS> \
  -keypass <KEY_PASS> \
  -dname "CN=CI Build, OU=Dev, O=kvcdr, L=Unknown, ST=Unknown, C=US"

base64 -w 0 ci-release.keystore > ci-release.keystore.b64
```

Store `ci-release.keystore` securely off-repo (password manager, encrypted backup). Do **not** commit it.

### Step 2 — Add four GitHub Actions secrets

| Secret name | Value |
|---|---|
| `CI_KEYSTORE_BASE64` | Content of `ci-release.keystore.b64` |
| `CI_KEYSTORE_ALIAS` | `ci-key` |
| `CI_KEYSTORE_STORE_PASSWORD` | The `<STORE_PASS>` value |
| `CI_KEYSTORE_KEY_PASSWORD` | The `<KEY_PASS>` value |

Settings → Secrets and variables → Actions → New repository secret.

### Step 3 — Update `.github/workflows/ci.yml`

Insert a "Decode keystore" step after "Grant execute permission for gradlew" and before "Lint":

```yaml
- name: Decode keystore
  run: |
    echo "${{ secrets.CI_KEYSTORE_BASE64 }}" | base64 --decode > $RUNNER_TEMP/ci-release.keystore
```

Add `env:` to the "Build debug APK" step:

```yaml
- name: Build debug APK
  env:
    CI_KEYSTORE_PATH: ${{ runner.temp }}/ci-release.keystore
    CI_KEYSTORE_STORE_PASSWORD: ${{ secrets.CI_KEYSTORE_STORE_PASSWORD }}
    CI_KEYSTORE_KEY_PASSWORD: ${{ secrets.CI_KEYSTORE_KEY_PASSWORD }}
    CI_KEYSTORE_ALIAS: ${{ secrets.CI_KEYSTORE_ALIAS }}
    CI_VERSION_CODE: ${{ github.run_number }}
  run: ./gradlew assembleDebug --no-daemon
```

Write the keystore to `$RUNNER_TEMP` (not the workspace directory) so it cannot be accidentally included in artifacts.

### Step 4 — Update `app/build.gradle.kts`

**4a.** Read `CI_VERSION_CODE` at the top of the `android {}` block, before `defaultConfig`:

```kotlin
val ciVersionCode = System.getenv("CI_VERSION_CODE")?.toIntOrNull() ?: 1
```

**4b.** In `defaultConfig`, change:

```kotlin
versionCode = 1
```
to:
```kotlin
versionCode = ciVersionCode
```

**4c.** Add a `signingConfigs` block inside `android {}`, before `buildTypes`:

```kotlin
signingConfigs {
    create("ci") {
        val keystorePath = System.getenv("CI_KEYSTORE_PATH")
        val storePass   = System.getenv("CI_KEYSTORE_STORE_PASSWORD")
        val keyPass     = System.getenv("CI_KEYSTORE_KEY_PASSWORD")
        val keyAlias    = System.getenv("CI_KEYSTORE_ALIAS")

        if (keystorePath != null && storePass != null && keyPass != null && keyAlias != null) {
            storeFile = file(keystorePath)
            storePassword = storePass
            keyPassword = keyPass
            this.keyAlias = keyAlias
        }
    }
}
```

**4d.** Apply the signing config to the `debug` build type conditionally (preserves normal local dev behavior):

```kotlin
debug {
    isDebuggable = true
    applicationIdSuffix = ".debug"

    val keystorePath = System.getenv("CI_KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfig = signingConfigs.getByName("ci")
    }
}
```

Local `assembleDebug` continues to use the machine's default debug keystore. Only CI builds use the stable `ci` signing config.

## Verification

1. Merge to `main`, watch the Actions run — "Build debug APK" step must succeed.
2. Confirm the signing cert: `apksigner verify --print-certs app-debug.apk | grep SHA-256`. Save the fingerprint.
3. Trigger a second CI run (trivial push). Download the new artifact. The SHA-256 fingerprint must match.
4. Sideload run N onto a device. Confirm it installs.
5. Sideload run N+1 as an update. Android should show the update prompt, not a conflict error.
6. Verify local builds still work: `docker compose run --rm build ./gradlew assembleDebug` — no env vars needed, should build with default debug keystore, `versionCode = 1`.
