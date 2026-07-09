#!/bin/bash
# Fund all running makers from the miner wallet.
set -euo pipefail

cd /mnt/d/dev/coinswap
set -a
source .docker-config
set +a

MAKER_COUNT="${MAKER_COUNT:-5}"
FUND_BTC="${FUND_BTC:-0.01}"
MARKER_DIR="/tmp/coinswap-maker-funded"

maker_container() {
  local idx="$1"
  if [[ "$idx" -eq 1 ]]; then echo "coinswap-makerd"; else echo "coinswap-makerd-${idx}"; fi
}

mkdir -p "$MARKER_DIR"

fund_maker_container() {
  local name="$1"

  if ! docker ps --format '{{.Names}}' | grep -qx "$name"; then
    echo "  $name: not running"
    return 1
  fi

  if docker logs "$name" 2>&1 | grep -q "Listening for requests"; then
    echo "  $name: already listening"
    return 2
  fi

  if [[ -f "${MARKER_DIR}/${name}" ]]; then
    echo "  $name: already sent funds, waiting for bond..."
    return 1
  fi

  local line addr
  line=$(docker logs "$name" 2>&1 | grep "Send at least" | tail -1 || true)
  addr=$(echo "$line" | grep -oE 'bcrt1[a-zA-Z0-9]{20,}' || true)
  if [[ -z "$addr" ]]; then
    echo "  $name: no funding address yet"
    return 1
  fi

  echo "  Funding $name -> $addr"
  docker exec coinswap-bitcoind bitcoin-cli -regtest \
    -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
    -rpcport="${BITCOIN_RPC_PORT}" -rpcwallet=miner \
    sendtoaddress "$addr" "$FUND_BTC" >/dev/null
  touch "${MARKER_DIR}/${name}"
  return 0
}

echo "==> Ensuring miner wallet has coins..."
docker exec coinswap-bitcoind bitcoin-cli -regtest \
  -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
  -rpcport="${BITCOIN_RPC_PORT}" createwallet "miner" 2>/dev/null || true
BAL=$(docker exec coinswap-bitcoind bitcoin-cli -regtest \
  -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
  -rpcport="${BITCOIN_RPC_PORT}" -rpcwallet=miner getbalance 2>/dev/null || echo "0")
if awk "BEGIN {exit !($BAL < 1)}"; then
  ADDR=$(docker exec coinswap-bitcoind bitcoin-cli -regtest \
    -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
    -rpcport="${BITCOIN_RPC_PORT}" -rpcwallet=miner getnewaddress)
  docker exec coinswap-bitcoind bitcoin-cli -regtest \
    -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
    -rpcport="${BITCOIN_RPC_PORT}" -rpcwallet=miner generatetoaddress 101 "$ADDR" >/dev/null
fi

echo "==> Funding makers (retries while makerd boots)..."
funded_any=0
for attempt in $(seq 1 30); do
  for i in $(seq 1 "$MAKER_COUNT"); do
    name=$(maker_container "$i")
    set +e
    fund_maker_container "$name"
    rc=$?
    set -e
    if [[ "$rc" -eq 0 ]]; then
      funded_any=1
    fi
  done
  ready=0
  for i in $(seq 1 "$MAKER_COUNT"); do
    name=$(maker_container "$i")
    if docker ps --format '{{.Names}}' | grep -qx "$name" \
      && docker logs "$name" 2>&1 | grep -q "Listening for requests"; then
      ready=$((ready + 1))
    fi
  done
  if [[ "$ready" -ge "$MAKER_COUNT" ]]; then
    break
  fi
  sleep 8
done

if [[ "$funded_any" -eq 1 ]]; then
  echo "==> Mining confirmation blocks..."
  CONF=$(docker exec coinswap-bitcoind bitcoin-cli -regtest \
    -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
    -rpcport="${BITCOIN_RPC_PORT}" -rpcwallet=miner getnewaddress)
  docker exec coinswap-bitcoind bitcoin-cli -regtest \
    -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
    -rpcport="${BITCOIN_RPC_PORT}" -rpcwallet=miner generatetoaddress 5 "$CONF" >/dev/null
fi

echo "==> Waiting for makers ready..."
for i in $(seq 1 60); do
  ready=0
  for j in $(seq 1 "$MAKER_COUNT"); do
    name=$(maker_container "$j")
    if docker ps --format '{{.Names}}' | grep -qx "$name" \
      && docker logs "$name" 2>&1 | grep -q "Listening for requests"; then
      ready=$((ready + 1))
    fi
  done
  echo "  ready: $ready / $MAKER_COUNT"
  if [[ "$ready" -ge "$MAKER_COUNT" ]]; then
    echo ""
    echo "All $MAKER_COUNT makers listening!"
    for j in $(seq 1 "$MAKER_COUNT"); do
      name=$(maker_container "$j")
      docker logs "$name" 2>&1 | grep -E "Swap Liquidity|Server setup complete" | tail -2
    done
    exit 0
  fi
  sleep 10
done

echo ""
echo "Not all makers ready. Run: bash /mnt/d/dev/coinswap-kotlin/scripts/check-makers.sh"
exit 1
