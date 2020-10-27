package fr.acinq.phoenix.utils

import fr.acinq.eclair.utils.Connection
import kotlinx.coroutines.channels.ReceiveChannel


actual class NetworkMonitor {
    actual fun openNetworkStateSubscription(): ReceiveChannel<Connection> {
        TODO("Not yet implemented")
    }

    actual fun start() {
        TODO("Not yet implemented")
    }

    actual fun stop() {
        TODO("Not yet implemented")
    }
}