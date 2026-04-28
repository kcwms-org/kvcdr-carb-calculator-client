# Issue #37 — Import/Export settings functionality

**Status:** Blocked on clarification

## Summary

User wants to export all app settings and import them later. This would allow backing up configuration and restoring it on a new device or after reinstalling the app.

## Context

App settings are stored in `AppPreferencesDataStore` using Jetpack DataStore (Preferences). Current settings include:
- Carb Calculator API URL (user-configurable base URL)
- Dexcom environment (sandbox vs production)
- Image quality setting (JPEG compression quality)

## Questions

Before writing implementation plan, need clarification:

1. **Export format:** JSON file? Encrypted? Plain text?
2. **Where to store/share:** Device file system (Downloads folder)? Share via intent (email, cloud)?
3. **Scope:** Only app settings, or also include logged meals/history?
4. **Import validation:** Should the app validate settings before importing (e.g., test API URL)?
5. **UI:** Add Import/Export buttons to Settings screen? Use file picker?

## Possible Approach (pending clarification)

1. **Export:** Serialize all DataStore preferences to JSON, write to Downloads folder or generate share intent
2. **Import:** File picker → read JSON → validate → write back to DataStore
3. **UI:** Add Import/Export buttons to SettingsScreen

## Out of Scope

- Cloud backup/sync (not mentioned)
- Exporting logged meals (unless user specifies)
- Encryption (unless user specifies)

## Acceptance Criteria

- [ ] User can export settings to a file
- [ ] User can import settings from a file
- [ ] Settings are correctly restored after import
- [ ] Invalid/corrupted settings files are handled gracefully
