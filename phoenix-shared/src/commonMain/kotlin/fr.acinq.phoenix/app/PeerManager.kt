package fr.acinq.phoenix.app

import fr.acinq.eclair.*
import fr.acinq.eclair.blockchain.electrum.ElectrumWatcher
import fr.acinq.eclair.db.ChannelsDb
import fr.acinq.eclair.db.Databases
import fr.acinq.eclair.db.PaymentsDb
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.TcpSocket
import fr.acinq.phoenix.data.Chain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(ExperimentalCoroutinesApi::class)
class PeerManager(
    loggerFactory: LoggerFactory,
    private val channelsDb: ChannelsDb,
    private val paymentsDb: PaymentsDb,
    private val walletManager: WalletManager,
    private val walletParamsManager: WalletParamsManager,
    private val tcpSocketBuilder: TcpSocket.Builder,
    private val electrumWatcher: ElectrumWatcher,
    private val chain: Chain
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)
    private val _peer = MutableStateFlow<Peer?>(null)
    public val peerState: StateFlow<Peer?> = _peer

    init {
        launch {
            val nodeParams = walletManager.generateNodeParams()
            val walletParams = walletParamsManager.walletParams.filterNotNull().first()
            _peer.value = buildPeer(nodeParams, walletParams)
        }
    }

    suspend fun getPeer() = peerState.filterNotNull().first()

    private fun buildPeer(nodeParams: NodeParams, walletParams: WalletParams): Peer {
        logger.info { "nodeid=${nodeParams.nodeId}" }
        logger.info { "nodeParams=$nodeParams" }
        logger.info { "walletParams=$walletParams" }

        val databases = object : Databases {
            override val channels: ChannelsDb get() = channelsDb
            override val payments: PaymentsDb get() = paymentsDb
        }

        return Peer(tcpSocketBuilder, nodeParams, walletParams, electrumWatcher, databases, MainScope())
    }
}