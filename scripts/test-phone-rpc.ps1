# Verify phone -> PC bitcoind RPC via adb reverse (reliable JSON, no quoting issues).
param(
    [string]$Device = "RZCW40SG3NN"
)

$adb = "adb"
if ($Device) { $adb += " -s $Device" }

$jsonPath = Join-Path $PSScriptRoot "rpc-test.json"
$remotePath = "/data/local/tmp/rpc-test.json"

Write-Host "Pushing test JSON to phone..."
Invoke-Expression "$adb push `"$jsonPath`" $remotePath"

Write-Host "Calling bitcoind via adb reverse (127.0.0.1:18442 on phone -> PC)..."
$result = Invoke-Expression "$adb shell `"curl -s -u user:password -H content-type:text/plain --data-binary @$remotePath http://127.0.0.1:18442/`""

Write-Host $result

if ($result -match '"chain":"regtest"') {
    Write-Host ""
    Write-Host "OK: RPC works through adb reverse. You can login in the app."
    exit 0
}

Write-Host ""
Write-Host "FAIL: Expected regtest chain in response. Rerun phone-adb-reverse.ps1 -StopOrbot"
exit 1
