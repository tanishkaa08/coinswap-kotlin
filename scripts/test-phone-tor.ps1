# Test Tor control from phone using curl (nc often missing on Samsung).
param([string]$Device = "RZCW40SG3NN")

$adbArgs = @()
if ($Device) { $adbArgs += @("-s", $Device) }

Write-Host "Testing TCP to 127.0.0.1:9051 on phone (via adb reverse)..."
$out = & adb @adbArgs shell curl -sS --connect-timeout 5 -m 5 telnet://127.0.0.1:9051 2>&1
$torExit = $LASTEXITCODE
Write-Host "curl exit/output: $out"

$rpc = & adb @adbArgs shell curl -sS --connect-timeout 5 -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:18442/ 2>&1
Write-Host "RPC port http code: $rpc"

if ($torExit -eq 0 -or "$out" -match "250|AUTHENTICATE|Tor") {
    Write-Host "OK: Tor control port reachable from phone."
    exit 0
}

if ("$out" -match "Connection refused|Could not connect|Failed to connect") {
    Write-Host "FAIL: Tor port refused on phone."
    exit 1
}

Write-Host "FAIL: Tor probe not verified (exit=$torExit). Fix adb reverse / Tor relay and retry."
exit 1
