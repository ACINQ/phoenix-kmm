package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.bitcoin.ByteVector
import fr.acinq.eclair.channel.CMD_CLOSE
import fr.acinq.eclair.channel.ChannelEvent
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.WrappedChannelEvent
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
        when (intent) {
            is CloseChannelsConfiguration.Intent.CloseAllChannels -> {
                val scriptPubKey = util.addressToPublicKeyScript(address = intent.address)
                if (scriptPubKey == null) {
                    throw IllegalArgumentException(
                        "Address is invalid. Caller MUST validate user input via parseBitcoinAddress"
                    )
                }
                launch {
                    val channelIds = peer.channels.keys
                    channelIds.forEach { channelId ->
                        val command = CMD_CLOSE(scriptPubKey = ByteVector(scriptPubKey))
                        val channelEvent = ChannelEvent.ExecuteCommand(command)
                        val peerEvent = WrappedChannelEvent(channelId, channelEvent)
                        peer.send(peerEvent)
                    }
                    model(CloseChannelsConfiguration.Model.ChannelsClosed)
                }
            }
        }
    }
}
