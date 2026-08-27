# CoinSwap Mobile (Kotlin)

Android **taker** client for [CoinSwap](https://github.com/citadel-tech/coinswap): multi-hop Bitcoin swaps over Tor, with an **Electrum** wallet backend.

Built with **Jetpack Compose** + official **UniFFI** bindings to the Rust taker (`libcoinswap_ffi.so`).

This repository is **only the phone taker**. It does not run makers or a Bitcoin node.

## What this app does

- Create / unlock a taker wallet (passcode)
- Sync balance and UTXOs via **Electrum**
- Discover makers over **Nostr** (marketplace)
- Connect to makers through SOCKS at `127.0.0.1:9050` (Orbot on signet, or a lab mapper on regtest)
- Run a **Legacy** coinswap (funding → ProofOfFunding → multi-hop contracts)
- Show wallet history, swap reports, and recovery status

## Architecture

```text
Compose UI
  → ViewModels
    → CoinswapRepository
      → Generated UniFFI Kotlin (org.coinswap)
        → libcoinswap_ffi.so
          → Rust Taker
            → Electrum (wallet / chain)
            → SOCKS 127.0.0.1:9050 (makers)
```

## Requirements

| Piece | Notes |
|--------|--------|
| Android phone/emulator | `arm64-v8a`, minSdk 26 |
| Sibling [coinswap-ffi](https://github.com/citadel-tech/coinswap-ffi) | `electrum` branch; generated Kotlin + `.so` |
| JDK 17 + Android SDK | for building |
| **Signet:** [Orbot](https://guardianproject.info/apps/orbot/) | SOCKS on `127.0.0.1:9050` (“Open Proxy on Localhost”) |
| **Regtest lab:** Docker + USB `adb` | electrs + makers |

## Native bindings

From a sibling checkout of coinswap-ffi (`../coinswap-ffi` or `-PcoinswapFfiRoot=...`):

- `coinswap-kotlin/lib/src/main/kotlin/org/coinswap/coinswap.kt`
- `coinswap-kotlin/lib/src/main/jniLibs/arm64-v8a/libcoinswap_ffi.so`

Wired in `app/build.gradle.kts` via `sourceSets` (do not hand-edit generated files).

## Build

```bash
# UniFFI artifacts must already exist under coinswap-ffi
./gradlew :app:assembleDebug
# Windows: .\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Electrum URL (baked at build time)

Default in `app/build.gradle.kts` is **public signet TLS**:

```text
ssl://electrum.citadelfoss.xyz:50002
```

| Goal | Command |
|------|---------|
| Signet TLS (default) | `./gradlew :app:assembleDebug` |
| Signet plaintext (TLS blocked) | `./gradlew :app:assembleDebug -PelectrumUrl=tcp://electrum.citadelfoss.xyz:50001` |
| Regtest lab electrs | `./gradlew :app:assembleDebug -PelectrumUrl=tcp://127.0.0.1:50001` |

Or set `electrumUrl=...` in `gradle.properties`.

Login probes Electrum and labels the network **Signet** vs **Regtest** from the URL.

| Network | Wallet name |
|---------|-------------|
| Signet (non-loopback Electrum) | `taker-signet` |
| Regtest / `127.0.0.1` Electrum | `taker-wallet` |

---

## Two test modes (pick one)

| | **A) Public signet** | **B) Local regtest lab** |
|--|----------------------|---------------------------|
| Electrum | `ssl://electrum.citadelfoss.xyz:50002` | `tcp://127.0.0.1:50001` → PC electrs |
| Who provides makers | Public Tor makers | Docker `makerd1` / `makerd2` |
| Phone `:9050` | **Orbot** | **`adb reverse` → `maker-direct-socks.py`** |
| Orbot | **Required (on)** | **Must be off** (or it steals `:9050`) |
| PC / Docker | Not required | Required |
| Confirms | Wait for signet blocks | Mine locally (~20–25s/block) |

The app **always** connects makers via SOCKS `127.0.0.1:9050`. That port must be either Orbot **or** the lab mapper — never both.

---

## A) Signet : no PC dependency

Use this against the public network (mentor Electrum + live Tor makers).

1. Build **without** a localhost `electrumUrl` (TLS default).
2. Install the APK. If you previously used regtest, clear app data or force-stop so you don’t reuse the lab wallet/session.
3. Start **Orbot**: Tor on + **Open Proxy on Localhost** (SOCKS `9050`). Prefer allow-clearnet / non-VPN so Electrum can reach `electrum.citadelfoss.xyz` **directly** (not through Tor).
4. Open the app → passcode → connect. UI should show **Signet** and citadelfoss.
5. Fund `taker-signet` with signet coins (faucet / peer).
6. **Markets** → sync (Nostr + onion reachability via Orbot).
7. **Swap** → amount / makers → start. Signet confirms can take several minutes.

**Expect:** Electrum sync and maker discovery without Docker/USB. Swap success still depends on remote makers staying up through ProofOfFunding; drops often look like `UnexpectedEof` / “failed to fill whole buffer”.

Plaintext fallback: `tcp://electrum.citadelfoss.xyz:50001`.

---

## B) Regtest lab : local electrs + makers (USB)

Use this for controlled makers and faster confirms. Paths below assume:

- App repo: `d:\dev\coinswap-kotlin` (or your clone)
- FFI compose: `d:\dev\coinswap-ffi\ffi-commons\docker-compose.electrum-regtest.yml`

### What runs where

```text
PC Docker:
  coinswap-bitcoind     (regtest chain + funds makers / miner wallet)
  coinswap-electrs      (:50001 — Electrum for the PHONE)
  coinswap-tor          (onion for makers)
  coinswap-makerd1/2    (makers via bitcoind RPC, listen 16102/16103)
  maker-port-1/2        (expose makers on host 26102/26103)

PC process:
  python scripts/maker-direct-socks.py   # SOCKS :19050 → local makers

Phone (USB):
  adb reverse 50001 → PC electrs
  adb reverse 9050  → PC :19050 (mapper)   ← Orbot must NOT bind 9050
  app Electrum URL = tcp://127.0.0.1:50001
```

### Step-by-step commands

**Terminal 1 : start stack** (`coinswap-ffi/ffi-commons`):

```powershell
cd d:\dev\coinswap-ffi\ffi-commons
docker compose -f docker-compose.electrum-regtest.yml up -d
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

One-time miner wallet (if needed):

```powershell
docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password -rpcport=18442 createwallet miner
docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password -rpcport=18442 -rpcwallet=miner -generate 101
```

**Terminal 2 : slow mine** (leave running; ~1 block / 25s so hop gaps stay ≤ taker tolerance):

```powershell
while ($true) {
  docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password -rpcport=18442 -rpcwallet=miner -generate 1 | Out-Null
  Start-Sleep 25
}
```

Or: `powershell -ExecutionPolicy Bypass -File d:\dev\coinswap-kotlin\scripts\mine-regtest-loop.ps1`

**Terminal 3 : maker SOCKS mapper** (`coinswap-kotlin`, leave running):

```powershell
cd d:\dev\coinswap-kotlin
python scripts\maker-direct-socks.py
# listens on 127.0.0.1:19050
```

**Terminal 4 : phone wiring + build** (`coinswap-kotlin`):

```powershell
# Orbot must not own phone :9050
adb shell am force-stop org.torproject.android

adb reverse --remove-all
adb reverse tcp:50001 tcp:50001
adb reverse tcp:9050 tcp:19050
adb reverse tcp:9051 tcp:9051
adb reverse --list

cd d:\dev\coinswap-kotlin
.\gradlew.bat :app:installDebug -PelectrumUrl=tcp://127.0.0.1:50001
```

Helper script (same reverses + optional Orbot stop):

```powershell
powershell -ExecutionPolicy Bypass -File d:\dev\coinswap-kotlin\scripts\phone-adb-reverse.ps1 -StopOrbot
```

**On the phone**

1. Open app → passcode → connect → UI shows **Regtest** (`taker-wallet`).
2. **Receive** → copy address.
3. Fund from PC (this Docker image often has a **small** miner balance — use `0.1`, not `1`):

```powershell
docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password -rpcport=18442 -rpcwallet=miner getbalances
docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password -rpcport=18442 -rpcwallet=miner sendtoaddress ADDRESS 0.1
docker exec coinswap-bitcoind bitcoin-cli -regtest -rpcuser=user -rpcpassword=password -rpcport=18442 -rpcwallet=miner -generate 1
```

4. Sync balance → **Markets** → Sync → **Swap**.


### Regtest via Orbot instead of the mapper

```powershell
adb reverse --remove tcp:9050
adb reverse tcp:50001 tcp:50001   # keep Electrum
# Start Orbot SOCKS 9050; leave Docker makers/tor up
```

Prefer the mapper for a reliable lab swap.

### Regtest troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Markets offline / cannot reach makers | Orbot holding `:9050` | `adb shell am force-stop org.torproject.android`; re-run reverses |
| Login not Regtest | APK built without loopback Electrum | Rebuild with `-PelectrumUrl=tcp://127.0.0.1:50001` |
| `Insufficient funds` on `sendtoaddress 1` | Miner wallet balance small on this image | `getbalances`; send `0.1` |
| Makers crash-loop / “Wallet is currently rescanning” | Burst-mined thousands of blocks; maker sync stuck | Stop mine loop; let makers sync; avoid mining hundreds of blocks at once |
| Electrum unreachable | No reverse / electrs down | `adb reverse --list`; `docker logs coinswap-electrs` |

Probe Electrum from PC:

```powershell
python d:\dev\coinswap-kotlin\scripts\probe_electrum.py 127.0.0.1 50001
```

---

## Tor / SOCKS notes

- Makers always use SOCKS `127.0.0.1:9050` (hardcoded in the FFI stack).
- Clearnet Electrum (citadelfoss) connects **directly**, not through Orbot, unless you use an `.onion` Electrum URL.
- In-app / embedded Tor was removed; it raced maker sessions and caused ProofOfFunding EOFs.

## Quick smoke checklist

- [ ] Login Electrum label correct (Signet or Regtest)
- [ ] Balance / UTXOs sync
- [ ] Markets lists makers (right `:9050` owner for your mode)
- [ ] Swap prepares and broadcasts funding
- [ ] Funding confirms (signet: wait; regtest: slow mine)
- [ ] Completes or fails with a clear report under Swap reports / recovery

## Project layout

```text
app/src/main/java/.../   Compose screens, ViewModels, repository
scripts/                 Lab helpers (maker-direct-socks.py, phone-adb-reverse.ps1, …)
```

## License

See [LICENSE](LICENSE).
