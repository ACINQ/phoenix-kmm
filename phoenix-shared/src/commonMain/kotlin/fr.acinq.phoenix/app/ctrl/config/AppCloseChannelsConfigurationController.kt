package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.io.WrappedChannelEvent
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.app.Utilities
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.CloseChannelsConfiguration
import fr.acinq.phoenix.ctrl.config.CloseChannelsConfiguration.Model.ChannelInfoStatus
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppCloseChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val util: Utilities
) : AppController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>(
    loggerFactory,
    CloseChannelsConfiguration.Model.Loading
) {
    var closingChannelIds: Set<ByteVector32>? = null

    fun channelInfoStatus(channel: ChannelState): ChannelInfoStatus? = when (channel) {
        is Normal -> ChannelInfoStatus.Normal
        is Closing -> ChannelInfoStatus.Closing
        is Closed -> ChannelInfoStatus.Closed
        is Aborted -> ChannelInfoStatus.Aborted
        else -> null
    }

    fun isMutualClosable(channelInfoStatus: ChannelInfoStatus): Boolean = when (channelInfoStatus) {
        ChannelInfoStatus.Normal -> true
        else -> false
    }

    fun isMutualClosable(channel: ChannelState): Boolean = channelInfoStatus(channel)?.let {
        isMutualClosable(it)
    } ?: false

    init {
        launch {
            peerManager.getPeer().channelsFlow.collect { channels ->

                val updatedChannelsList = channels.filter {
                    closingChannelIds?.let { set ->
                        set.contains(it.key)
                    } ?: true
                }.mapNotNull {
                    channelInfoStatus(it.value)?.let { mappedStatus ->
                        CloseChannelsConfiguration.Model.ChannelInfo(
                            id = it.key,
                            balance = sats(it.value),
                            status = mappedStatus
                        )
                    }
                }

                if (closingChannelIds != null) {
                    model(CloseChannelsConfiguration.Model.ChannelsClosed(updatedChannelsList))
                } else {
                    val mutualClosableChannelsList = updatedChannelsList.filter {
                        isMutualClosable(it.status)
                    }
                    model(CloseChannelsConfiguration.Model.Ready(mutualClosableChannelsList))
                }
            }
        }
    }

    fun sats(channel: ChannelState): Long {
        return channel.localCommitmentSpec?.toLocal?.truncateToSatoshi()?.toLong() ?: 0
    }

    override fun process(intent: CloseChannelsConfiguration.Intent) {
        var scriptPubKey : ByteArray? = null
        if (intent is CloseChannelsConfiguration.Intent.MutualCloseAllChannels) {
            scriptPubKey = util.addressToPublicKeyScript(address = intent.address)
        }
        if (scriptPubKey == null) {
            throw IllegalArgumentException(
                "Address is invalid. Caller MUST validate user input via parseBitcoinAddress"
            )
        }

        launch {
            val peer = peerManager.getPeer()
            val filteredChannels = peer.channels.filter {
                isMutualClosable(it.value)
            }

            closingChannelIds = closingChannelIds?.let {
                it.plus(filteredChannels.keys)
            } ?: filteredChannels.keys

            filteredChannels.keys.forEach { channelId ->
                val command = CMD_CLOSE(scriptPubKey = ByteVector(scriptPubKey))
                val channelEvent = ChannelEvent.ExecuteCommand(command)
                val peerEvent = WrappedChannelEvent(channelId, channelEvent)
                peer.send(peerEvent)
            }
        }
    }
}
