#!/bin/bash
# Recreate the maker containers against the freshly built coinswap image.
# Data volumes are preserved so existing maker wallets and fidelity bonds survive.
set -euo pipefail

IMAGE="coinswap/coinswap:latest"

# name:network_port:rpc_port:wallet:volume
MAKERS=(
  "coinswap-makerd:6104:6103:coinswap-maker:coinswap_maker-data"
  "coinswap-makerd-2:6110:6111:maker-2:coinswap_maker-data-2"
  "coinswap-makerd-3:6120:6121:maker-3:coinswap_maker-data-3"
  "coinswap-makerd-4:6130:6131:maker-4:coinswap_maker-data-4"
  "coinswap-makerd-5:6140:6141:maker-5:coinswap_maker-data-5"
)

echo "=== image being deployed ==="
docker image inspect "$IMAGE" --format 'created: {{.Created}}'

for entry in "${MAKERS[@]}"; do
  IFS=':' read -r NAME NETPORT RPCPORT WALLET VOL <<< "$entry"

  echo
  echo "==> recreating $NAME (net=$NETPORT rpc=$RPCPORT wallet=$WALLET)"
  docker rm -f "$NAME" >/dev/null 2>&1 || true

  docker run -d \
    --name "$NAME" \
    --network host \
    --user coinswap \
    --workdir /app \
    -v "$VOL:/home/coinswap/.coinswap" \
    -e RUST_LOG=info \
    -e TOR_SOCKS_PORT=9050 \
    -e TOR_CONTROL_PORT=9051 \
    -e TOR_AUTH_PASSWORD= \
    -e MIN_SWAP_AMOUNT=10000 \
    -e FIDELITY_AMOUNT=50000 \
    -e FIDELITY_TIMELOCK=13104 \
    -e BASE_FEE=100 \
    -e AMOUNT_RELATIVE_FEE_PPT=1000 \
    "$IMAGE" \
    sh -c "
      sleep 5
      mkdir -p /home/coinswap/.coinswap/maker
      cat > /home/coinswap/.coinswap/maker/config.toml << EOM
network_port = $NETPORT
rpc_port = $RPCPORT
socks_port = 9050
control_port = 9051
tor_auth_password = \"\"
min_swap_amount = 10000
fidelity_amount = 50000
fidelity_timelock = 13104
connection_type = \"TOR\"
base_fee = 100
amount_relative_fee_ppt = 1000
EOM
      exec makerd -t \"\" -r \"127.0.0.1:18442\" -a \"user:password\" -w \"$WALLET\"
    " >/dev/null
done

echo
echo "=== waiting 45s for makerd startup + ADD_ONION ==="
sleep 45

echo
echo "=== status ==="
docker ps --filter name=coinswap-makerd --format 'table {{.Names}}\t{{.Status}}'

echo
echo "=== startup logs (last 12 lines each) ==="
for entry in "${MAKERS[@]}"; do
  IFS=':' read -r NAME _ _ _ _ <<< "$entry"
  echo "---------- $NAME ----------"
  docker logs --tail 12 "$NAME" 2>&1
done
