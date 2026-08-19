#!/bin/bash
set -euo pipefail
BCLI="docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password -rpcport=18442"
echo "bitcoind height: $($BCLI getblockcount)"
docker logs coinswap-electrs 2>&1 | tail -8
ADDR=$($BCLI -rpcwallet=test getnewaddress)
$BCLI -rpcwallet=test generatetoaddress 5 "$ADDR" >/dev/null
echo "height now: $($BCLI getblockcount)"
$BCLI getrawtransaction c3b19cffb058f2ba97f686e8fe16b39905694a173482f2216ce392496bc5ad27 true | python3 -c 'import sys,json;d=json.load(sys.stdin);print("funding confirms", d.get("confirmations"))'
