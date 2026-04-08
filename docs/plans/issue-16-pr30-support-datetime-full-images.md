# Plan: Support PR #30 Backend Changes + Image Gallery + Save-to-Phone Config

**Issue:** Backend PR #30 adds datetime input to `/analyze` endpoint and returns full-sized images instead of thumbnails. This plan addresses three interconnected requirements:
1. Support datetime picker in capture flow
2. Display full images on history screen (clickable image viewer)
3. Add user-configurable option to save images to device storage

---

## Requirements

### 1. DateTime Input Support
- Backend `/analyze` endpoint now accepts `datetime` parameter (optional, defaults to now)
- Add datetime picker UI to CaptureScreen
- Allow user to override submission timestamp (useful for backfilling historical meals)
- Default to current datetime; user can change if needed

### 2. Full-Sized Image Response
- Backend now returns full-resolution images from `/analyze` (not thumbnails)
- Update `AnalysisResult` model to store full image URL/data
- No multiple images: single image per submission (unlike backend capability)
- Display full image when tapped from history list

### 3. History Screen Image Viewer
- Show thumbnail/preview of image in history list items
- Clicking image opens full-screen image viewer/gallery
- Full-screen viewer shows datetime, food items, carbs, glucose if available
- Ability to dismiss viewer (back button/swipe)

### 4. Save-to-Phone Config Option
- New toggle in SettingsScreen: "Save images to device" (default: **OFF**)
- When enabled: save original image to device photo gallery or dedicated app folder
- When disabled: do not persist images (only display during result, not in history)
- Use `MediaStore.Images.Media.insertImage()` or `ContentResolver` API
- Handle scoped storage (API 30+) requirements
- Request `WRITE_EXTERNAL_STORAGE` permission if enabled

---

## Implementation Tasks

### Task 1: Update Models
- **File:** `app/src/main/java/com/kevcoder/carbcalculator/domain/model/`
  - `AnalysisResult`: Add `datetime: LocalDateTime`, `fullImageUrl: String`
  - `CarbLog`: Already has datetime; ensure it aligns with submission datetime
  - Update Moshi serialization as needed

### Task 2: Update API Service
- **File:** `app/src/main/java/com/kevcoder/carbcalculator/data/remote/carbapi/CarbApiService.kt`
  - Add `datetime` parameter to `/analyze` POST request
  - Update response model to match new schema with full image URL
  - Handle both old and new response formats if needed (backwards compat)

### Task 3: Update Capture Flow
- **File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/capture/CaptureScreen.kt`
  - Add datetime picker UI (Material3 DatePicker + TimePicker)
  - Store selected datetime in `CaptureViewModel`
  - Pass datetime to API call in `/analyze` POST
  - Show selected datetime in UI (editable, defaults to now)

### Task 4: Update Settings
- **File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/settings/SettingsScreen.kt`
  - Add toggle: "Save images to device"
  - Store preference in `AppPreferencesDataStore`
  - Add `saveImagesToDevice: Boolean` to `AppSettings` model

### Task 5: Add Image Persistence Logic
- **File:** `app/src/main/java/com/kevcoder/carbcalculator/data/repository/CarbRepository.kt`
  - New function: `saveImageToDevice(imageUrl: String): Boolean`
  - Uses `MediaStore.Images.Media.insertImage()` for gallery integration
  - Handle scoped storage + permission checks
  - Only called if `AppSettings.saveImagesToDevice == true`

### Task 6: Update History Storage
- **File:** `app/src/main/java/com/kevcoder/carbcalculator/data/local/db/`
  - `CarbLogEntity`: Add `fullImageUrl: String?` column (optional)
  - `CarbLogDao`: Update insert/query to handle image URL
  - Migration: Create migration if DB schema changes

### Task 7: Update History Screen
- **File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/history/HistoryScreen.kt`
  - Display image thumbnail in list items (coil image loading)
  - Add click listener to open full-screen image viewer
  - Pass full image URL to viewer

### Task 8: Add Image Viewer Composable
- **File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/` (new)
  - Create `ImageViewerDialog` or `FullScreenImageScreen` composable
  - Display full image + metadata (datetime, food items, carbs, glucose)
  - Dismiss via back button or swipe
  - Handle image loading states (loading, error, success)

### Task 9: Update Result Screen
- **File:** `app/src/main/java/com/kevcoder/carbcalculator/ui/result/ResultScreen.kt`
  - Display full image from `AnalysisResult`
  - Call `saveImageToDevice()` if setting enabled
  - Show save status/confirmation to user

### Task 10: Handle Permissions
- **File:** `AndroidManifest.xml`
  - Add `WRITE_EXTERNAL_STORAGE` permission (or `READ_EXTERNAL_STORAGE` depending on scope)
  - Use runtime permissions (API 23+) via `ActivityResultContract`
- **File:** `CarbCalculatorApp.kt` or relevant ViewModel
  - Request permission when user toggles "Save images" setting
  - Gracefully handle permission denial

### Task 11: Update Tests
- Unit tests for datetime serialization
- Tests for image saving logic (mock `MediaStore` API)
- Tests for settings persistence

---

## Architecture Notes

- **Datetime Handling:** Use `LocalDateTime` (java.time or `java.util.Date` + Moshi adapter)
- **Image Storage:** Prefer `MediaStore.Images.Media.insertImage()` for gallery integration vs. custom app folder
- **Scoped Storage:** Use `ContentResolver.insert()` with `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` for API 30+
- **Caching:** Full images may be large; consider disk/memory cache strategy (Coil handles this)
- **Backwards Compat:** Handle case where backend returns old response format (no datetime, no full image)

---

## Testing Checklist

- [ ] DateTime picker opens and can be set
- [ ] DateTime is sent to `/analyze` endpoint
- [ ] Full image URL is returned and displayed
- [ ] Clicking image in history opens full-screen viewer
- [ ] Full-screen viewer shows metadata correctly
- [ ] "Save images" toggle saves to device gallery when ON
- [ ] No images saved when toggle is OFF
- [ ] Permission dialog shows when enabling setting
- [ ] Permission denial gracefully handled
- [ ] Backwards compat: old submissions without images don't crash

---

## Notes

- Backend returns **one image per submission** (not multiple); UI follows suit
- Image viewer should match Material3 design language
- Consider adding image zoom capability in full-screen viewer
- Datetime picker should use Material3 DatePickerDialog + TimePickerDialog

**Status:** Ready for implementation

