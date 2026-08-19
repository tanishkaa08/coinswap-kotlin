# Forward only Electrum (electrs :50001) from the phone to the PC.
# Do NOT reverse Tor. The app runs its own Tor on 127.0.0.1:9050 / 9051.
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
        Write-Warning "Orbot force-stop failed or package missing (exit $LASTEXITCODE). Continuing."
    }
    Start-Sleep -Seconds 2
}

Write-Host "Forwarding electrs + maker SOCKS mapper + docker Tor control"
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:50001", "tcp:50001")
# Phone Taker always uses 9050. Map it to the local onion->TCP proxy, not Docker Tor.
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:9050", "tcp:19050")
Invoke-AdbRequired -CommandArgs @("reverse", "tcp:9051", "tcp:9051")

Write-Host ""
Invoke-AdbRequired -CommandArgs @("reverse", "--list")
Write-Host ""
Write-Host "Phone 50001 -> electrs. Phone 9050 -> maker-direct-socks :19050. Phone 9051 -> coinswap-tor."
