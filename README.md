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

Camera, system file picker, export destinations and sharing targets still require physical-device testing.

## First milestone

- Compose Home, Documents, Tools and Settings screens.
- System camera capture and multiple image import.
- Review, rotate all pages, optional grayscale and remove pages.
- Create multipage A4 PDFs using Android PdfDocument.
- Private local document library with search, rename and delete confirmation.
- Native PDF page preview, Save As export and Android sharing.

Documents are stored privately and removed on uninstall. Export important files first. Images are sampled to limit memory use. Rotation and grayscale apply to all pages. Imported images require a readable Android content URI. The camera uses the installed system camera application.

## Planned next

In-app CameraX capture, crop and perspective correction, individual page editing, OCR, PDF import/merge/split, and resilient background processing. EXIF orientation is handled during image decoding. This initial version is not a complete replacement for all Scanify web tools. Physical-device testing and release signing are still required.
