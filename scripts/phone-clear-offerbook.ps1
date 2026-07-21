# Clear stale offerbook on phone (makers stuck offline after backend fix).
# Full app wipe is opt-in only: -ForceClear
param(
    [string]$Device = "RZCW40SG3NN",
    [switch]$ForceClear
)

$adbArgs = @()
if ($Device) { $adbArgs += @("-s", $Device) }

$pkg = "com.example.coinswapmobile"
$path = "files/taker/offerbook.json"

Write-Host "Clearing offerbook cache on phone..."
& adb @adbArgs shell run-as $pkg rm -f $path 2>$null
if ($LASTEXITCODE -ne 0) {
    if ($ForceClear) {
        Write-Host "run-as failed; -ForceClear set — wiping app data (re-login required)..."
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
