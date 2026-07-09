#!/bin/bash
docker ps -a --filter name=makerd --format 'table {{.Names}}\t{{.Status}}'
echo "---"
for c in coinswap-makerd coinswap-makerd-2 coinswap-makerd-3 coinswap-makerd-4 coinswap-makerd-5; do
  if docker ps -a --format '{{.Names}}' | grep -qx "$c"; then
    echo "=== $c ==="
    docker logs "$c" 2>&1 | tail -15
    echo ""
  fi
done
