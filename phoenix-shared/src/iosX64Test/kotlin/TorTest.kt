package fr.acinq.phoenix.tor

import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.tests.utils.runSuspendBlocking
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getApplicationCacheDirectoryPath
import fr.acinq.phoenix.utils.torLog
import fr.acinq.phoenix.utils.torProxy
import fr.acinq.tor.Tor
import io.ktor.util.*
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.simplePrintFrontend
import kotlin.test.Test
import kotlin.time.ExperimentalTime


class TorLocalTest {
    @InternalAPI
    @OptIn(ExperimentalTime::class)
    @Test
    fun run() = runSuspendBlocking {
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
}
