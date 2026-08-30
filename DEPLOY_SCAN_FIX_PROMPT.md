# Deploy v1.2.4 Scan Fix — Handoff Prompt

## Goal
Ship the schedule-scan crash fix as release v1.2.4, so users no longer see a
silent "UNREADABLE" and instead get the real backend error message.

## Prerequisites
- Working dir: C:/Users/HP/AndroidStudioProjects/edukasyon (git repo, branch master)
- Commit a0ec89b ("fix: surface scan errors to UI + harden extractJson...") is ALREADY on origin/master.
- Firebase CLI authenticated (`firebase login` done), gh CLI authenticated.
- Safety classifier may be flaky — retry Bash calls that get 503 timeouts.

## Tasks (in order)

### 1. Bump version to 1.2.4
Edit androidApp/build.gradle.kts:
    versionName = "1.2.4"
(versionCode should already be 7; if not, set versionCode = 7)

### 2. Build the release APK
./gradlew :androidApp:assembleRelease -x lint -q
=> output: androidApp/build/outputs/apk/release/androidApp-release.apk

Capture:
  SHA=$(sha256sum androidApp/build/outputs/apk/release/androidApp-release.apk | cut -d' ' -f1)
  SIZE=$(stat -c%s androidApp/build/outputs/apk/release/androidApp-release.apk)

### 3. Update version.json (both copies)
Root version.json  AND  download-site/version.json  to:
{
  "versionCode": 7,
  "versionName": "1.2.4",
  "apkUrl": "https://github.com/jerviesLachica/edukasyon/releases/download/v1.2.4/androidApp-release.apk",
  "releaseNotes": "Fix schedule scan: real backend error shown instead of generic UNREADABLE; JSON parsing hardened for reasoning-model output.",
  "mandatoryUpdate": false,
  "apkFile": "androidApp-release.apk",
  "sha256": "<SHA from step 2>",
  "sizeBytes": <SIZE from step 2>,
  "releaseDate": "2026-08-28"
}

### 4. Commit + push
git add -A
git commit -m "chore: bump to v1.2.4 (scan error surfacing + extractJson fix)"
git push origin master

### 5. Create GitHub Release
gh release create v1.2.4 androidApp/build/outputs/apk/release/androidApp-release.apk \
  --title "v1.2.4" \
  --notes "Schedule scan now shows the real backend error instead of silent UNREADABLE; hardened JSON parsing for reasoning-model output."

### 6. Deploy to Firebase Hosting (download-site)
firebase deploy --only hosting:studentai-download

## Verification (after deploy)
curl -s https://edukasyon-studentai.web.app/version.json
  -> should show versionName 1.2.4, sha256 matches SHA above, apkFile androidApp-release.apk

## Success criteria
- GitHub release v1.2.4 exists with androidApp-release.apk attached.
- version.json (both root + download-site) returns 1.2.4 with correct sha256.
- Firebase hosting deployed.
- git log shows 1.2.4 commit on master.