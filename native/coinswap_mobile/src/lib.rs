//! coinswap_mobile
//! =================
//! JNI wrapper around **coinswap PR #874's** `Wallet<ElectrumBackend>`. It
//! exposes a minimal wallet surface to the Android app and returns JSON.
//!
//! Exposed functions (all return a JSON string):
//!   - initElectrumWallet(dataDir, electrumUrl, walletName)
//!   - syncWallet()
//!   - getBalance()
//!   - getNewAddress()
//!   - listUtxos()
//!
//! The API used here comes straight from the PR's own
//! `examples/electrum_wallet_basic.rs`:
//!
//! ```ignore
//! use coinswap::wallet::{AddressType, ElectrumBackend, ElectrumConfig, Wallet};
//! let cfg = ElectrumConfig { url, wallet_name };
//! let mut wallet = Wallet::<ElectrumBackend>::init(&wallet_path, &cfg, None)?;
//! wallet.sync_and_save()?;
//! let balances = wallet.get_balances()?;      // .spendable/.regular/.swap/.fidelity/.contract
//! let addr = wallet.get_next_external_address(AddressType::P2TR)?;
//! let utxos = wallet.list_all_utxo();
//! ```

use std::path::PathBuf;
use std::sync::Mutex;

use coinswap::wallet::{AddressType, ElectrumBackend, ElectrumConfig, Wallet};

use jni::objects::{JObject, JString};
use jni::sys::jstring;
use jni::JNIEnv;

use once_cell::sync::Lazy;
use serde_json::json;

/// The live wallet, held across JNI calls. Wallet methods need `&mut self`,
/// hence a `Mutex`.
static WALLET: Lazy<Mutex<Option<Wallet<ElectrumBackend>>>> = Lazy::new(|| Mutex::new(None));

fn wallet_file_path(data_dir: &str, wallet_name: &str) -> PathBuf {
    let dir = std::path::Path::new(data_dir).join("wallets");
    let _ = std::fs::create_dir_all(&dir);
    dir.join(wallet_name)
}

fn now_unix() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// Serialise the current wallet state into the JSON shape the Kotlin layer expects.
fn state_json(wallet: &mut Wallet<ElectrumBackend>) -> Result<String, String> {
    let balances = wallet
        .get_balances()
        .map_err(|e| format!("get_balances: {e:?}"))?;

    // list_all_utxo() returns the wallet's UTXO entries (bitcoind-rpc
    // ListUnspentResultEntry). If `cargo check` complains about a field name
    // or that this returns tuples, adjust the four lines below and re-run.
    let mut utxos_json = Vec::new();
    for u in wallet.list_all_utxo() {
        utxos_json.push(json!({
            "txid": u.txid.to_string(),
            "vout": u.vout,
            "amountSats": u.amount.to_sat(),
            "confirmations": u.confirmations,
        }));
    }

    Ok(json!({
        "balanceSats": balances.spendable.to_sat(),
        "confirmedSats": balances.spendable.to_sat(),
        "unconfirmedSats": 0,
        "backend": "ELECTRUM",
        "lastSyncUnix": now_unix(),
        "utxos": utxos_json,
    })
    .to_string())
}

// ──────────────────────────────────────────────────────────────────────────
// Core logic
// ──────────────────────────────────────────────────────────────────────────

fn do_init(data_dir: String, electrum_url: String, wallet_name: String) -> Result<String, String> {
    let wallet_path = wallet_file_path(&data_dir, &wallet_name);
    let cfg = ElectrumConfig {
        url: electrum_url,
        wallet_name: wallet_name.clone(),
    };

    // `init` creates the wallet on first run and loads it afterwards.
    // Third arg is the optional encryption passphrase.
    let mut wallet = Wallet::<ElectrumBackend>::init(&wallet_path, &cfg, None)
        .map_err(|e| format!("Wallet init failed: {e:?}"))?;

    wallet
        .sync_and_save()
        .map_err(|e| format!("initial sync failed: {e:?}"))?;

    let state = state_json(&mut wallet)?;
    *WALLET.lock().unwrap() = Some(wallet);
    Ok(state)
}

fn do_sync() -> Result<String, String> {
    let mut guard = WALLET.lock().unwrap();
    let wallet = guard
        .as_mut()
        .ok_or_else(|| "wallet not initialized".to_string())?;
    wallet
        .sync_and_save()
        .map_err(|e| format!("sync failed: {e:?}"))?;
    state_json(wallet)
}

fn do_balance() -> Result<String, String> {
    let mut guard = WALLET.lock().unwrap();
    let wallet = guard
        .as_mut()
        .ok_or_else(|| "wallet not initialized".to_string())?;
    state_json(wallet)
}

fn do_new_address() -> Result<String, String> {
    let mut guard = WALLET.lock().unwrap();
    let wallet = guard
        .as_mut()
        .ok_or_else(|| "wallet not initialized".to_string())?;
    let address = wallet
        .get_next_external_address(AddressType::P2TR)
        .map_err(|e| format!("get_next_external_address: {e:?}"))?;
    // Persist the advanced address index.
    let _ = wallet.sync_and_save();
    Ok(json!({ "address": address.to_string() }).to_string())
}

// ──────────────────────────────────────────────────────────────────────────
// JNI boundary
// ──────────────────────────────────────────────────────────────────────────

fn read_jstring(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(|v| v.into()).unwrap_or_default()
}

fn envelope(result: Result<String, String>) -> String {
    match result {
        Ok(json_string) => json_string,
        Err(msg) => json!({ "error": msg }).to_string(),
    }
}

fn to_jstring(mut env: JNIEnv, s: String) -> jstring {
    env.new_string(s)
        .unwrap_or_else(|_| env.new_string("{\"error\":\"jstring alloc failed\"}").unwrap())
        .into_raw()
}

fn init_logger() {
    #[cfg(target_os = "android")]
    {
        use std::sync::Once;
        static ONCE: Once = Once::new();
        ONCE.call_once(|| {
            android_logger::init_once(
                android_logger::Config::default().with_max_level(log::LevelFilter::Info),
            );
        });
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_coinswapmobile_data_CoinSwapNative_initElectrumWallet<
    'local,
>(
    mut env: JNIEnv<'local>,
    _this: JObject<'local>,
    data_dir: JString<'local>,
    electrum_url: JString<'local>,
    wallet_name: JString<'local>,
) -> jstring {
    init_logger();
    let data_dir = read_jstring(&mut env, &data_dir);
    let electrum_url = read_jstring(&mut env, &electrum_url);
    let wallet_name = read_jstring(&mut env, &wallet_name);
    let out = envelope(do_init(data_dir, electrum_url, wallet_name));
    to_jstring(env, out)
}

#[no_mangle]
pub extern "system" fn Java_com_example_coinswapmobile_data_CoinSwapNative_syncWallet<'local>(
    env: JNIEnv<'local>,
    _this: JObject<'local>,
) -> jstring {
    to_jstring(env, envelope(do_sync()))
}

#[no_mangle]
pub extern "system" fn Java_com_example_coinswapmobile_data_CoinSwapNative_getBalance<'local>(
    env: JNIEnv<'local>,
    _this: JObject<'local>,
) -> jstring {
    to_jstring(env, envelope(do_balance()))
}

#[no_mangle]
pub extern "system" fn Java_com_example_coinswapmobile_data_CoinSwapNative_getNewAddress<'local>(
    env: JNIEnv<'local>,
    _this: JObject<'local>,
) -> jstring {
    to_jstring(env, envelope(do_new_address()))
}

#[no_mangle]
pub extern "system" fn Java_com_example_coinswapmobile_data_CoinSwapNative_listUtxos<'local>(
    env: JNIEnv<'local>,
    _this: JObject<'local>,
) -> jstring {
    to_jstring(env, envelope(do_balance()))
}
