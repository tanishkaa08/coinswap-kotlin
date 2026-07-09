#!/bin/bash
set -euo pipefail
ONION="${1:-ngkge4sj2f5h7aq5wci4hbpkcmgfhipedevqca62emplqqtrl23twjyd.onion}"
echo "Testing SOCKS 127.0.0.1:9050 -> ${ONION}:21"
docker run --rm --network host curlimages/curl:8.5.0 -sS --max-time 20 \
  --socks5-hostname 127.0.0.1:9050 "http://${ONION}:21/" -o /dev/null -w "http_code=%{http_code}\n" \
  || echo "FAILED"
