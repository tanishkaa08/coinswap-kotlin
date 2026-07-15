#!/bin/bash
# Restart makers so they re-register onion services with coinswap-tor.
set -euo pipefail
for c in coinswap-makerd coinswap-makerd-2 coinswap-makerd-3 coinswap-makerd-4 coinswap-makerd-5; do
  if docker ps -a --format '{{.Names}}' | grep -qx "$c"; then
    echo "==> restarting $c"
    docker restart "$c"
  fi
done
echo "waiting 40s for makerd + ADD_ONION..."
sleep 40
docker ps --filter name=coinswap-makerd --format 'table {{.Names}}\t{{.Status}}'
