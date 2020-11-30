package fr.acinq.phoenix.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import fr.acinq.eclair.utils.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@ExperimentalCoroutinesApi
actual class NetworkMonitor actual constructor(loggerFactory: LoggerFactory, val ctx: PlatformContext) : CoroutineScope by MainScope() {

    val logger = newLogger(loggerFactory)
    private val connectivityChannel = ConflatedBroadcastChannel<Connection>()
    actual fun openNetworkStateSubscription(): ReceiveChannel<Connection> = connectivityChannel.openSubscription()

    actual fun start() {
        val connectivityManager = ctx.application.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        connectivityManager?.registerNetworkCallback(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                launch { connectivityChannel.send(Connection.ESTABLISHED) }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                launch { connectivityChannel.send(Connection.CLOSED) }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                launch { connectivityChannel.send(Connection.CLOSED) }
            }

        })
        logger.error { "Not yet implemented!" }
    }

    actual fun stop() {
        logger.error { "Not yet implemented!" }
    }
}
