package fr.acinq.phoenix.utils

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.app.PeerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

data class Connections(
    val internet: Connection = Connection.CLOSED,
    val peer: Connection = Connection.CLOSED,
    val electrum: Connection = Connection.CLOSED
) {
    val global : Connection
        get() = internet + peer + electrum
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsMonitor(peerManager: PeerManager, electrumClient: ElectrumClient, networkMonitor: NetworkMonitor): CoroutineScope {

    private val job = Job()
    override val coroutineContext = MainScope().coroutineContext + job

    private val _connections = MutableStateFlow<Connections>(Connections())
    public val connections: StateFlow<Connections> = _connections

    init {
        launch {
            combine(
                peerManager.peer().connectionState,
                electrumClient.connectionState,
                networkMonitor.networkState
            ) { peerState, electrumState, internetState ->
                Connections(
                    peer = peerState,
                    electrum = electrumState,
                    internet = internetState
                )
            }.collect {
                _connections.value = it
            }
        }
    }
}