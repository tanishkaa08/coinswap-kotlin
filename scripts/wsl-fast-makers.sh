#!/bin/bash
# Fast path: 5 makers in ~2-3 min (parallel start, batch fund, short polls).
# Usage: MAKER_COUNT=5 bash wsl-fast-makers.sh
set -euo pipefail

cd /mnt/d/dev/coinswap
SCRIPTS=/mnt/d/dev/coinswap-kotlin/scripts
set -a
source .docker-config
set +a

MAKER_COUNT="${MAKER_COUNT:-5}"
IMAGE_NAME="${IMAGE_NAME:-coinswap}"
FUND_BTC="${FUND_BTC:-0.01}"
MARKER_DIR="/tmp/coinswap-maker-funded"
export BITCOIN_RPC_AUTH="${BITCOIN_RPC_USER}:${BITCOIN_RPC_PASSWORD}"
# Makers use --network host, so hit bitcoind on the WSL loopback (not host.docker.internal).
export BITCOIN_RPC_HOST="${BITCOIN_RPC_HOST:-127.0.0.1:${BITCOIN_RPC_PORT:-18442}}"

NET_PORTS=(6104 6110 6120 6130 6140)
RPC_PORTS=(6103 6111 6121 6131 6141)
WALLETS=("coinswap-maker" "maker-2" "maker-3" "maker-4" "maker-5")

if ! [[ "$MAKER_COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo "ERROR: MAKER_COUNT must be a positive integer (got: $MAKER_COUNT)" >&2
  exit 1
fi
if (( MAKER_COUNT > ${#NET_PORTS[@]} || MAKER_COUNT > ${#RPC_PORTS[@]} || MAKER_COUNT > ${#WALLETS[@]} )); then
  echo "ERROR: MAKER_COUNT=$MAKER_COUNT exceeds configured makers (${#WALLETS[@]})." >&2
  exit 1
fi

maker_container() {
  local idx="$1"
  if [[ "$idx" -eq 1 ]]; then echo "coinswap-makerd"; else echo "coinswap-makerd-${idx}"; fi
}

maker_volume() {
  local idx="$1"
  if [[ "$idx" -eq 1 ]]; then echo "coinswap_maker-data"; else echo "coinswap_maker-data-${idx}"; fi
}

cli() {
  docker exec coinswap-bitcoind bitcoin-cli -regtest \
    -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
    -rpcport="${BITCOIN_RPC_PORT}" "$@"
}

purge_bitcoind_wallet() {
  local wallet="$1"
  cli unloadwallet "$wallet" 2>/dev/null || true
  docker exec coinswap-bitcoind rm -rf "/home/bitcoin/.bitcoin/regtest/wallets/${wallet}"
}

start_maker_bg() {
  local idx="$1" net_port="$2" rpc_port="$3" wallet="$4"
  local container volume
  container=$(maker_container "$idx")
  volume=$(maker_volume "$idx")

  docker stop "$container" 2>/dev/null || true
  docker rm "$container" 2>/dev/null || true

  docker run -d --name "$container" --network host \
    -v "${volume}:/home/coinswap/.coinswap" \
    -e "RUST_LOG=${RUST_LOG:-info}" \
    -e "TOR_SOCKS_PORT=${TOR_SOCKS_PORT:-9050}" \
    -e "TOR_CONTROL_PORT=${TOR_CONTROL_PORT:-9051}" \
    -e "TOR_AUTH_PASSWORD=${TOR_AUTH_PASSWORD:-}" \
    -e "MIN_SWAP_AMOUNT=${MIN_SWAP_AMOUNT:-10000}" \
    -e "FIDELITY_AMOUNT=${FIDELITY_AMOUNT:-50000}" \
    -e "FIDELITY_TIMELOCK=${FIDELITY_TIMELOCK:-13104}" \
    -e "BASE_FEE=${BASE_FEE:-100}" \
    -e "AMOUNT_RELATIVE_FEE_PPT=${AMOUNT_RELATIVE_FEE_PPT:-1000}" \
    "coinswap/${IMAGE_NAME}:latest" \
    sh -c "
      sleep 5
      mkdir -p /home/coinswap/.coinswap/maker
      cat > /home/coinswap/.coinswap/maker/config.toml << EOM
network_port = ${net_port}
rpc_port = ${rpc_port}
socks_port = ${TOR_SOCKS_PORT:-9050}
control_port = ${TOR_CONTROL_PORT:-9051}
tor_auth_password = \"${TOR_AUTH_PASSWORD:-}\"
min_swap_amount = ${MIN_SWAP_AMOUNT:-10000}
fidelity_amount = ${FIDELITY_AMOUNT:-50000}
fidelity_timelock = ${FIDELITY_TIMELOCK:-13104}
connection_type = \"TOR\"
base_fee = ${BASE_FEE:-100}
amount_relative_fee_ppt = ${AMOUNT_RELATIVE_FEE_PPT:-1000}
EOM
      exec makerd -t \"${TOR_AUTH_PASSWORD:-}\" -r \"${BITCOIN_RPC_HOST}\" -a \"${BITCOIN_RPC_AUTH}\" -w \"${wallet}\"
    "
}

fund_maker() {
  local idx="$1"
  local name wallet addr ismine marker
  name=$(maker_container "$idx")
  wallet="${WALLETS[$((idx - 1))]}"
  marker="${MARKER_DIR}/${name}"

  docker ps --format '{{.Names}}' | grep -qx "$name" || return 1
  docker logs "$name" 2>&1 | grep -q "Listening for requests" && return 2
  [[ -f "$marker" ]] && return 1

  addr=$(docker logs "$name" 2>&1 | grep "Send at least" | tail -1 | grep -oE 'bcrt1[a-zA-Z0-9]{20,}' || true)
  [[ -z "$addr" ]] && return 1

  ismine=$(cli -rpcwallet="$wallet" getaddressinfo "$addr" 2>/dev/null \
    | { grep -o '"ismine": [a-z]*' || true; } | awk '{print $2}')
  ismine=${ismine:-false}
  if [[ "$ismine" != "true" ]]; then
    echo "    $name: address not in bitcoind wallet yet (ismine=$ismine)"
    return 1
  fi

  echo "    funding $name -> $addr"
  cli -rpcwallet=miner sendtoaddress "$addr" "$FUND_BTC" >/dev/null
  touch "$marker"
  return 0
}

echo "==> [1/5] Tor (host + phone relay)..."
bash "$SCRIPTS/wsl-setup-tor-for-phone.sh" | tail -8

echo "==> [2/5] Miner coins..."
cli createwallet miner 2>/dev/null || true
cli loadwallet miner 2>/dev/null || true
BAL=$(cli -rpcwallet=miner getbalance 2>/dev/null || echo 0)
if awk "BEGIN {exit !($BAL < 1)}"; then
  A=$(cli -rpcwallet=miner getnewaddress)
  cli -rpcwallet=miner generatetoaddress 101 "$A" >/dev/null
fi

mkdir -p "$MARKER_DIR"
rm -f "${MARKER_DIR}"/coinswap-makerd-*

echo "==> [3/5] Reset makers 2-5 (fresh volumes + bitcoind wallets)..."
for i in 2 3 4 5; do
  docker stop "$(maker_container "$i")" 2>/dev/null || true
  docker rm "$(maker_container "$i")" 2>/dev/null || true
  docker volume rm "$(maker_volume "$i")" 2>/dev/null || true
  purge_bitcoind_wallet "maker-${i}"
done

echo "==> [4/5] Start makers..."
if docker logs coinswap-makerd 2>&1 | grep -q "Listening for requests"; then
  echo "    maker 1 already listening — skip restart"
else
  echo "    starting maker 1..."
  start_maker_bg 1 "${NET_PORTS[0]}" "${RPC_PORTS[0]}" "${WALLETS[0]}"
fi

echo "    starting makers 2-5 in parallel..."
for i in 2 3 4 5; do
  start_maker_bg "$i" "${NET_PORTS[$((i - 1))]}" "${RPC_PORTS[$((i - 1))]}" "${WALLETS[$((i - 1))]}" &
done
wait
echo "    boot wait 20s..."
sleep 20

echo "==> [5/5] Fund + confirm..."
funded_any=0
for round in $(seq 1 15); do
  for i in $(seq 1 "$MAKER_COUNT"); do
    set +e
    fund_maker "$i"
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
  [[ "$ready" -ge "$MAKER_COUNT" ]] && break
  sleep 4
done

if [[ "$funded_any" -eq 1 ]]; then
  CONF=$(cli -rpcwallet=miner getnewaddress)
  cli -rpcwallet=miner generatetoaddress 5 "$CONF" >/dev/null
  echo "    mined 5 blocks"
fi

echo "==> Waiting for all makers (max ~2 min)..."
for _ in $(seq 1 24); do
  ready=0
  for i in $(seq 1 "$MAKER_COUNT"); do
    name=$(maker_container "$i")
    if docker ps --format '{{.Names}}' | grep -qx "$name" \
      && docker logs "$name" 2>&1 | grep -q "Listening for requests"; then
      ready=$((ready + 1))
    fi
  done
  echo "  ready: $ready / $MAKER_COUNT"
  [[ "$ready" -ge "$MAKER_COUNT" ]] && break
  sleep 5
done

echo ""
for i in $(seq 1 "$MAKER_COUNT"); do
  name=$(maker_container "$i")
  echo "--- $name ---"
  docker logs "$name" 2>&1 | grep -E "Listening|Swap Liquidity|Send at least|Error" | tail -2
done

ready=0
for i in $(seq 1 "$MAKER_COUNT"); do
  docker logs "$(maker_container "$i")" 2>&1 | grep -q "Listening for requests" && ready=$((ready + 1))
done
if [[ "$ready" -ge "$MAKER_COUNT" ]]; then
  echo ""
  echo "SUCCESS: all $MAKER_COUNT makers ready. Sync Markets on phone."
  exit 0
fi
echo ""
echo "PARTIAL: $ready/$MAKER_COUNT ready. Maker 1 works for 1-hop swap testing."
exit 1
