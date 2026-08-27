<#
.SYNOPSIS
  One-command SchedMate release pipeline. Updates version metadata everywhere,
  builds the signed APK, publishes the GitHub release, deploys the download site,
  and broadcasts the update to all installs via FCM.

.USAGE
  # Metadata only (bump files, no build/deploy):
  .\scripts\release.ps1 -VersionName 1.2.2

  # Full release:
  .\scripts\release.ps1 -VersionName 1.2.2 -Notes "What's new..." -All

  # Pick stages individually:
  .\scripts\release.ps1 -VersionName 1.2.2 -Build -CreateRelease -DeploySite -Broadcast

.NOTES
  - VersionCode defaults to (current + 1) read from androidApp/build.gradle.kts.
  - Requires: JDK/gradle for -Build, gh CLI authed for -CreateRelease,
    firebase CLI authed for -DeploySite, secrets\render-ADMIN_API_KEY.txt for -Broadcast.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$VersionName,          # e.g. 1.2.2

    [int]$VersionCode = 0,         # 0 = auto (current + 1)

    [string]$Notes = "",           # short release notes for version.json / broadcast / GH release

    [switch]$Changelog,            # also append an entry to ChangelogRepository.kt

    [switch]$Build,                # run assembleRelease
    [switch]$CreateRelease,        # publish GitHub release with the APK
    [switch]$DeploySite,           # firebase deploy download site
    [switch]$Broadcast,            # FCM topic broadcast via backend

    [switch]$All,                  # shorthand: Build+CreateRelease+DeploySite+Broadcast

    [switch]$DryRun                # print what would happen; write nothing
)

$ErrorActionPreference = "Stop"
if ($All) { $Build = $true; $CreateRelease = $true; $DeploySite = $true; $Broadcast = $true }

$root     = Split-Path -Parent $PSScriptRoot
$gradleF  = Join-Path $root "androidApp\build.gradle.kts"
$apkRel   = "androidApp\build\outputs\apk\release\androidApp-release.apk"
$repoSlug = "jerviesLachica/edukasyon"

# ---------- 1. Read current versions from build.gradle.kts ----------
$gradle = Get-Content $gradleF -Raw
if ($gradle -notmatch 'versionCode\s*=\s*(\d+)') { throw "versionCode not found in build.gradle.kts" }
$currentCode = [int]$Matches[1]
if ($gradle -notmatch 'versionName\s*=\s*"([^"]+)"') { throw "versionName not found in build.gradle.kts" }
$currentName = $Matches[1]

if ($VersionCode -eq 0) { $VersionCode = $currentCode + 1 }
if ($VersionCode -le $currentCode) { throw "VersionCode must be > $currentCode (got $VersionCode)" }
if ($VersionName -notmatch '^\d+\.\d+\.\d+$') { throw "VersionName must look like 1.2.2 (got '$VersionName')" }

$tag    = "v$VersionName"
$apkUrl = "https://github.com/$repoSlug/releases/download/$tag/schedmate-$VersionName.apk"
Write-Host ""
Write-Host ("Release plan: {0} (code {1}) -> {2} (code {3})" -f $currentName, $currentCode, $VersionName, $VersionCode) -ForegroundColor Cyan
Write-Host ("  APK URL: {0}" -f $apkUrl)
if ($DryRun) { Write-Host "[dry-run] no changes written." -ForegroundColor Yellow }

# ---------- 2. Bump build.gradle.kts ----------
if (-not $DryRun) {
    $gradle = $gradle -replace 'versionCode\s*=\s*\d+', "versionCode = $VersionCode"
    $gradle = $gradle -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$VersionName`""
    [System.IO.File]::WriteAllText($gradleF, $gradle, [System.Text.UTF8Encoding]::new($false))
    Write-Host "[ok] build.gradle.kts bumped" -ForegroundColor Green
}

# ---------- 3. Build APK ----------
$apkPath = Join-Path $root $apkRel
if ($Build) {
    Write-Host "[..] gradlew :androidApp:assembleRelease (this takes a while)" -ForegroundColor Cyan
    if (-not $DryRun) {
        Push-Location $root
        & .\gradlew.bat :androidApp:assembleRelease --console=plain 2>&1 | Select-Object -Last 5
        Pop-Location
        if ($LASTEXITCODE -ne 0 -and $? -eq $false) { throw "Gradle build failed" }
        if (-not (Test-Path $apkPath)) { throw "APK not found at $apkRel after build" }
    }
    Write-Host "[ok] APK built" -ForegroundColor Green
}

# ---------- 4. APK facts (hash/size) ----------
$sha256 = ""; $sizeBytes = 0
if (Test-Path $apkPath) {
    $sha256   = (Get-FileHash $apkPath -Algorithm SHA256).Hash.ToLower()
    $sizeBytes = (Get-Item $apkPath).Length
    Write-Host ("[ok] APK facts: sha256={0}... size={1} bytes" -f $sha256.Substring(0,12), $sizeBytes)
} elseif ($Build) {
    Write-Host "[warn] APK not found; version.json will omit sha256/size" -ForegroundColor Yellow
}

# ---------- 5. Write version.json (single source of truth for app + website) ----------
$meta = [ordered]@{
    versionCode      = $VersionCode
    versionName      = $VersionName
    apkUrl           = $apkUrl
    releaseNotes     = $Notes
    mandatoryUpdate  = $false
    apkFile          = "schedmate-$VersionName.apk"
    sha256           = $sha256
    sizeBytes        = $sizeBytes
    releaseDate      = (Get-Date -Format "yyyy-MM-dd")
}
$json = $meta | ConvertTo-Json
foreach ($dest in @((Join-Path $root "version.json"), (Join-Path $root "download-site\version.json"))) {
    if ($DryRun) { Write-Host ("[dry-run] would write {0}" -f $dest) }
    else { [System.IO.File]::WriteAllText($dest, $json, [System.Text.UTF8Encoding]::new($false)); Write-Host "[ok] $dest (BOM-free)" -ForegroundColor Green }
}

# ---------- 6. Optional: app changelog entry ----------
$changelogKt = Join-Path $root "androidApp\src\main\kotlin\com\edukasyon\studentai\core\update\ChangelogRepository.kt"
if ($Changelog -and (Test-Path $changelogKt)) {
    $noteLines = ""
    if ($Notes) { $noteLines = "`"$($Notes)`"," }
    $entry = @"
        ChangelogEntry(
            versionName = "$VersionName",
            versionCode = $VersionCode,
            releaseDate = "$(Get-Date -Format 'yyyy-MM-dd')",
            isMandatory = false,
            notes = listOf(
                $noteLines
            ),
        ),
"@
    $kt = Get-Content $changelogKt -Raw
    if ($kt -notmatch [regex]::Escape("versionName = `"$VersionName`"" )) {
        $kt = $kt -replace 'val changelog = listOf\(\r?\n', ("val changelog = listOf(`r`n" + $entry + "`r`n")
        if (-not $DryRun) { [System.IO.File]::WriteAllText($changelogKt, $kt, [System.Text.UTF8Encoding]::new($false)) }
        Write-Host "[ok] changelog entry added" -ForegroundColor Green
    } else {
        Write-Host "[skip] changelog already contains $VersionName"
    }
}

# ---------- 7. GitHub release ----------
if ($CreateRelease) {
    if (-not (Test-Path $apkPath)) { throw "-CreateRelease needs the APK; run with -Build first" }
    $upload = Join-Path $env:TEMP "schedmate-$VersionName.apk"
    Copy-Item $apkPath $upload -Force
    Write-Host "[..] gh release create $tag" -ForegroundColor Cyan
    if (-not $DryRun) {
        gh release create $tag $upload --title "$tag" --notes $Notes
        if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }
    }
    Write-Host "[ok] release published: https://github.com/$repoSlug/releases/tag/$tag" -ForegroundColor Green
}

# ---------- 8. Deploy download site ----------
if ($DeploySite) {
    Write-Host "[..] firebase deploy hosting" -ForegroundColor Cyan
    Push-Location $root
    try {
        if (-not $DryRun) { firebase deploy --only hosting:studentai-download --non-interactive 2>&1 | Select-Object -Last 3 }
    } finally { Pop-Location }
    Write-Host "[ok] site deployed -> https://edukasyon-studentai.web.app" -ForegroundColor Green
}

# ---------- 9. FCM broadcast ----------
if ($Broadcast) {
    $keyFile = Join-Path $root "secrets\render-ADMIN_API_KEY.txt"
    if (-not (Test-Path $keyFile)) { throw "Missing $keyFile (needed for broadcast)" }
    $key = (Get-Content $keyFile -Raw).Trim()
    $body = @{
        versionCode     = "$VersionCode"
        versionName     = $VersionName
        apkUrl          = $apkUrl
        releaseNotes    = $Notes
        mandatoryUpdate = $false
    } | ConvertTo-Json -Compress
    Write-Host "[..] broadcasting to topic app_updates" -ForegroundColor Cyan
    if (-not $DryRun) {
        $r = Invoke-RestMethod -Method Post -Uri "https://studentai-backend-ha0z.onrender.com/internal/broadcast-update" `
            -Headers @{ "x-admin-key" = $key; "Content-Type" = "application/json" } `
            -Body $body -TimeoutSec 120
        Write-Host ("[ok] broadcast sent: {0}" -f $r.messageId) -ForegroundColor Green
    }
}

Write-Host ""
Write-Host ("Done: {0} (code {1}){2}" -f $VersionName, $VersionCode, $(if ($DryRun) { " [dry-run]" })) -ForegroundColor Cyan
