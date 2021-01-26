package fr.acinq.phoenix.tor

import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.tests.utils.runSuspendBlocking
import fr.acinq.phoenix.utils.PlatformContext
import io.ktor.util.*
import kotlinx.coroutines.delay
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.simplePrintFrontend
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


class TorLocalTest {
    @InternalAPI
    @OptIn(ExperimentalTime::class)
    @Test
    fun run() = runSuspendBlocking {
        val tor = Tor(PlatformContext(), LoggerFactory(simplePrintFrontend))

        tor.start(this)

        delay(2.seconds)

        val socket = TcpSocket.Builder().connect(tor.proxy, "neverssl.com", 80)
        socket.send("GET / HTTP/1.0\nhost: neverssl.com\n\n".encodeToByteArray())

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
