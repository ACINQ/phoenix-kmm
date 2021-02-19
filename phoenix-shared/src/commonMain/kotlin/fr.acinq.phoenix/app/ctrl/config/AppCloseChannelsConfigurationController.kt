package fr.acinq.phoenix.app.ctrl.config

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.eclair.channel.*
import fr.acinq.eclair.io.WrappedChannelEvent
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.app.Utilities
import fr.acinq.phoenix.app.WalletManager
import fr.acinq.phoenix.app.ctrl.AppController
import fr.acinq.phoenix.ctrl.config.CloseChannelsConfiguration
import fr.acinq.phoenix.data.Chain
import fr.acinq.phoenix.data.Wallet
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppCloseChannelsConfigurationController(
    loggerFactory: LoggerFactory,
    private val peerManager: PeerManager,
    private val walletManager: WalletManager,
    private val masterPubkeyPath: String,
    private val chain: Chain,
    private val util: Utilities
) : AppController<CloseChannelsConfiguration.Model, CloseChannelsConfiguration.Intent>(
    loggerFactory,
    CloseChannelsConfiguration.Model.Loading
) {
    var closingChannelIds: Set<ByteVector32>? = null

    init {
        launch {
            peerManager.getPeer().channelsFlow.collect { channels ->
                val closingChannelIds = closingChannelIds
                if (closingChannelIds != null) {
                    // UI has requested that we close certain channels.
                    // We want to send Model.ChannelsClose,
                    // with a list that contains only those channels specified in closingChannelIds.
                    val updatedChannelsList = channels.filter {
                        closingChannelIds.contains(it.key)
                    }.mapNotNull {
                        val status = when (it.value) {
                            is Normal -> CloseChannelsConfiguration.Model.ChannelInfoStatus.Normal
                            is Closing -> CloseChannelsConfiguration.Model.ChannelInfoStatus.Closing
                            is Closed -> CloseChannelsConfiguration.Model.ChannelInfoStatus.Closed
                            is Aborted -> CloseChannelsConfiguration.Model.ChannelInfoStatus.Aborted
                            else -> null
                        }
                        status?.let { mappedStatus ->
                            CloseChannelsConfiguration.Model.ChannelInfo(
                                id = it.key,
                                sats = sats(it.value),
                                status = mappedStatus
                            )
                        }
                    }
                    model(CloseChannelsConfiguration.Model.ChannelsClosed(updatedChannelsList))
                } else {
                    // UI is waiting for list of channels (user hasn't requested close yet).
                    // We want to send Model.Ready,
                    // with a list that contains only channels in Normal state.
                    val normalChannelsList = channels.mapNotNull {
                        when (it.value) {
                            is Normal -> {
                                CloseChannelsConfiguration.Model.ChannelInfo(
                                    id = it.key,
                                    sats = sats(it.value),
                                    status = CloseChannelsConfiguration.Model.ChannelInfoStatus.Normal
                                )
                            }
                            else -> null
                        }
                    }

                    val wallet = walletManager.wallet.value!!
                    val address = wallet.onchainAddress(
                        path = masterPubkeyPath,
                        isMainnet = chain == Chain.MAINNET
                    )

                    model(CloseChannelsConfiguration.Model.Ready(normalChannelsList, address))
                }
            }
        }
    }

    fun sats(channel: ChannelState): Long {
        return channel.localCommitmentSpec?.toLocal?.truncateToSatoshi()?.toLong() ?: 0
    }

    override fun process(intent: CloseChannelsConfiguration.Intent) {
        var scriptPubKey: ByteArray? = null
        if (intent is CloseChannelsConfiguration.Intent.CloseChannels) {
            scriptPubKey = util.addressToPublicKeyScript(address = intent.address)
            if (scriptPubKey == null) {
                throw IllegalArgumentException(
                    "Address is invalid. Caller MUST validate user input via parseBitcoinAddress"
                )
            }
        }
        val channelIds = when (intent) {
            is CloseChannelsConfiguration.Intent.CloseChannels -> intent.channelIds.toSet()
            is CloseChannelsConfiguration.Intent.ForceCloseChannels -> intent.channelIds.toSet()
        }
        launch {
            if (closingChannelIds != null) {
                throw IllegalArgumentException(
                    "Close intent already used. Can only be used once per instance."
                )
            }
            closingChannelIds = channelIds
            val peer = peerManager.getPeer()
            val filteredChannels = peer.channels.filter {
                channelIds.contains(it.key)
            }
            filteredChannels.keys.forEach { channelId ->
                val command: CloseCommand = if (scriptPubKey != null) {
                    CMD_CLOSE(scriptPubKey = ByteVector(scriptPubKey))
                } else {
                    CMD_FORCECLOSE
                }
                val channelEvent = ChannelEvent.ExecuteCommand(command)
                val peerEvent = WrappedChannelEvent(channelId, channelEvent)
                peer.send(peerEvent)
            }
        }
    }
}
