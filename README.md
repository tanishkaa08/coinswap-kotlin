# CoinSwap Mobile (Kotlin)

Android **taker** client for [CoinSwap](https://github.com/citadel-tech/coinswap): multi-hop Bitcoin swaps over Tor, with an Electrum wallet backend (no Bitcoin Core RPC on the phone).

Built with **Jetpack Compose** + official **UniFFI** bindings to the Rust taker (`libcoinswap_ffi.so`).

## What this app does

- Create / unlock a taker wallet (passcode)
- Sync balance and UTXOs via **Electrum**
- Discover makers over **Nostr** (marketplace)
- Connect to makers through **Orbot** SOCKS (`127.0.0.1:9050`)
- Run a **Legacy** coinswap (funding → ProofOfFunding → multi-hop contracts)
- Show wallet history, swap reports, and recovery status

It does **not** run a Bitcoin node. Chain data comes from Electrum; maker traffic goes over Tor via Orbot.

## Architecture

```text
Compose UI
  → ViewModels
    → CoinswapRepository
      → Generated UniFFI Kotlin (org.coinswap)
        → libcoinswap_ffi.so
          → Rust Taker
            → Electrum (clearnet or lab) + Orbot SOCKS (makers)
```

## Requirements

| Piece | Notes |
|--------|--------|
| Android phone/emulator | `arm64-v8a`, minSdk 26 |
| [Orbot](https://guardianproject.info/apps/orbot/) | SOCKS on `127.0.0.1:9050` (“Open Proxy on Localhost”) |
| Sibling [coinswap-ffi](https://github.com/citadel-tech/coinswap-ffi) | `electrum` branch, generated Kotlin + `.so` |
| JDK 17 + Android SDK | for building |

## Native bindings

From a sibling checkout of coinswap-ffi (`../coinswap-ffi` or `-PcoinswapFfiRoot=...`):

- `coinswap-kotlin/lib/src/main/kotlin/org/coinswap/coinswap.kt`
- `coinswap-kotlin/lib/src/main/jniLibs/arm64-v8a/libcoinswap_ffi.so`

Wired in `app/build.gradle.kts` via `sourceSets` (do not hand-edit generated files).

## Build

```bash
# UniFFI artifacts must already exist under coinswap-ffi
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Electrum URL (baked at build time)

Default in `app/build.gradle.kts` is **public signet TLS**:

```text
ssl://electrum.citadelfoss.xyz:50002
```

Override:

```bash
# Signet TLS (default if electrumUrl is unset)
./gradlew :app:assembleDebug

# Signet plaintext (if TLS is blocked on your network)
./gradlew :app:assembleDebug -PelectrumUrl=tcp://electrum.citadelfoss.xyz:50001

# Local regtest electrs (USB adb reverse — see below)
./gradlew :app:assembleDebug -PelectrumUrl=tcp://127.0.0.1:50001
```

Or set `electrumUrl=...` in `gradle.properties`.

Login probes reachable Electrum endpoints and labels the network **Signet** vs **Regtest** from the URL.

---

## Testing

### A) Signet: no PC dependency

Use this to test against the public network (mentor Electrum + live Tor makers).

1. Build **without** a localhost `electrumUrl` (or use the TLS default above).
2. Install the APK; force-stop any old lab session if you previously used regtest.
3. Start **Orbot**, enable Tor, enable **Open Proxy on Localhost** (SOCKS `9050`). Prefer non-VPN / allow-clearnet so Electrum can reach `electrum.citadelfoss.xyz` directly.
4. Open the app → set a passcode → connect. UI should show **Signet** and `electrum.citadelfoss.xyz`.
5. Wallet name used: `taker-signet`.
6. Fund the receive address with **signet** coins (faucet / peer).
7. **Markets** → sync until makers appear (Nostr + onion reachability).
8. **Swap** → pick amount / makers → start. Wait for funding confirms (signet blocks can take several minutes).

**Expect:** Electrum sync and maker discovery without any Docker/USB lab. Swap success still depends on makers staying online through ProofOfFunding over Tor; drops show up as `UnexpectedEof` / “failed to fill whole buffer”.

Plaintext fallback server: `tcp://electrum.citadelfoss.xyz:50001`.

### B) Regtest lab : local electrs + makers (USB)

Use this when you need controlled makers / fast confirms.

1. Bring up the coinswap-ffi electrum regtest stack (bitcoind, electrs `:50001`, makerd ×2, Tor). Keep a **slow** mine loop (≈1 block / 20–25s) so hop confirms stay within the taker’s height-gap tolerance.
2. On the PC, map makers for the phone (optional direct SOCKS helper):

   ```bash
   python scripts/maker-direct-socks.py   # :19050 → local makers
   ```

3. USB reverse:

   ```bash
   adb reverse tcp:50001 tcp:50001
   adb reverse tcp:9050 tcp:19050   # if using maker-direct-socks; else point 9050 at Orbot carefully
   adb reverse tcp:9051 tcp:9051
   ```

4. Build with loopback Electrum:

   ```bash
   ./gradlew :app:assembleDebug -PelectrumUrl=tcp://127.0.0.1:50001
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

5. Login should show **Regtest**. Wallet name: `taker-wallet`.
6. Fund the regtest address from lab bitcoind; mine confirms; run a swap.

**Note:** For lab maker SOCKS, Orbot must not own `:9050` on the phone if `adb reverse` is mapping that port to the PC helper.

### Quick smoke checklist

- [ ] Login reaches Electrum (Signet or Regtest label correct)
- [ ] Balance / UTXOs sync
- [ ] Markets lists makers (Orbot up)
- [ ] Swap prepares and broadcasts funding
- [ ] Funding confirms (signet: wait; regtest: mine)
- [ ] Swap completes or fails with a clear report under Swap reports / recovery

---

## Tor / Orbot notes

- Makers always use SOCKS `127.0.0.1:9050` (hardcoded in the FFI stack).
- Clearnet Electrum (citadelfoss) connects **directly** (not through Orbot), unless you use an `.onion` Electrum URL.
- In-app Tor was removed; embedded Tor raced maker sessions and caused ProofOfFunding EOFs.

## Project layout

```text
app/src/main/java/.../   Compose screens, ViewModels, repository, Tor helpers
scripts/                 Lab helpers (e.g. maker-direct-socks.py)
```

## License

See [LICENSE](LICENSE).
