#!/bin/bash
# Start N makerd instances for regtest (shared bitcoind + host tor).
# Usage: MAKER_COUNT=5 bash wsl-start-makers.sh
set -euo pipefail

cd /mnt/d/dev/coinswap
set -a
source .docker-config
set +a

MAKER_COUNT="${MAKER_COUNT:-5}"
IMAGE_NAME="${IMAGE_NAME:-coinswap}"
export BITCOIN_RPC_AUTH="${BITCOIN_RPC_USER}:${BITCOIN_RPC_PASSWORD}"
export BITCOIN_RPC_HOST="${EXTERNAL_BITCOIND_HOST:-host.docker.internal:18442}"
export MAKERD_EXTRA_ARGS="${MAKERD_EXTRA_ARGS:-}"
if [[ "${USE_TAPROOT:-false}" == "true" ]]; then
  export MAKERD_EXTRA_ARGS="--taproot"
fi

NET_PORTS=(6104 6110 6120 6130 6140)
RPC_PORTS=(6103 6111 6121 6131 6141)
WALLETS=("coinswap-maker" "maker-2" "maker-3" "maker-4" "maker-5")

maker_container() {
  local idx="$1"
  if [[ "$idx" -eq 1 ]]; then echo "coinswap-makerd"; else echo "coinswap-makerd-${idx}"; fi
}

maker_volume() {
  local idx="$1"
  if [[ "$idx" -eq 1 ]]; then echo "coinswap_maker-data"; else echo "coinswap_maker-data-${idx}"; fi
}

start_maker() {
  local idx="$1"
  local net_port="$2"
  local rpc_port="$3"
  local wallet="$4"
  local container
  local volume
  container=$(maker_container "$idx")
  volume=$(maker_volume "$idx")

  echo "==> Starting maker $idx ($wallet) on ports $net_port/$rpc_port ..."
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
      sleep 8
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
      exec makerd -t \"${TOR_AUTH_PASSWORD:-}\" -r \"${BITCOIN_RPC_HOST}\" -a \"${BITCOIN_RPC_AUTH}\" ${MAKERD_EXTRA_ARGS} -w \"${wallet}\"
    "

  if [[ "${FAST_START:-0}" != "1" ]]; then
    echo "    waiting 20s before next maker..."
    sleep 20
  fi
}

for i in $(seq 1 "$MAKER_COUNT"); do
  start_maker "$i" "${NET_PORTS[$((i - 1))]}" "${RPC_PORTS[$((i - 1))]}" "${WALLETS[$((i - 1))]}"
done

echo "==> Maker status:"
for i in $(seq 1 "$MAKER_COUNT"); do
  name=$(maker_container "$i")
  echo "--- $name ---"
  docker logs "$name" 2>&1 | grep -E "Server setup complete|Swap Liquidity|Send at least|Address in use" | tail -4 \
    || docker logs "$name" 2>&1 | tail -5
done
