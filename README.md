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
            → Bitcoin Core RPC + ZMQ + Tor
```

No Electrum client and no custom JNI wrapper.

## Native bindings

This app consumes the generated UniFFI sources and ARM64 library from a sibling checkout:

- `../coinswap-ffi/coinswap-kotlin/lib/src/main/kotlin/org/coinswap/coinswap.kt`
- `../coinswap-ffi/coinswap-kotlin/lib/src/main/jniLibs/arm64-v8a/libcoinswap_ffi.so`

Wired in `app/build.gradle.kts` via `sourceSets` (generated files are not edited).
Override the checkout with `-PcoinswapFfiRoot=/path/to/coinswap-ffi` if needed.

Requires JNA (`net.java.dev.jna:jna`) for UniFFI library loading.

## Setup

On first launch you enter connection settings (with sensible defaults):

- RPC host, port, username, password
- ZMQ host/port (`tcp://host:port`)
- Tor SOCKS + control ports (optional auth)
- Wallet name + password

Then the app calls:

```kotlin
Taker.init(
    dataDir = appDataDir,
    walletFileName = cfg.walletName,
    rpcConfig = session.toRpcConfig(),
    controlPort = cfg.torControlPort.toUShort(),
    torAuthPassword = cfg.torAuthPassword.ifBlank { null },
    zmqAddr = cfg.zmqAddr,
    password = cfg.walletPassword.ifBlank { null },
)
```

Blank Tor auth / wallet passwords are passed as `null` (same as desktop). The live instance is kept in `TakerHolder`.
