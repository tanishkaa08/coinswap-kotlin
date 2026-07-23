# Test Tor control TCP reachability from phone (nc/curl telnet is unreliable).
param([string]$Device = "")

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

Write-Host "Testing TCP connect to 127.0.0.1:9051 on phone (via adb reverse)..."
# Prefer a TCP-only connect probe (no telnet/protocol handshake).
$probeCmd = @'
toybox nc -z -w 5 127.0.0.1 9051 2>/dev/null && exit 0
nc -z -w 5 127.0.0.1 9051 2>/dev/null && exit 0
python3 -c "import socket;s=socket.create_connection(('127.0.0.1',9051),5);s.close()" 2>/dev/null && exit 0
exit 1
'@
$out = & adb @adbArgs shell $probeCmd 2>&1
$torExit = $LASTEXITCODE
Write-Host "probe exit=$torExit output=$out"

$rpc = & adb @adbArgs shell curl -sS --connect-timeout 5 -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:18442/ 2>&1
Write-Host "RPC port http code: $rpc"

if ($torExit -eq 0) {
    Write-Host "OK: Tor control port reachable from phone."
    exit 0
}

Write-Host "FAIL: Tor TCP probe failed (exit=$torExit). Fix adb reverse / Tor relay and retry."
exit 1
