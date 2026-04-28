# Issue #33 — Data loss on app update

**Status:** Implementing fix

## Summary

User reports data persists across updates even after issue #24 was fixed. Root cause: debug builds have `applicationIdSuffix = ".debug"`, making them a completely separate app (`com.kevcoder.carbcalculator.debug` vs `com.kevcoder.carbcalculator`). Android treats these as different apps with separate data directories.

## Root Cause

In `app/build.gradle.kts` (line 63):
```kotlin
debug {
    isDebuggable = true
    applicationIdSuffix = ".debug"  // ← Makes debug APK a separate app
    ...
}
```

Result:
- CI builds APK as `com.kevcoder.carbcalculator.debug`
- User's previous version may have been `com.kevcoder.carbcalculator` or vice versa
- Android sees them as unrelated packages; data doesn't migrate between them

## Solution

Remove the `applicationIdSuffix` for CI builds. CI APKs will use the standard package name `com.kevcoder.carbcalculator`, allowing data to persist across updates.

**Rationale:**
- CI builds are for QA/testing, not local development
- QA should test the same package name that will ship
- For local dev, developers can still use unmodified debug APKs or adjust their build config locally

## Implementation

Remove line 63 from `app/build.gradle.kts` (or conditionally apply the suffix only for local debug builds, not CI).

## Acceptance Criteria

- [ ] CI APK uses package name `com.kevcoder.carbcalculator` (no `.debug` suffix)
- [ ] User can install new CI APK over old one and data persists
- [ ] Verified: install old APK → add logs → install new APK → logs still present
