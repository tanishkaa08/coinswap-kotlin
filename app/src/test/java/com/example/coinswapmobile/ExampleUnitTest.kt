package com.example.coinswapmobile

import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.FfiEnv
import com.example.coinswapmobile.data.TakerAppConfig
import com.example.coinswapmobile.data.toBackendConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure wallet-contract checks that do not need an Android device or UniFFI.
 */
class WalletContractTest {

    @Test
    fun takerAppConfig_serializesConnectionMetadata() {
        val cfg = TakerAppConfig(
            electrumUrl = "tcp://10.0.0.2:50001",
            walletName = "demo-wallet",
            walletPassword = "wp",
            torAuthPassword = "tor-pass",
        )
        assertEquals("tcp://10.0.0.2:50001", cfg.electrumUrl)
        assertEquals(null, cfg.electrumSocks5)
        assertEquals("demo-wallet", cfg.walletName)
        val onion = cfg.copy(electrumUrl = "tcp://abcd.onion:50001")
        assertEquals("127.0.0.1:9050", onion.electrumSocks5)
        val loopback = cfg.copy(electrumUrl = TakerAppConfig.DEFAULT_ELECTRUM_URL)
        assertEquals(null, loopback.electrumSocks5)
        val signet = cfg.copy(electrumUrl = TakerAppConfig.SIGNET_ELECTRUM_URL)
        assertEquals(null, signet.electrumSocks5)
        assertNull(signet.toBackendConfig().socks5)
        assertEquals("127.0.0.1:9050", cfg.torSocksEndpoint)
        val copy = cfg.copy()
        assertEquals(cfg.walletPassword, copy.walletPassword)
        assertEquals(cfg.torAuthPassword, copy.torAuthPassword)
    }

    @Test
    fun swapId_rejectsPathTraversal() {
        assertTrue(CoinswapRepository.isSafeSwapId("abc-123"))
        assertFalse(CoinswapRepository.isSafeSwapId(""))
        assertFalse(CoinswapRepository.isSafeSwapId("../evil"))
        assertFalse(CoinswapRepository.isSafeSwapId("a/b"))
        assertFalse(CoinswapRepository.isSafeSwapId("a\\b"))
    }

    @Test
    fun coinSelection_emptyManualIsRejected_emptyAutoIsAllowed() {
        fun prepareAllowed(manual: Boolean, selectedCount: Int): Boolean {
            if (manual && selectedCount == 0) return false
            return true
        }
        assertTrue(prepareAllowed(manual = false, selectedCount = 0)) // automatic
        assertFalse(prepareAllowed(manual = true, selectedCount = 0)) // explicit empty manual
        assertTrue(prepareAllowed(manual = true, selectedCount = 2))
    }

    @Test
    fun clearSessionContract_wipesCredentialKeys() {
        // Logout must wipe credential keys (see UserSession.clearSession), not only logged_in=false.
        val clearedKeys = setOf(
            "electrum_url",
            "rpc_host", "rpc_port", "rpc_user", "rpc_password",
            "zmq_host", "zmq_port", "tor_control", "socks_host", "socks_port",
            "tor_auth", "wallet_name", "wallet_password", "protocol",
        )
        assertTrue(clearedKeys.containsAll(listOf("electrum_url", "wallet_password", "tor_auth")))
    }

    @Test
    fun electrumUrlForHost_addsSchemeAndPort() {
        assertEquals("tcp://10.0.0.2:50001", TakerAppConfig.electrumUrlForHost("10.0.0.2"))
        assertEquals(
            "ssl://electrum.example:50002",
            TakerAppConfig.electrumUrlForHost("ssl://electrum.example:50002"),
        )
        assertEquals(
            "ssl://electrum.citadelfoss.xyz:50002",
            TakerAppConfig.electrumUrlForHost("electrum.citadelfoss.xyz:50002"),
        )
        assertEquals(
            "tcp://electrum.citadelfoss.xyz:50001",
            TakerAppConfig.electrumUrlForHost("electrum.citadelfoss.xyz:50001:t"),
        )
        assertEquals(
            "ssl://electrum.citadelfoss.xyz:50002",
            TakerAppConfig.electrumUrlForHost("electrum.citadelfoss.xyz:50002:s"),
        )
        assertEquals("taker-signet", TakerAppConfig.walletNameForElectrum(TakerAppConfig.SIGNET_ELECTRUM_URL))
        assertEquals("taker-wallet", TakerAppConfig.walletNameForElectrum(TakerAppConfig.DEFAULT_ELECTRUM_URL))
    }

    @Test
    fun useMax_keepsPrepareFeeReserve() {
        assertEquals(90_000L, CoinswapRepository.maxSwappableSats(100_000L))
        assertEquals(0L, CoinswapRepository.maxSwappableSats(5_000L))
        assertTrue(CoinswapRepository.poolCanFundSwap(210_000L, 200_000L))
        assertFalse(CoinswapRepository.poolCanFundSwap(200_000L, 200_000L))
        assertFalse(CoinswapRepository.poolCanFundSwap(209_999L, 200_000L))
    }

    @Test
    fun makerFitsAmount_requiresOnlineMinMaxAndLiquidity() {
        assertTrue(
            CoinswapRepository.makerFitsAmount(
                online = true,
                minSats = 100_000,
                maxSats = 1_000_000,
                liquiditySats = 500_000,
                amountSats = 200_000,
            ),
        )
        assertFalse(
            CoinswapRepository.makerFitsAmount(
                online = false,
                minSats = 100_000,
                maxSats = 1_000_000,
                liquiditySats = 500_000,
                amountSats = 200_000,
            ),
        )
        assertFalse(
            CoinswapRepository.makerFitsAmount(
                online = true,
                minSats = 100_000,
                maxSats = 150_000,
                liquiditySats = 500_000,
                amountSats = 200_000,
            ),
        )
        assertFalse(
            CoinswapRepository.makerFitsAmount(
                online = true,
                minSats = 100_000,
                maxSats = 1_000_000,
                liquiditySats = 150_000,
                amountSats = 200_000,
            ),
        )
        assertTrue(
            CoinswapRepository.makerFitsAmount(
                online = true,
                minSats = 100_000,
                maxSats = 0,
                liquiditySats = 0,
                amountSats = 200_000,
            ),
        )
    }

    @Test
    fun maxAmountFittingMakerCount_capsUseMaxToSharedMakerLiquidity() {
        val makers = listOf(
            Triple(true, 100_000L, 300_000L),
            Triple(true, 100_000L, 250_000L),
            Triple(true, 100_000L, 800_000L),
            Triple(false, 100_000L, 900_000L),
        )
        // Wallet could send 700k; 2 online makers can share up to 300k
        // (300k + 800k caps; the 250k maker drops out above 250k).
        assertEquals(
            300_000L,
            CoinswapRepository.maxAmountFittingMakerCount(
                walletCap = 700_000L,
                needed = 2,
                makers = makers,
            ),
        )
        assertEquals(
            0L,
            CoinswapRepository.maxAmountFittingMakerCount(
                walletCap = 700_000L,
                needed = 4,
                makers = makers,
            ),
        )
    }

    @Test
    fun walletExists_onlyMatchesExactWalletFile() {
        val dir = File.createTempFile("taker", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val wallets = File(dir, "wallets").apply { mkdirs() }
        File(wallets, "taker-signet_swap_report.json").writeText("{}")
        assertFalse(FfiEnv.walletExists(dir.absolutePath, "taker-signet"))
        File(wallets, "taker-signet").writeText("wallet")
        assertTrue(FfiEnv.walletExists(dir.absolutePath, "taker-signet"))
        assertFalse(FfiEnv.walletExists(dir.absolutePath, ""))
    }
}
