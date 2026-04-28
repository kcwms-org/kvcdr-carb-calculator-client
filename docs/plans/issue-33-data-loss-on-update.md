# Issue #33 — Data loss on app update

**Status:** Blocked on clarification

## Summary

User reports that updating the app causes total data loss. A previous PR was meant to persist data across updates, but it doesn't work.

## Context

The app uses Room (SQLite) for local persistence. Room database files should survive app updates as they're stored in the app's private data directory (`/data/data/com.kevcoder.carbcalculator/databases/`), which Android preserves across app upgrades.

If data is being lost, the likely causes are:
1. Database file is being deleted during install or startup
2. Database migration issue (schema changed; old data not migrated)
3. Backup/restore mechanism is not enabled, and the device is doing a clean install instead of an in-place upgrade

## Questions

Before planning a fix, need clarification:

1. **Which PR was meant to fix this?** Link to the prior PR or issue number so I can audit it.
2. **What version(s) are affected?** Is this happening on a fresh install → update, or is it a problem only for certain version pairs?
3. **Device/OS details:** What Android version and device? Is this a debug build (signed with a different key), which would cause app uninstall/reinstall?
4. **Reproduction steps:** Exactly which steps lose the data? (e.g., install v1.0, log 3 meals, update to v1.0.1, check history — all gone?)

## Out of scope

- Re-implementing data persistence from scratch (existing Room setup is sound)
- Cloud backup/sync (not mentioned in the requirement)

## Acceptance criteria

- [ ] Data persists across at least one documented app update
- [ ] No data loss when upgrading from any prior version to the current version
- [ ] Root cause of the previous failure is identified and documented
