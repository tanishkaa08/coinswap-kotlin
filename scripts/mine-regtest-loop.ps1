$ErrorActionPreference = 'SilentlyContinue'
$addr = 'bcrt1qj7v9d3sucglrazjt38ydngwnq3jpcydvuejl2s'
while ($true) {
  docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcport=18442 -rpcuser=user -rpcpassword=password generatetoaddress 1 $addr 2>$null | Out-Null
  Start-Sleep -Seconds 25
}
