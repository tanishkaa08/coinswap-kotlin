# CoinSwap Mobile (Kotlin)

Android Compose taker client over official UniFFI bindings.

## Architecture

```text
Compose UI
  → ViewModels
    → CoinswapRepository
      → Generated UniFFI Kotlin (org.coinswap)
        → libcoinswap_ffi.so
          → Rust Taker
            → Electrum (electrs) + Tor
```

The wallet talks to an Electrum server. Bitcoin Core RPC and ZMQ are not used by this app.
Maker discovery and swaps use **Orbot** SOCKS on `127.0.0.1:9050` (no in-app Tor).
Enable Orbot’s “Open Proxy on Localhost” so port 9050 is available.

## Native bindings

This app consumes the generated UniFFI sources and ARM64 library from a sibling checkout of
[coinswap-ffi](https://github.com/citadel-tech/coinswap-ffi) on the **electrum** branch:

- `../coinswap-ffi/coinswap-kotlin/lib/src/main/kotlin/org/coinswap/coinswap.kt`
- `../coinswap-ffi/coinswap-kotlin/lib/src/main/jniLibs/arm64-v8a/libcoinswap_ffi.so`

Wired in `app/build.gradle.kts` via `sourceSets` (generated files are not edited).
Override the checkout with `-PcoinswapFfiRoot=/path/to/coinswap-ffi` if needed.

Requires JNA (`net.java.dev.jna:jna`) for UniFFI library loading.

Rebuild the native library after checking out `electrum`:

```bash
git -C ../coinswap-ffi checkout electrum
# then run _build_ffi_master.sh from WSL with Android NDK
```

## Setup

On first launch you only set a wallet passcode. The app uses a single hardcoded wallet name
and connects to Electrum automatically:

```kotlin
Taker.init(
    dataDir = appDataDir,
    walletFileName = cfg.walletName,
    rpcConfig = null,
    controlPort = cfg.torControlPort.toUShort(),
    torAuthPassword = cfg.torAuthPassword.ifBlank { null },
    zmqAddr = TakerAppConfig.DUMMY_ZMQ_ADDR, // ignored for Electrum
    password = cfg.walletPassword.ifBlank { null },
    nostrRelays = null,
    backendConfig = BackendConfig(
        kind = "electrum",
        url = "ssl://electrum.citadelfoss.xyz:50002", // Neo VPS signet (TLS)
        // Plaintext fallback: tcp://electrum.citadelfoss.xyz:50001
        username = null,
        password = null,
        walletName = null,
        zmqAddr = null,
        socks5 = null, // clearnet Electrum — set only for .onion Electrum URLs
        timeout = null,
        pollIntervalSecs = null,
        maxRetries = null,
    ),
)
```

Pass `-PelectrumUrl=ssl://electrum.citadelfoss.xyz:50002` (default) or a local
`tcp://<lan-ip>:50001` for regtest. The live instance is kept in `TakerHolder`.
Clearnet Electrum connects directly; makers still use Orbot SOCKS on `9050`.
