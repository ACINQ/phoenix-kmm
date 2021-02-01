package fr.acinq.phoenix.app

import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.getApplicationCacheDirectoryPath
import fr.acinq.phoenix.utils.torLog
import fr.acinq.tor.Tor
import fr.acinq.tor.TorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(ExperimentalCoroutinesApi::class)
class TorManager(loggerFactory: LoggerFactory, ctx: PlatformContext) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)
    public val tor: Tor = Tor(getApplicationCacheDirectoryPath(ctx), torLog(loggerFactory))

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

    init {
        launch {
            isTorEnabled.collect { isTorEnabled ->
                when(isTorEnabled) {
                    true -> try {
                        tor.start(this)
                    } catch (ex: Throwable) {
                        logger.error(ex) { "Tor connection cannot be started." }
                    }
                    false -> tor.stop()
                }
            }
        }
    }
}