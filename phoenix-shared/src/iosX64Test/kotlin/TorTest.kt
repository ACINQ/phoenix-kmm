package fr.acinq.phoenix.tor

import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.io.receiveAvailable
import fr.acinq.eclair.io.send
import fr.acinq.eclair.tests.utils.runSuspendBlocking
import fr.acinq.phoenix.utils.*
import fr.acinq.tor.Tor
import fr.acinq.tor.socks.socks5Handshake
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.simplePrintFrontend
import kotlin.test.Test
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class TorLocalTest {

    @Test
    fun clearText() = runSuspendBlocking {
        val loggerFactory = LoggerFactory(simplePrintFrontend)

        val tor = Tor(getApplicationCacheDirectoryPath(PlatformContext()), torLog(loggerFactory))
        tor.start(this)

        val socketBuilder = TcpSocket.Builder().torProxy(loggerFactory)
        val socket = socketBuilder.connect("neverssl.com", 80)
        socket.send("GET / HTTP/1.0\nhost: neverssl.com\n\n".encodeToByteArray(), flush = true)

        val buffer = ByteArray(64 * 1024)
        try {
            while (true) {
                val read = socket.receiveAvailable(buffer)
                print(buffer.decodeToString(0, read))
            }
        } catch (_: TcpSocket.IOException) {}

        tor.stop()
    }

    @Test
    fun withSsl() = runSuspendBlocking {
        val loggerFactory = LoggerFactory(simplePrintFrontend)

        val tor = Tor(getApplicationCacheDirectoryPath(PlatformContext()), torLog(loggerFactory))
        tor.start(this)

        val socketBuilder = TcpSocket.Builder().torProxy(loggerFactory)
        val socket = socketBuilder.connect("www.google.com", 443, TcpSocket.TLS.SAFE)
        socket.send("GET / HTTP/1.0\nhost: www.google.com\n\n".encodeToByteArray())

        val buffer = ByteArray(64 * 1024)
        try {
            while (true) {
                val read = socket.receiveAvailable(buffer)
                print(buffer.decodeToString(0, read))
            }
        } catch (_: TcpSocket.IOException) {}

        tor.stop()
    }
}
