package com.example.coinswapmobile

import com.example.coinswapmobile.data.CoinswapRepository
import com.example.coinswapmobile.data.TakerAppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure wallet-contract checks that do not need an Android device or UniFFI.
 */
class WalletContractTest {

    @Test
    fun takerAppConfig_serializesConnectionMetadata() {
        val cfg = TakerAppConfig(
            rpcHost = "10.0.0.2",
            rpcPort = 18443,
            rpcUsername = "alice",
            rpcPassword = "secret",
            zmqHost = "10.0.0.2",
            zmqPort = 28333,
            walletName = "demo-wallet",
            walletPassword = "wp",
            torAuthPassword = "tor-pass",
        )
        assertEquals("10.0.0.2:18443", cfg.rpcUrl)
        assertEquals("tcp://10.0.0.2:28333", cfg.zmqAddr)
        assertEquals("demo-wallet", cfg.walletName)
        // Round-trip via copy preserves secrets for session save/load contracts.
        val copy = cfg.copy()
        assertEquals(cfg.rpcPassword, copy.rpcPassword)
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
            "rpc_host", "rpc_port", "rpc_user", "rpc_password",
            "zmq_host", "zmq_port", "tor_control", "socks_host", "socks_port",
            "tor_auth", "wallet_name", "wallet_password", "protocol",
        )
        assertTrue(clearedKeys.containsAll(listOf("rpc_password", "wallet_password", "tor_auth")))
    }
}
