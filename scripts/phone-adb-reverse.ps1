# Forward phone localhost ports to PC backend (bitcoind + Tor for login/markets).
# Tor uses relay ports 19050/19051 on PC (see wsl-setup-tor-for-phone.sh).
param(
    [string]$Device = "RZCW40SG3NN",
    [switch]$StopOrbot
)

$ErrorActionPreference = "Stop"

$adbArgs = @()
if ($Device) { $adbArgs += @("-s", $Device) }

function Invoke-AdbRequired {
    param([Parameter(Mandatory)][string[]]$CommandArgs)
    & adb @adbArgs @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($CommandArgs -join ' ') failed with exit $LASTEXITCODE"
    }
}

if ($StopOrbot) {
    Write-Host "Stopping Orbot..."
    & adb @adbArgs shell am force-stop org.torproject.android
    Start-Sleep -Seconds 2
}

Write-Host "Setting adb reverse on $Device ..."
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:18442", "tcp:18442")
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:28332", "tcp:28332")
# Removals may fail if nothing was mapped — ignore.
& adb @adbArgs reverse --remove tcp:9050 2>$null
& adb @adbArgs reverse --remove tcp:9051 2>$null
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:9050", "tcp:19050")
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:9051", "tcp:19051")

Write-Host ""
Invoke-AdbRequired -CommandArgs @("reverse", "--list")
