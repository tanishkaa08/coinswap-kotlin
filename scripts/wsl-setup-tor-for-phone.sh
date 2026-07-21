#!/bin/bash
# Host tor for makers (127.0.0.1) + bridge relay for phone (19050/19051).
set -euo pipefail

cd /mnt/d/dev/coinswap
SCRIPTS=/mnt/d/dev/coinswap-kotlin/scripts
set -a
source .docker-config
set +a

SOCKS="${TOR_SOCKS_PORT:-9050}"
CONTROL="${TOR_CONTROL_PORT:-9051}"
RELAY_SOCKS=19050
RELAY_CONTROL=19051
# Extra tor listeners on docker bridge gateway (reachable from relay container)
BRIDGE_IP="172.17.0.1"
BRIDGE_SOCKS=9052
BRIDGE_CONTROL=9053
AUTH="${TOR_AUTH_PASSWORD:-}"

echo "==> Stopping old tor/relay..."
docker stop coinswap-tor coinswap-tor-relay 2>/dev/null || true
docker rm coinswap-tor coinswap-tor-relay 2>/dev/null || true
powershell.exe -File /mnt/d/dev/coinswap-kotlin/scripts/remove-tor-portproxy.ps1 2>/dev/null || true

echo "==> Host tor: makers 127.0.0.1 + relay ${BRIDGE_IP}:${BRIDGE_SOCKS}..."
docker run -d --name coinswap-tor --network host \
  -v coinswap_tor-data:/var/lib/tor \
  alpine:3.20 /bin/sh -c "
    apk add --no-cache tor >/dev/null
    chown -R tor:tor /var/lib/tor
    HASH=\$(su -s /bin/sh tor -c \"tor --hash-password '${AUTH}'\" | grep '^16:')
    cat > /tmp/torrc << EOF
SocksPort 127.0.0.1:${SOCKS}
ControlPort 127.0.0.1:${CONTROL}
SocksPort ${BRIDGE_IP}:${BRIDGE_SOCKS}
ControlPort ${BRIDGE_IP}:${BRIDGE_CONTROL}
DataDirectory /var/lib/tor
HashedControlPassword \$HASH
EOF
    exec su -s /bin/sh tor -c 'tor -f /tmp/torrc'
  "

echo "Waiting for tor bootstrap..."
BOOTSTRAPPED=0
for i in $(seq 1 45); do
  if docker logs coinswap-tor 2>&1 | grep -q "Bootstrapped 100%"; then
    BOOTSTRAPPED=1
    break
  fi
  sleep 2
done
if [[ "$BOOTSTRAPPED" -ne 1 ]]; then
  echo "ERROR: Tor did not bootstrap within 90s. Check: docker logs coinswap-tor" >&2
  exit 1
fi

echo "==> Bridge relay ${RELAY_SOCKS}/${RELAY_CONTROL} -> ${BRIDGE_IP}:${BRIDGE_SOCKS}/${BRIDGE_CONTROL}..."
docker run -d --name coinswap-tor-relay \
  -p "127.0.0.1:${RELAY_SOCKS}:${RELAY_SOCKS}" \
  -p "127.0.0.1:${RELAY_CONTROL}:${RELAY_CONTROL}" \
  alpine:3.20 /bin/sh -c "
    apk add --no-cache socat >/dev/null
    socat TCP-LISTEN:${RELAY_SOCKS},bind=0.0.0.0,fork,reuseaddr TCP:${BRIDGE_IP}:${BRIDGE_SOCKS} &
    socat TCP-LISTEN:${RELAY_CONTROL},bind=0.0.0.0,fork,reuseaddr TCP:${BRIDGE_IP}:${BRIDGE_CONTROL} &
    wait
  "

sleep 2
docker ps --filter name=coinswap-tor --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'

echo "==> Restart makers..."
bash "$SCRIPTS/wsl-restart-makers.sh"

echo ""
echo "NEXT on Windows:"
echo "  powershell -File scripts/windows-tor-portproxy.ps1   # Admin"
echo "  powershell -File scripts/phone-adb-reverse.ps1 -StopOrbot"
