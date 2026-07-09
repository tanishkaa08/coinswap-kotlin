#!/bin/bash
# Full clean reset for makers 2-5: remove stale volumes + bitcoind wallets, then start + fund.
set -euo pipefail

cd /mnt/d/dev/coinswap
SCRIPTS=/mnt/d/dev/coinswap-kotlin/scripts
set -a
source .docker-config
set +a

echo "==> Stopping makers 2-5..."
for i in 2 3 4 5; do
  docker stop "coinswap-makerd-${i}" 2>/dev/null || true
  docker rm "coinswap-makerd-${i}" 2>/dev/null || true
done

echo "==> Removing stale maker volumes 2-5..."
for i in 2 3 4 5; do
  docker volume rm "coinswap_maker-data-${i}" 2>/dev/null || true
done

echo "==> Unloading stale bitcoind wallets maker-2..5..."
for i in 2 3 4 5; do
  docker exec coinswap-bitcoind bitcoin-cli -regtest \
    -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
    -rpcport="${BITCOIN_RPC_PORT}" unloadwallet "maker-${i}" 2>/dev/null || true
  docker exec coinswap-bitcoind rm -rf "/home/bitcoin/.bitcoin/regtest/wallets/maker-${i}"
done

rm -rf /tmp/coinswap-maker-funded

echo "==> Starting all 5 makers..."
MAKER_COUNT=5 bash "$SCRIPTS/wsl-start-makers.sh" 2>&1 | tail -30

echo "==> Waiting 30s for funding addresses..."
sleep 30

echo "==> Verifying addresses are ismine before funding..."
for i in 2 3 4 5; do
  addr=$(docker logs "coinswap-makerd-${i}" 2>&1 | grep "Send at least" | tail -1 | grep -oE 'bcrt1[a-zA-Z0-9]{20,}' || true)
  if [[ -z "$addr" ]]; then
    echo "  maker-${i}: no address yet"
    continue
  fi
  ismine=$(docker exec coinswap-bitcoind bitcoin-cli -regtest \
    -rpcuser="${BITCOIN_RPC_USER}" -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
    -rpcport="${BITCOIN_RPC_PORT}" -rpcwallet="maker-${i}" \
    getaddressinfo "$addr" 2>/dev/null | grep '"ismine"' || echo '"ismine": false')
  echo "  maker-${i} $addr -> $ismine"
done

MAKER_COUNT=5 bash "$SCRIPTS/wsl-fund-makers.sh"
