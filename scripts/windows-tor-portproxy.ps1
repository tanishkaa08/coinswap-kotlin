# Portproxy ONLY for bitcoind/ZMQ. Tor relay uses docker -p (do not proxy 19050/19051).
param([string]$WslDistro = "Ubuntu")

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Re-launching as Administrator..."
    Start-Process powershell -Verb RunAs -ArgumentList "-File `"$PSCommandPath`"" -Wait
    exit $LASTEXITCODE
}

$wslIp = (wsl -d $WslDistro hostname -I).Trim().Split(" ")[0]
if (-not $wslIp) { throw "Could not get WSL IP" }
Write-Host "WSL IP: $wslIp"

foreach ($port in @(18442, 28332)) {
    netsh interface portproxy delete v4tov4 listenaddress=127.0.0.1 listenport=$port 2>$null | Out-Null
    netsh interface portproxy add v4tov4 listenaddress=127.0.0.1 listenport=$port connectaddress=$wslIp connectport=$port
    Write-Host "  127.0.0.1:$port -> ${wslIp}:$port"
}
Write-Host "(Tor relay 19050/19051 uses docker publish - no portproxy)"
netsh interface portproxy show v4tov4
