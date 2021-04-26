package fr.acinq.phoenix.app

import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.WalletParams
import fr.acinq.lightning.blockchain.electrum.ElectrumWatcher
import fr.acinq.lightning.db.Databases
import fr.acinq.lightning.io.Peer
import fr.acinq.lightning.io.TcpSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

@OptIn(ExperimentalCoroutinesApi::class)
class PeerManager(
    loggerFactory: LoggerFactory,
    private val nodeParamsManager: NodeParamsManager,
    private val configurationManager: AppConfigurationManager,
    private val tcpSocketBuilder: TcpSocket.Builder,
    private val electrumWatcher: ElectrumWatcher,
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)
    private val _peer = MutableStateFlow<Peer?>(null)
    public val peerState: StateFlow<Peer?> = _peer

    init {
        launch {
            _peer.value = Peer(
                nodeParams = nodeParamsManager.nodeParams.filterNotNull().first(),
                walletParams = configurationManager.chainContext.filterNotNull().first().walletParams(),
                watcher = electrumWatcher,
                db = nodeParamsManager.databases.filterNotNull().first(),
                socketBuilder = tcpSocketBuilder,
                scope = MainScope()
            )
        }
    }

    suspend fun getPeer() = peerState.filterNotNull().first()
}