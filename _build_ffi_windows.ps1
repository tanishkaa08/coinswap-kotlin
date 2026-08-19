$ErrorActionPreference = "Stop"
$ndk = "C:\Users\TANISHKA\AppData\Local\Android\Sdk\ndk\26.3.11579264"
$bin = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin"
$clang = Join-Path $bin "aarch64-linux-android24-clang.cmd"
$ar = Join-Path $bin "llvm-ar.exe"

if (-not (Test-Path $clang)) { throw "missing $clang" }

$env:PATH = "$bin;$env:PATH"
$env:ANDROID_NDK_HOME = $ndk
$env:ANDROID_NDK_ROOT = $ndk
$env:CC_aarch64_linux_android = $clang
$env:AR_aarch64_linux_android = $ar
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = $clang
$env:CFLAGS_aarch64_linux_android = "-D__ANDROID_MIN_SDK_VERSION__=24"
$env:CFLAGS = "-D__ANDROID_MIN_SDK_VERSION__=24"

$ffi = "D:\dev\coinswap-ffi\ffi-commons"
$outJni = "D:\dev\coinswap-ffi\coinswap-kotlin\lib\src\main\jniLibs\arm64-v8a"
$lib = "D:\dev\coinswap-ffi\ffi-commons\target\aarch64-linux-android\release-smaller\libcoinswap_ffi.so"

Set-Location $ffi
Write-Host "=== building aarch64-linux-android ==="
cargo build --profile release-smaller --target aarch64-linux-android
if (-not (Test-Path $lib)) { throw "missing $lib" }
New-Item -ItemType Directory -Force -Path $outJni | Out-Null
Copy-Item $lib (Join-Path $outJni "libcoinswap_ffi.so") -Force
Write-Host "=== staged $(Join-Path $outJni 'libcoinswap_ffi.so') ==="
Get-Item (Join-Path $outJni "libcoinswap_ffi.so") | Format-List FullName, Length, LastWriteTime
