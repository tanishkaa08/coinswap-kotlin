#!/bin/bash
set -euo pipefail
cd /mnt/d/dev/coinswap
source .docker-config
echo "=== Tor hidden services ==="
docker exec coinswap-tor sh -c 'printf "AUTHENTICATE \"%s\"\r\nGETINFO onions/current\r\nQUIT\r\n" "" | nc 127.0.0.1 9051' 2>&1 | head -30

echo ""
echo "=== Maker onions ==="
for c in coinswap-makerd coinswap-makerd-2 coinswap-makerd-3 coinswap-makerd-4 coinswap-makerd-5; do
  echo -n "$c: "
  docker logs "$c" 2>&1 | grep -i "Hidden Service" | tail -1 || echo "(none)"
done
