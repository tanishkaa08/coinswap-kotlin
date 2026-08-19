#!/bin/bash
# Mine 1 block every 45s so swap hops can confirm on regtest.
set -euo pipefail
BCLI="docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password -rpcport=18442"
COUNT="${1:-80}"
INTERVAL="${2:-45}"
for i in $(seq 1 "$COUNT"); do
  ADDR=$($BCLI -rpcwallet=test getnewaddress)
  $BCLI -rpcwallet=test generatetoaddress 1 "$ADDR" >/dev/null
  H=$($BCLI getblockcount)
  echo "$(date -u +%H:%M:%S) mined $i/$COUNT height=$H"
  sleep "$INTERVAL"
done
