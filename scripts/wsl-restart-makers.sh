#!/bin/bash
# Re-register maker hidden services with coinswap-tor (fixes "all makers offline").
set -euo pipefail
for c in coinswap-makerd coinswap-makerd-2 coinswap-makerd-3 coinswap-makerd-4 coinswap-makerd-5; do
  echo "==> restarting $c"
  docker restart "$c"
done
echo "waiting 40s for makerd + ADD_ONION..."
sleep 40
bash /mnt/d/dev/coinswap-kotlin/scripts/debug-tor-onions.sh
ONION=$(docker logs coinswap-makerd 2>&1 | grep -oE '[a-z0-9]{56}\.onion' | tail -1)
if [[ -n "$ONION" ]]; then
  bash /mnt/d/dev/coinswap-kotlin/scripts/test-onion-socks.sh "$ONION"
fi
