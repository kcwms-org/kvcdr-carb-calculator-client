# Issue #33 — Data loss on app update

**Status:** Done — resolved by #24 fix

## Summary

User reported data loss on app update. Root cause: issue #24 (signing key mismatch causing uninstall/reinstall on each CI build). When the app is uninstalled and reinstalled, all local data is wiped by the OS.

## Context

This is a **duplicate** of issue #24. The data loss is a symptom, not a primary bug:

1. **Issue #24 root cause:** Each GitHub Actions run generated a new debug keystore, causing Android to reject updates with "package conflicts with an existing package."
2. **Effect:** App was force-uninstalled and reinstalled, wiping all local data (Room database, DataStore, etc.)
3. **Fix (PR #27):** Stable keystore stored as GitHub secret, auto-increment versionCode using `github.run_number`.

With #24 fixed, updates are now signed consistently, allowing in-place upgrades. Room database survives across updates as designed.

## Why This Is Resolved

PR #27 (`fix: stable CI keystore and auto-increment versionCode (issue #24) (#27)`):
- Generates stable keystore once, stores as GitHub secret
- Decodes keystore in CI before building
- Auto-increments `versionCode` using `github.run_number`
- Allows Android to accept updates as in-place upgrades instead of forcing uninstall/reinstall

With consistent signing, the OS no longer wipes app data on update. Room database persists as designed.

## Verification

To confirm data persists on the next update, install current APK, add log entries, update to the next build, and verify history is intact.
