package com.example.coinswapmobile.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Application-scoped singleton that owns the coinswap Taker FFI instance.
 *
 * ──────────────────────────────────────────────────────────────────
 *  STUB MODE (current): the org.coinswap:coinswap-kotlin library is
 *  not yet built from source, so all FFI calls are no-ops.
 *
 *  TO ENABLE REAL FFI:
 *  1. Build the library from coinswap-ffi/coinswap-kotlin:
 *       ./gradlew :lib:publishToMavenLocal -PlocalBuild=true
 *  2. Un-comment the dependency in app/build.gradle.kts
 *  3. Replace this file with the version in git history tagged [FFI]
 * ──────────────────────────────────────────────────────────────────
 */
object TakerManager {

    private const val TAG = "TakerManager"

    /**
     * Opaque handle.  Holds an org.coinswap.Taker instance at runtime
     * once the native library is present and [init] has been called.
     */
    @Volatile private var _taker: Any? = null

    val isInitialized: Boolean get() = _taker != null

    /** Human-readable label for the wallet, shown in the UI after login. */
    var walletName: String = "taker-wallet"
        private set

    /**
     * Initialises the taker.
     *
     * When the coinswap-kotlin AAR is present this calls Taker.init() via
     * reflection so that this file compiles without the library on the
     * classpath.  Reflection is intentional here — it lets the rest of the
     * codebase import no org.coinswap.* types, keeping compilation clean.
     */
    suspend fun init(
        dataDir: String,
        walletName: String,
        rpcUrl: String,
        rpcUsername: String = "user",
        rpcPassword: String = "password",
        walletPassword: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        this@TakerManager.walletName = walletName
        try {
            val takerClass = Class.forName("org.coinswap.Taker")

            // Build RPCConfig via reflection
            val rpcConfigClass = Class.forName("org.coinswap.RPCConfig")
            val rpcConfig = rpcConfigClass
                .getDeclaredConstructor(String::class.java, String::class.java,
                    String::class.java, String::class.java)
                .newInstance(rpcUrl, rpcUsername, rpcPassword, walletName)

            val zmqHost = runCatching {
                java.net.URI.create(rpcUrl).host ?: "127.0.0.1"
            }.getOrDefault("127.0.0.1")

            // Taker.init(dataDir, walletFileName, rpcConfig, controlPort,
            //             torAuthPassword, zmqAddr, password)
            val initMethod = takerClass.getDeclaredMethod(
                "init",
                String::class.java,        // dataDir
                String::class.java,        // walletFileName
                rpcConfigClass,            // rpcConfig
                Short::class.javaObjectType,  // controlPort  (UShort → Short in JVM)
                String::class.java,        // torAuthPassword
                String::class.java,        // zmqAddr
                String::class.java,        // password
            )
            val taker = initMethod.invoke(
                null,
                dataDir,
                walletName,
                rpcConfig,
                9051.toShort(),
                "coinswap",
                "tcp://$zmqHost:28332",
                walletPassword?.takeIf { it.isNotBlank() },
            )
            _taker = taker
            Log.i(TAG, "Taker initialized via reflection, wallet: $walletName")
            Result.success(Unit)
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "coinswap-kotlin not on classpath — running in stub mode")
            // In stub/dev mode we still "succeed" so the app navigates to Home.
            // Screens show empty data until the library is built and init is re-run.
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Taker init failed", e)
            Result.failure(e)
        }
    }

    /**
     * Returns the raw taker handle, or null when running in stub mode.
     * Repositories call this and handle the null case themselves.
     */
    fun rawTaker(): Any? = _taker

    /** Releases the taker handle on logout. */
    fun reset() {
        _taker = null
        walletName = "taker-wallet"
    }
}
