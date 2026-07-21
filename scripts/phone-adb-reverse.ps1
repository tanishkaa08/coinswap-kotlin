# Forward phone localhost ports to PC backend (bitcoind + Tor for login/markets).
# Tor uses relay ports 19050/19051 on PC (see wsl-setup-tor-for-phone.sh).
param(
    [string]$Device = "RZCW40SG3NN",
    [switch]$StopOrbot
)

$adbArgs = @()
if ($Device) { $adbArgs += @("-s", $Device) }

if ($StopOrbot) {
    Write-Host "Stopping Orbot..."
    & adb @adbArgs shell am force-stop org.torproject.android
    Start-Sleep -Seconds 2
}

Write-Host "Setting adb reverse on $Device ..."
& adb @adbArgs reverse tcp:18442 tcp:18442
& adb @adbArgs reverse tcp:28332 tcp:28332
# Phone app uses 9050/9051 -> PC relay 19050/19051 (host tor + makers)
& adb @adbArgs reverse --remove tcp:9050 2>$null
& adb @adbArgs reverse --remove tcp:9051 2>$null
& adb @adbArgs reverse tcp:9050 tcp:19050
& adb @adbArgs reverse tcp:9051 tcp:19051

Write-Host ""
& adb @adbArgs reverse --list
