# Verify phone -> PC bitcoind RPC via adb reverse (reliable JSON, no quoting issues).
param(
    [string]$Device = ""
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

$jsonPath = Join-Path $PSScriptRoot "rpc-test.json"
$remotePath = "/data/local/tmp/rpc-test.json"

Write-Host "Pushing test JSON to phone..."
& adb @adbArgs push $jsonPath $remotePath
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAIL: adb push failed (exit $LASTEXITCODE)."
    exit $LASTEXITCODE
}

Write-Host "Calling bitcoind via adb reverse (127.0.0.1:18442 on phone -> PC)..."
$result = & adb @adbArgs shell "curl -s -u user:password -H content-type:text/plain --data-binary @$remotePath http://127.0.0.1:18442/"
$curlExit = $LASTEXITCODE
Write-Host $result

if ($curlExit -ne 0) {
    Write-Host "FAIL: RPC curl failed (exit $curlExit)."
    exit $curlExit
}

if ($result -match '"chain":"regtest"') {
    Write-Host ""
    Write-Host "OK: RPC works through adb reverse. You can login in the app."
    exit 0
}

Write-Host ""
Write-Host "FAIL: Expected regtest chain in response. Rerun phone-adb-reverse.ps1 -StopOrbot"
exit 1
