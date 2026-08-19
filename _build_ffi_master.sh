#!/bin/bash
# Rebuild libcoinswap_ffi.so (arm64-v8a) from citadel-tech/coinswap-ffi electrum
# and regenerate the UniFFI Kotlin bindings the Android app compiles against.
set -euo pipefail

export ANDROID_NDK_ROOT="$HOME/Android/Sdk/ndk/26.3.11579264"
NDK_BIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"
export PATH="$NDK_BIN:$HOME/.cargo/bin:$PATH"
export CFLAGS="-D__ANDROID_MIN_SDK_VERSION__=24"
export AR="llvm-ar"

TARGET="aarch64-linux-android"
LIB_NAME="libcoinswap_ffi.so"
FFI_DIR="/mnt/d/dev/coinswap-ffi/ffi-commons"
OUT_JNI="/mnt/d/dev/coinswap-ffi/coinswap-kotlin/lib/src/main/jniLibs/arm64-v8a"
OUT_KT="/mnt/d/dev/coinswap-ffi/coinswap-kotlin/lib/src/main/kotlin/"

echo "=== toolchain ==="
cargo --version
rustc --version
echo "ndk: $ANDROID_NDK_ROOT"

cd "$FFI_DIR"

echo "=== dependency source ==="
grep -n '^coinswap' Cargo.toml

echo "=== building $TARGET (release-smaller) ==="
CC="aarch64-linux-android24-clang" \
CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="aarch64-linux-android24-clang" \
cargo build --profile release-smaller --target "$TARGET"

echo "=== staging .so ==="
mkdir -p "$OUT_JNI"
cp "./target/$TARGET/release-smaller/$LIB_NAME" "$OUT_JNI/"
ls -la "$OUT_JNI/$LIB_NAME"

echo "=== regenerating UniFFI kotlin bindings ==="
mkdir -p "$OUT_KT"
cargo run --bin uniffi-bindgen generate \
  --library "./target/$TARGET/release-smaller/$LIB_NAME" \
  --language kotlin --out-dir "$OUT_KT" --no-format

echo "=== done ==="
ls -la "$OUT_KT/org/coinswap/"
