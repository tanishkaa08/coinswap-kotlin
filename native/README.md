# native/coinswap_mobile — coinswap PR #874 Electrum bridge

This crate compiles to `libcoinswap_mobile.so` (loaded by the app via
`System.loadLibrary("coinswap_mobile")`). It wraps **coinswap PR #874's**
`Wallet<ElectrumBackend>` and exposes a minimal wallet surface over JNI,
returning JSON strings to Kotlin. The Kotlin layer (`CoinSwapNative`,
`CoinswapRepository`, `WalletViewModel`) does not change.

## Step 0 — Vendor the PR branch

The crate depends on `coinswap = { path = "../../vendor/coinswap" }`, so put the
PR source there:

```bash
# from the repo root
git clone https://github.com/citadel-tech/coinswap vendor/coinswap
cd vendor/coinswap
git fetch origin pull/874/head:electrum-874
git checkout electrum-874
cd ../..
```

(Confirm it built: `cargo run --example electrum_wallet_basic` inside
`vendor/coinswap` should work on your PC.)

## Step 1 — Toolchain (do the native build on Linux/WSL)

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk
export ANDROID_NDK_HOME=~/Android/Sdk/ndk/<version>
```

## Step 2 — Sanity-check the wrapper compiles

```bash
cd native/coinswap_mobile
cargo check
```

If `cargo check` fails, it's almost always one of:
- a coinswap API rename (adjust the call in `src/lib.rs`, see the header there);
- the `list_all_utxo()` element fields (adjust the 4 lines in `state_json`);
- a coinswap dependency that doesn't cross-compile — try disabling optional
  coinswap features in `vendor/coinswap/Cargo.toml` (e.g. Tor/`tor`), since the
  wallet path doesn't need them.

## Step 3 — Build the Android libs into the app

```bash
cargo ndk -t arm64-v8a -t armeabi-v7a -o ../../app/src/main/jniLibs build --release
```

Produces:
```
app/src/main/jniLibs/arm64-v8a/libcoinswap_mobile.so       # v8 (most phones)
app/src/main/jniLibs/armeabi-v7a/libcoinswap_mobile.so     # v7
```
Add `-t x86_64` too if you want to run on the Android Studio emulator.

**Also bundle `libc++_shared.so`** (the Rust cdylib links against it). Copy from
your NDK for each ABI, e.g.:
```
$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so
  -> app/src/main/jniLibs/arm64-v8a/libc++_shared.so
```
Without it, `System.loadLibrary("coinswap_mobile")` fails at runtime even when
`libcoinswap_mobile.so` is in the APK (`dlopen failed: library "libc++_shared.so" not found`).

Verify JNI exports match Kotlin:
```bash
nm -D app/src/main/jniLibs/arm64-v8a/libcoinswap_mobile.so | grep Java_
```
Expected symbols:
`Java_com_example_coinswapmobile_data_CoinSwapNative_{initElectrumWallet,syncWallet,getBalance,getNewAddress,listUtxos}`

## Notes

- **Network**: coinswap decides the wallet network internally. Point the app at
  an Electrum server on the matching network (regtest/signet/testnet/mainnet).
  The app default is `ssl://electrum.blockstream.info:60002` (testnet).
- A wallet file is created on first init at
  `<app filesDir>/wallets/<walletName>`. Fund the address shown on the Receive
  screen to see a real balance after Sync.
- The JSON contract between Rust and Kotlin is fixed — if you change coinswap
  calls, keep the output JSON keys the same and the Android side keeps working.
