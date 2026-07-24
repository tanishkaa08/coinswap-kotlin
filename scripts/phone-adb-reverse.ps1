# Forward phone localhost ports to PC backend (bitcoind + Tor for login/markets).
# Tor uses relay ports 19050/19051 on PC (see wsl-setup-tor-for-phone.sh).
param(
    [string]$Device = "",
    [switch]$StopOrbot
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
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Orbot force-stop failed or package missing (exit $LASTEXITCODE). Continuing with adb reverse."
    }
    Start-Sleep -Seconds 2
}

Write-Host "Setting adb reverse on $Device ..."
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:18442", "tcp:18442")
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:28332", "tcp:28332")
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:38332", "tcp:38332")
# Removals may fail if nothing was mapped - ignore (don't trip $ErrorActionPreference Stop).
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& adb @adbArgs reverse --remove tcp:9050 2>$null | Out-Null
& adb @adbArgs reverse --remove tcp:9051 2>$null | Out-Null
$ErrorActionPreference = $prevEap
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:9050", "tcp:19050")
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:9051", "tcp:19051")

Write-Host ""
Invoke-AdbRequired -CommandArgs @("reverse", "--list")
