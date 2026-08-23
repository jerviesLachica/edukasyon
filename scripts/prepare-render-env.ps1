<#
.SYNOPSIS
  Prepares Render environment values from a downloaded Firebase service-account key.
.USAGE
  1. Download key from Firebase Console -> Project settings -> Service accounts
     https://console.firebase.google.com/project/edukasyon-studentai/settings/serviceaccounts/adminsdk
  2. Run: .\scripts\prepare-render-env.ps1 -KeyPath "C:\path\to\key.json"
  3. Paste secrets\render-FIREBASE_SERVICE_ACCOUNT.txt contents into Render env var FIREBASE_SERVICE_ACCOUNT
  4. Set ADMIN_API_KEY on Render using the generated value.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$KeyPath
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $KeyPath)) { Write-Error "Key file not found: $KeyPath"; exit 1 }

$raw = Get-Content $KeyPath -Raw
try { $json = $raw | ConvertFrom-Json } catch { Write-Error "File is not valid JSON."; exit 1 }
if (-not $json.private_key -or -not $json.client_email) {
    Write-Error "Not a Firebase service-account key (missing private_key/client_email)."
    exit 1
}
Write-Host ("OK - service account: {0}" -f $json.client_email) -ForegroundColor Green

New-Item -ItemType Directory -Force -Path "secrets" | Out-Null
Copy-Item $KeyPath "secrets\firebase-service-account.json" -Force

$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($raw))
Set-Content -Path "secrets\render-FIREBASE_SERVICE_ACCOUNT.txt" -Value $b64 -NoNewline

$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$adminKey = [Convert]::ToBase64String($bytes) -replace '[^a-zA-Z0-9]', ''
Set-Content -Path "secrets\render-ADMIN_API_KEY.txt" -Value $adminKey -NoNewline

Write-Host ""
Write-Host "Render env vars ready:" -ForegroundColor Cyan
Write-Host "  FIREBASE_SERVICE_ACCOUNT -> paste contents of secrets\render-FIREBASE_SERVICE_ACCOUNT.txt"
Write-Host ("  ADMIN_API_KEY            -> {0}" -f (Get-Content "secrets\render-ADMIN_API_KEY.txt"))
Write-Host ""
Write-Host "(secrets\ is gitignored - safe to keep locally, never commit)" -ForegroundColor Yellow
