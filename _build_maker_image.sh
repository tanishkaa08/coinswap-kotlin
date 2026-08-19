#!/bin/bash
# Rebuild the maker Docker image from the exact coinswap commit the Android
# taker links against, so both sides agree on the SwapDetails wire format.
set -euo pipefail

TARGET="cca279fc2390003e3f7aed33468ac0348620ad32"
DB="$HOME/.cargo/git/db/coinswap-6f9ba6f4678fd107"
WORK="/tmp/cs-master"

echo "=== preparing source at $TARGET ==="
rm -rf "$WORK"
# The cargo git db is a bare mirror that already contains the target commit,
# so this needs no network access.
git clone --no-checkout "$DB" "$WORK" >/dev/null 2>&1
cd "$WORK"
git checkout --detach "$TARGET" >/dev/null 2>&1
git log -1 --format='source commit: %h %ad %s' --date=iso

echo
echo "=== confirming the breaking change is present ==="
if grep -q 'refund_locktime_offset' src/protocol/common_messages.rs; then
  echo "UNEXPECTED: refund_locktime_offset still present -- wrong commit"
  exit 1
fi
echo "ok: SwapDetails has no refund_locktime_offset (matches the taker)"

echo
echo "=== building image coinswap/coinswap:latest ==="
docker build -f docker/Dockerfile -t coinswap/coinswap:latest . 2>&1

echo
echo "=== new image ==="
docker image inspect coinswap/coinswap:latest --format 'created: {{.Created}}'
