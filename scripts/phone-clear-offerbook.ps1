# Clear stale offerbook on phone (makers stuck offline after backend fix).
param([string]$Device = "RZCW40SG3NN")

$adbArgs = @()
if ($Device) { $adbArgs += @("-s", $Device) }

$pkg = "com.example.coinswapmobile"
$path = "files/taker/offerbook.json"

Write-Host "Clearing offerbook cache on phone..."
& adb @adbArgs shell run-as $pkg rm -f $path 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "run-as failed; clearing app data (you will need to log in again)..."
    & adb @adbArgs shell pm clear $pkg
} else {
    Write-Host "OK: offerbook.json removed. Reopen app and Sync Marketplace."
}
