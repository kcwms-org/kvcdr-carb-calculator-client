# Issue #39 — branding: we need a logo

## Summary

Replace the default "blue rounded square" launcher icon with a custom logo: a generic blue ring (visually inspired by the diabetes awareness circle, but not the trademarked IDF mark) with the lowercase wordmark `kvcdr` centered inside it. Implement as an Android adaptive icon using vector drawables. Project `minSdk = 26`, so the adaptive-icon path covers every supported device — legacy PNG fallbacks are dropped.

**Status:** Done — merged via PR #<number>

## Root cause / context

Today the app ships with the Android Studio default `ic_launcher.png` / `ic_launcher_round.png` in five mipmap densities (`mipmap-mdpi` through `mipmap-xxxhdpi`). There is no `mipmap-anydpi-v26/ic_launcher.xml`, so on Android 8+ launchers the flat PNG is masked into whatever shape the launcher chooses (rounded square, circle, squircle), producing the "blue rounded square" the user dislikes.

`AndroidManifest.xml` already references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` (lines 21, 23) — those references stay; only the resource definitions change.

The app's `minSdk` and `targetSdk` are set in `app/build.gradle.kts`; adaptive icons require API 26+. Devices below 26 will fall back to the legacy PNG mipmaps, so we keep those (regenerated to match the new design).

## Proposed approach

### Design

- **Background layer** — solid white (`#FFFFFF`).
- **Foreground layer** — a blue ring + lowercase `kvcdr` text inside it, both rendered as vector paths.
  - Ring stroke color: `#1976D2` (Material Blue 700 — distinct from the trademarked IDF `#0072CE` while reading as the same family).
  - Ring outer diameter sits inside the 66dp adaptive-icon safe zone (the foreground canvas is 108dp but launchers may mask the outer 33dp on each axis).
  - Text: `kvcdr`, lowercase, sans-serif, same blue as the ring, vertically and horizontally centered.

### Files to add

```
app/src/main/res/
├── drawable/
│   ├── ic_launcher_background.xml      # white solid (vector)
│   └── ic_launcher_foreground.xml      # blue ring + kvcdr text (vector paths)
├── mipmap-anydpi-v26/
│   ├── ic_launcher.xml                 # <adaptive-icon> referencing the two layers
│   └── ic_launcher_round.xml           # same — round mask is launcher-side
└── values/
    └── ic_launcher_background.xml      # <color name="ic_launcher_background">#FFFFFF</color>
```

### Files to replace

```
app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
├── ic_launcher.png                     # rasterized fallback at correct density
└── ic_launcher_round.png               # round-cropped variant for legacy round-icon launchers
```

PNG sizes (Android standard):
- mdpi 48×48, hdpi 72×72, xhdpi 96×96, xxhdpi 144×144, xxxhdpi 192×192.

### Implementation steps

1. **Author `ic_launcher_foreground.xml`** as a `<vector>` drawable with `android:width="108dp"` / `android:height="108dp"` / `viewportWidth="108"` / `viewportHeight="108"`.
   - One `<path>` for the ring (a stroked circle, not a filled donut, so a single path with `android:strokeColor` + `android:strokeWidth` keeps the XML small).
   - Five `<path>` elements for the letters `k v c d r` rendered as filled glyph paths. Glyph paths must be embedded directly — Android vector drawables do not support `<text>` elements, so each letter is converted to SVG path data ahead of time.
   - Center the wordmark within the 66dp safe zone (canvas coordinates 21–87 on each axis).

2. **Author `ic_launcher_background.xml`** as a single full-canvas filled rect at `#FFFFFF`.

3. **Add `mipmap-anydpi-v26/ic_launcher.xml`**:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
       <background android:drawable="@drawable/ic_launcher_background" />
       <foreground android:drawable="@drawable/ic_launcher_foreground" />
   </adaptive-icon>
   ```
   Duplicate to `ic_launcher_round.xml` (the launcher applies the round mask itself).

4. **Regenerate PNG fallbacks** for the five legacy density buckets. Easiest path: render the adaptive-icon composite once at 192×192 and downsample. Acceptable alternatives: use Android Studio's *Image Asset Studio* (right-click `res/` → New → Image Asset → Launcher Icons) which produces both the adaptive-icon XML and the legacy PNGs in one pass, then hand-tweak the colors/text.

5. **Verify on device/emulator** at API 26+ (adaptive icon path) and API ≤25 (PNG path). Confirm the launcher icon is no longer a flat blue square.

### Files NOT changing

- `AndroidManifest.xml` — `android:icon` / `android:roundIcon` references stay.
- In-app branding (header, splash, about screen) — out of scope; clarification answer was launcher-only for now.

## Risks & open questions

- **Glyph paths**: vector drawables can't embed font text, so `kvcdr` must be converted to SVG path data. If the chosen typeface is licensed (e.g., Inter, Roboto via Google Fonts under SIL OFL / Apache-2.0), include attribution where required. Roboto (Apache-2.0) is the lowest-friction choice and is already present on every Android device.
- **Trademark**: we deliberately picked `#1976D2` (Material Blue 700) over the IDF `#0072CE` and a generic ring shape, so there is no IDF mark reproduction. If a stakeholder later wants the exact IDF appearance, the IDF brand guidelines need to be reviewed for permitted use.
- **Legacy PNG quality**: regenerating five PNGs by hand is error-prone. Strongly recommend the Image Asset Studio path during implementation rather than manual export.
- **Local main is ahead of origin**: at the time this branch was cut, local `main` had one unpushed commit (`c1bbdae Mark issue #37 plan as complete`). That commit rides along on this branch, which is harmless but worth noting if you rebase.

## Acceptance criteria

- [ ] On a device running Android 8+ (API 26+), the launcher icon shows a white background with a blue ring containing the lowercase text `kvcdr`, and respects the launcher's mask shape (circle, squircle, rounded square — all should look intentional, not cropped).
- [ ] On a device running Android ≤25, the legacy PNG icon is shown, matching the adaptive-icon design as closely as raster allows.
- [ ] No regression: `android:icon` and `android:roundIcon` in `AndroidManifest.xml` still resolve; the app installs and launches.
- [ ] `./gradlew assembleDebug` succeeds inside the Docker build container.
- [ ] `./gradlew lint` reports no new icon-related warnings (e.g., `IconMissingDensityFolder`, `IconDensities`).
