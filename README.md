# Scanify Native

Native Kotlin and Jetpack Compose Android app. No WebView, JavaScript, or bundled web application.

## Build

Open this directory in Android Studio. Requires JDK 17+, Android SDK 36 and Android 8.0+ on the device. Configure `ANDROID_HOME` or `sdk.dir` in `local.properties`.

```powershell
./gradlew.bat assembleDebug lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Validation

`assembleDebug`, `lintDebug` and emulator launch passed. The isolated on-device storage test passed multipage PDF creation, grayscale rendering, persistence, rename and delete:

```powershell
./gradlew.bat assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.scanify.nativeapp.test/com.scanify.nativeapp.StoreSmokeRunner
```

Emulator checks passed camera permission denial/retry/grant, live preview, single capture, two-photo batch capture, retake replacing a page without changing the page count, and saving captured pages as a multipage PDF. Camera quality, torch, front/back switching, system file picker, export destinations and sharing targets still require physical-device testing.

## First milestone

- Compose Home, Documents, Tools and Settings screens.
- Website-inspired mobile design: Scanify header, search, eight Quick Tools cards, gradient scan action and five-position navigation with a floating scan button. Unimplemented tools show an availability dialog.
- Website-style Documents category chips, quick actions, compact PDF rows and recent/oldest sorting. Review uses a full-screen gray stage, filter thumbnails and teal save action. PDF reader has a white header, page controls and bottom action toolbar. Settings matches the workspace/privacy card layout with native storage wording. Unsupported OCR, PDF edit/sign/add-page, folders/favorites and dark appearance show availability dialogs.
- In-app CameraX capture and multiple image import. Website-style black camera screen with live preview, teal shutter, Single/Batch controls, gallery, flashlight when available, camera switching when available, HD/Standard size, Original/Grayscale selection, grid lines and ID framing guide. Tap-to-focus and pinch-to-zoom are provided by CameraX.
- Review, rotate all pages, optional grayscale and remove pages.
- Manual four-corner crop and perspective correction for the selected review page, with Reset, corrected Preview and Apply. Drag handles protect against Android edge-back gestures. Cancel leaves the draft unchanged; Apply creates a separate cropped JPEG and replaces only that draft page. The original input file is not overwritten. Rotation and grayscale apply after cropping. Editing returns to the same review page.
- Create multipage A4 PDFs using Android PdfDocument.
- Private local document library with search, rename and delete confirmation.
- Native PDF page preview, Save As export and Android sharing.

Documents are stored privately and removed on uninstall. Export important files first. Images are sampled to limit memory use. Rotation and grayscale apply to all pages. Imported images require a readable Android content URI. Camera permission is requested only when opening the native camera. No microphone permission is required. Filters apply during review/PDF processing; live preview remains in color. ID mode adds a framing guide, without auto-crop or automatic ID processing. HD requests the largest supported capture size; actual output depends on hardware. CameraX releases the camera when the camera screen closes or its lifecycle stops.

The document viewer follows the supplied mobile reference: compact title/Tag/pages/selection/More header, naturally proportioned scan preview, Add Page strip, gray workspace, and Add/Edit/Share/To Word/Sign toolbar. Save, rename and delete are in More. New scans keep a processed JPEG preview next to the A4 PDF so the viewer preserves the photo proportions and selected grayscale/rotation. Older documents fall back to rendered PDF pages. Tags, page selection, AI, To Word and advanced editing still show availability messages.

## Planned next

Automatic document detection/capture and auto-crop, individual page rotation/filters/reordering, OCR, PDF import/merge/split, and resilient background processing. Crop currently requires manually positioning all four corners. Output is limited to 2400 pixels on its longest edge to bound memory. EXIF orientation is handled during image decoding. This initial version is not a complete replacement for all Scanify web tools. Physical-device testing and release signing are still required.

Crop validation passed on Android: synthetic trapezoid correction with four quadrant colors, rejection of crossed/degenerate/nonfinite corners, unchanged original file, and cropped JPEG dimensions. Emulator UI checks passed dragging all four handles, Preview, Apply, returning to review and saving the result as a PDF. Build and lint passed.
