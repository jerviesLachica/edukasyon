# APK releases

Place release APK files here so the download page can serve them.

## Build a release APK

From the project root (Windows):

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"
.\gradlew.bat :androidApp:assembleRelease
```

Output: `androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk`

For sideload distribution you can rename and copy (debug build for testing):

```powershell
Copy-Item androidApp\build\outputs\apk\debug\androidApp-debug.apk `
  download-site\downloads\schedmate-1.0.0-debug.apk
```

For release:

```powershell
Copy-Item androidApp\build\outputs\apk\release\androidApp-release-unsigned.apk `
  download-site\downloads\schedmate-1.0.0.apk
```

Update `download-site/js/config.js`:
- `version`
- `downloads.androidApk.url` (match the filename)

Then commit, push, and Render will redeploy the static site automatically.

## Signed releases (recommended for production)

1. Create a keystore and add signing config to `androidApp/build.gradle.kts`
2. Run `assembleRelease` to produce a signed APK
3. Copy to this folder with a versioned name

## Google Play

When published, set `downloads.android.enabled = true` and add the Play Store URL in `config.js`.
