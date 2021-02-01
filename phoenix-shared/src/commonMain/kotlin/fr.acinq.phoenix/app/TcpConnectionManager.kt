package fr.acinq.phoenix.app

import fr.acinq.eclair.io.TcpSocket
import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getApplicationCacheDirectoryPath
import fr.acinq.phoenix.utils.torLog
import fr.acinq.phoenix.utils.torProxy
import fr.acinq.tor.Tor
import fr.acinq.tor.TorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(ExperimentalCoroutinesApi::class)
class TcpConnectionManager(
    private val tcpSocketBuilder: TcpSocket.Builder,
    loggerFactory: LoggerFactory, ctx: PlatformContext
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    private val _socketBuilder = MutableStateFlow<TcpSocket.Builder?>(null)
    val socketBuilder: StateFlow<TcpSocket.Builder?> = _socketBuilder

    private val tor by lazy { Tor(getApplicationCacheDirectoryPath(ctx), torLog(loggerFactory)) }

    private val isNetworkAvailable = MutableStateFlow(false)
    private val _isTorEnabled = MutableStateFlow(false)
    public val isTorEnabled = _isTorEnabled

    public val torState = combine(_isTorEnabled, tor.state) { isTorEnabled, state ->
        when (isTorEnabled) {
            true -> when (state) {
                TorState.STARTING -> Connection.ESTABLISHING
                TorState.RUNNING -> Connection.ESTABLISHED
                TorState.STOPPED -> Connection.CLOSED
            }
            false -> null
        }
    }

    fun updateTorUsage(enabled: Boolean): Unit {
        isTorEnabled.value = enabled
    }
    fun updateNetworkAvailability(available: Boolean): Unit {
        isNetworkAvailable.value = available
    }

    init {
        launch {
            combine(isNetworkAvailable, isTorEnabled) { isNetworkAvailable: Boolean, isTorEnabled: Boolean ->
                isNetworkAvailable && isTorEnabled
            }.collect { connectThroughTor ->
                when(connectThroughTor) {
                    true -> try {
                        tor.start(this)
                    } catch (ex: Throwable) {
                        logger.error(ex) { "Tor connection cannot be started." }
                    }
                    false -> if (tor.state.value != TorState.STOPPED) tor.stop()
                }
            }
        }
        launch {
            torState.collect {
                val newSocketBuilder = when(it){
                    Connection.ESTABLISHING, Connection.CLOSED -> null
                    Connection.ESTABLISHED -> tcpSocketBuilder.torProxy(loggerFactory)
                    null -> tcpSocketBuilder
                }

                if (_socketBuilder.value != newSocketBuilder) _socketBuilder.value = newSocketBuilder
            }
        }
    }
}