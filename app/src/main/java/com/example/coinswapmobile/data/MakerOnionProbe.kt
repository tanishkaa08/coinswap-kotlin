package com.example.coinswapmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Probe maker .onion reachability through Orbot SOCKS before prepare.
 * Coinswap makers listen on port 21 over Tor.
 */
object MakerOnionProbe {
    private const val MAKER_PORT = 21

    suspend fun reachableOnions(
        onions: List<String>,
        socksHost: String = TakerAppConfig.DEFAULT_SOCKS_HOST,
        socksPort: Int = TakerAppConfig.DEFAULT_SOCKS_PORT,
        timeoutMs: Int = 25_000,
    ): List<String> = coroutineScope {
        onions.map { onion ->
            async(Dispatchers.IO) {
                val key = MakerRoutePrefs.normalize(onion) ?: return@async null
                if (socksConnectOnion(socksHost, socksPort, key, MAKER_PORT, timeoutMs)) key
                else null
            }
        }.awaitAll().filterNotNull()
    }

    private fun socksConnectOnion(
        socksHost: String,
        socksPort: Int,
        onion: String,
        destPort: Int,
        timeoutMs: Int,
    ): Boolean = try {
        Socket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(socksHost, socksPort), timeoutMs)
            val out = DataOutputStream(socket.getOutputStream())
            val inp = DataInputStream(socket.getInputStream())
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val ver = inp.read()
            val method = inp.read()
            if (ver != 0x05 || method != 0x00) return false
            val hostBytes = onion.toByteArray(Charsets.UTF_8)
            if (hostBytes.size !in 1..255) return false
            out.writeByte(0x05)
            out.writeByte(0x01)
            out.writeByte(0x00)
            out.writeByte(0x03)
            out.writeByte(hostBytes.size)
            out.write(hostBytes)
            out.writeShort(destPort)
            out.flush()
            val repVer = inp.read()
            val rep = inp.read()
            val _rsv = inp.read()
            val atyp = inp.read()
            when (atyp) {
                0x01 -> repeat(4) { inp.read() }
                0x03 -> repeat(inp.read().coerceAtLeast(0)) { inp.read() }
                0x04 -> repeat(16) { inp.read() }
                else -> return false
            }
            inp.read()
            inp.read()
            repVer == 0x05 && rep == 0x00
        }
    } catch (_: Exception) {
        false
    }
}
