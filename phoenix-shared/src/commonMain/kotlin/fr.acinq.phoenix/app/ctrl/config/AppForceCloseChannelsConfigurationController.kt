package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.io.WrappedChannelEvent
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.ForceCloseChannelsConfiguration
import fr.acinq.phoenix.ctrl.config.ForceCloseChannelsConfiguration.Model.ChannelInfoStatus
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppForceCloseChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val walletManager: WalletManager,
    private val masterPubkeyPath: String,
    private val chain: Chain
) : AppController<ForceCloseChannelsConfiguration.Model, ForceCloseChannelsConfiguration.Intent>(
    loggerFactory,
    ForceCloseChannelsConfiguration.Model.Loading
) {
    // When the view first appears, the channelList may already contain channels that are in
    // a closing state. We want to avoid including them when broadcasting Model.ChannelsClosed.
    // So we track the channelIds that we've issued close commands for.
    var closingChannelIds: Set<ByteVector32>? = null

    fun channelInfoStatus(channel: ChannelState): ChannelInfoStatus? = when (channel) {
        is Normal -> ChannelInfoStatus.Normal
        is Offline -> ChannelInfoStatus.Offline
        is Syncing -> ChannelInfoStatus.Syncing
        is Closing -> ChannelInfoStatus.Closing
        is Closed -> ChannelInfoStatus.Closed
        is Aborted -> ChannelInfoStatus.Aborted
        else -> null
    }

    fun isForceClosable(channelInfoStatus: ChannelInfoStatus): Boolean = when (channelInfoStatus) {
        ChannelInfoStatus.Normal -> true
        ChannelInfoStatus.Offline -> true
        ChannelInfoStatus.Syncing -> true
        else -> false
    }

    fun isForceClosable(channel: ChannelState): Boolean = channelInfoStatus(channel)?.let {
        isForceClosable(it)
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
                        ForceCloseChannelsConfiguration.Model.ChannelInfo(
                            id = it.key,
                            balance = sats(it.value),
                            status = mappedStatus
                        )
                    }
                }

                if (closingChannelIds != null) {
                    model(ForceCloseChannelsConfiguration.Model.ChannelsClosed(updatedChannelsList))
                } else {
                    val forceClosableChannelsList = updatedChannelsList.filter {
                        isForceClosable(it.status)
                    }

                    val wallet = walletManager.wallet.value!!
                    val address = wallet.onchainAddress(
                        path = masterPubkeyPath,
                        isMainnet = chain == Chain.MAINNET
                    )

                    model(ForceCloseChannelsConfiguration.Model.Ready(forceClosableChannelsList, address))
                }
            }
        }
    }

    fun sats(channel: ChannelState): Long {
        return channel.localCommitmentSpec?.toLocal?.truncateToSatoshi()?.toLong() ?: 0
    }

    override fun process(intent: ForceCloseChannelsConfiguration.Intent) {
        launch {
            val peer = peerManager.getPeer()
            val filteredChannels = peer.channels.filter {
                isForceClosable(it.value)
            }

            closingChannelIds = closingChannelIds?.let {
                it.plus(filteredChannels.keys)
            } ?: filteredChannels.keys

            filteredChannels.keys.forEach { channelId ->
                val channelEvent = ChannelEvent.ExecuteCommand(CMD_FORCECLOSE)
                val peerEvent = WrappedChannelEvent(channelId, channelEvent)
                peer.send(peerEvent)
            }
        }
    }
}