#!/bin/bash
# Fund the three makers that have no fidelity bond, so all five can serve swaps.
set -uo pipefail

BCLI="docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password"

echo "=== miner balance ==="
$BCLI -rpcwallet=miner getbalance

# Addresses makerd printed while waiting for fidelity bond funds.
declare -A ADDRS=(
  ["maker-3"]="bcrt1pls255x07gscfjf6ztzhztumcu87npd9z7hq3wlcm663pgjlgyznsgz5vu7"
  ["maker-4"]="bcrt1pm9qff59ju4z4tv3xgktt734gpd4jwedutsqa8jdcf5jfwda6w45szdeuhz"
  ["maker-5"]="bcrt1p5eprpxsngme45myzmxp3a7l9sw7e5fxmn5zwk03c6pyruf0ueqks46qnpc"
)

for w in maker-3 maker-4 maker-5; do
  echo "==> sending 0.1 BTC to $w (${ADDRS[$w]})"
  $BCLI -rpcwallet=miner sendtoaddress "${ADDRS[$w]}" 0.1 2>&1
done

echo
echo "=== mining 3 blocks to confirm ==="
MINERADDR=$($BCLI -rpcwallet=miner getnewaddress)
$BCLI generatetoaddress 3 "$MINERADDR" >/dev/null 2>&1
echo "height: $($BCLI getblockcount)"
