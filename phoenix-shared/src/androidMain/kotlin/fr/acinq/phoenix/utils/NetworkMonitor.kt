package fr.acinq.phoenix.utils

import fr.acinq.eclair.utils.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel


actual class NetworkMonitor actual constructor() : CoroutineScope by MainScope() {

    val logger = newLogger()

    actual fun openNetworkStateSubscription(): ReceiveChannel<Connection> {
        logger.error { "Not yet implemented!" }
        return Channel()
    }

    actual fun start() {
        logger.error { "Not yet implemented!" }
    }

    actual fun stop() {
        logger.error { "Not yet implemented!" }
    }
}
