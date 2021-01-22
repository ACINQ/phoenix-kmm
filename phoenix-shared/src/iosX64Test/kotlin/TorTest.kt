package fr.acinq.phoenix.tor

import fr.acinq.eclair.tests.utils.runSuspendBlocking
import fr.acinq.eclair.tests.utils.runSuspendTest
import fr.acinq.phoenix.utils.PlatformContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.kodein.log.LoggerFactory
import org.kodein.log.frontend.simplePrintFrontend
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.hours
import kotlin.time.seconds


class TorLocalTest {
    @OptIn(ExperimentalTime::class)
    @Test fun run() = runSuspendBlocking {
        val tor = Tor(PlatformContext(), LoggerFactory(simplePrintFrontend))

        tor.start()

        delay(5.seconds)

        tor.stop()

        delay(5.seconds)

        tor.start()

        delay(5.seconds)
    }
}
