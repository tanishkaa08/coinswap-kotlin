# Forward phone localhost ports to PC backend (bitcoind + Tor for login/markets).
# Tor uses relay ports 19050/19051 on PC (see wsl-setup-tor-for-phone.sh).
param(
    [string]$Device = "RZCW40SG3NN",
    [switch]$StopOrbot
)

$adb = "adb"
if ($Device) { $adb += " -s $Device" }

if ($StopOrbot) {
    Write-Host "Stopping Orbot..."
    Invoke-Expression "$adb shell am force-stop org.torproject.android"
    Start-Sleep -Seconds 2
}

Write-Host "Setting adb reverse on $Device ..."
Invoke-Expression "$adb reverse tcp:18442 tcp:18442"
Invoke-Expression "$adb reverse tcp:28332 tcp:28332"
# Phone app uses 9050/9051 -> PC relay 19050/19051 (host tor + makers)
Invoke-Expression "$adb reverse --remove tcp:9050" 2>$null
Invoke-Expression "$adb reverse --remove tcp:9051" 2>$null
Invoke-Expression "$adb reverse tcp:9050 tcp:19050"
Invoke-Expression "$adb reverse tcp:9051 tcp:19051"

Write-Host ""
Invoke-Expression "$adb reverse --list"
