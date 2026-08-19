#!/bin/bash
set -euo pipefail
BCLI="docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password -rpcport=18442"
TXID=c3b19cffb058f2ba97f686e8fe16b39905694a173482f2216ce392496bc5ad27
echo "height: $($BCLI getblockcount)"
echo "mempool: $($BCLI getmempoolinfo)"
$BCLI getrawtransaction "$TXID" true > /tmp/tx.json 2>/dev/null || echo "tx not found"
python3 - <<'PY'
import json, os
p="/tmp/tx.json"
if os.path.exists(p) and os.path.getsize(p)>10:
    d=json.load(open(p))
    print("txid", d.get("txid"))
    print("confirmations", d.get("confirmations", 0))
    print("blockhash", d.get("blockhash"))
else:
    print("funding tx missing from bitcoind")
PY
ADDR=$($BCLI -rpcwallet=test getnewaddress)
echo "mining 25 blocks to $ADDR"
$BCLI -rpcwallet=test generatetoaddress 25 "$ADDR" >/dev/null
echo "height now: $($BCLI getblockcount)"
$BCLI getrawtransaction "$TXID" true > /tmp/tx.json 2>/dev/null || true
python3 - <<'PY'
import json, os
p="/tmp/tx.json"
if os.path.exists(p) and os.path.getsize(p)>10:
    d=json.load(open(p))
    print("confirmations after mine", d.get("confirmations", 0))
PY
