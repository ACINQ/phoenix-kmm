package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.eclair.io.Peer
import fr.acinq.phoenix.app.Utilities
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.CloseChannelsConfiguration
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppCloseChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peer: Peer,
    private val util: Utilities
) : AppController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>(
    loggerFactory,
    CloseChannelsConfiguration.Model.Loading
) {
    init {
        launch {
            peer.channelsFlow.collect { channels ->
                val sats = channels.values.sumOf { it.localCommitmentSpec?.toLocal?.truncateToSatoshi()?.toLong() ?: 0 }
                model(CloseChannelsConfiguration.Model.Ready(
                    channelCount = channels.size,
                    sats = sats
                ))
            }
        }
    }

    override fun process(intent: CloseChannelsConfiguration.Intent) {
        TODO("Not yet implemented")
    }
}
