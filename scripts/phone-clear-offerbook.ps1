# Clear stale offerbook on phone (makers stuck offline after backend fix).
# Full app wipe is opt-in only: -ForceClear
param(
    [string]$Device = "",
    [switch]$ForceClear
)

$ErrorActionPreference = "Stop"

function Resolve-AdbDevice {
    param([string]$Preferred)
    if ($Preferred) { return $Preferred }
    $lines = & adb devices 2>$null | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" }
    if (-not $lines -or $lines.Count -eq 0) {
        throw "No adb device connected. Pass -Device <serial>."
    }
    if ($lines.Count -gt 1) {
        throw "Multiple adb devices connected. Pass -Device <serial>."
    }
    return (($lines | Select-Object -First 1) -split "\s+")[0]
}

$Device = Resolve-AdbDevice -Preferred $Device
$adbArgs = @("-s", $Device)

$pkg = "com.example.coinswapmobile"
$path = "files/taker/offerbook.json"

Write-Host "Clearing offerbook cache on phone..."
& adb @adbArgs shell run-as $pkg rm -f $path 2>$null
if ($LASTEXITCODE -ne 0) {
    if ($ForceClear) {
        Write-Host "run-as failed; -ForceClear set - wiping app data (re-login required)..."
        & adb @adbArgs shell pm clear $pkg
        if ($LASTEXITCODE -ne 0) {
            Write-Host "FAIL: pm clear also failed."
            exit 1
        }
        Write-Host "OK: app data cleared."
        exit 0
    }
    Write-Host "FAIL: run-as could not remove offerbook.json (exit $LASTEXITCODE)."
    Write-Host "App data was NOT wiped. Re-run with -ForceClear only if you intend a full reset."
    exit 1
}

Write-Host "OK: offerbook.json removed. Reopen app and Sync Marketplace."
exit 0
