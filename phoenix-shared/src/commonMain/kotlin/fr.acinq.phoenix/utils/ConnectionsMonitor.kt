package fr.acinq.phoenix.utils

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.utils.Connection
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.app.TcpConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

data class Connections(
    val internet: Connection = Connection.CLOSED,
    val tor: Connection? = null,
    val peer: Connection = Connection.CLOSED,
    val electrum: Connection = Connection.CLOSED
) {
    val global : Connection
        get() = internet + tor + peer + electrum
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsMonitor(
    peerManager: PeerManager,
    electrumClient: ElectrumClient,
    networkMonitor: NetworkMonitor,
    tcpConnectionManager: TcpConnectionManager,
): CoroutineScope {

    private val job = Job()
    override val coroutineContext = MainScope().coroutineContext + job

    private val _connections = MutableStateFlow(Connections())
    public val connections: StateFlow<Connections> = _connections

    init {
        launch {
            combine(
                peerManager.getPeer().connectionState,
                electrumClient.connectionState,
                networkMonitor.networkState,
                tcpConnectionManager.torState,
            ) { peerState, electrumState, internetState, torState ->
                Connections(
                    peer = peerState,
                    electrum = electrumState,
                    internet = when (internetState) {
                        NetworkState.Available -> Connection.ESTABLISHED
                        NetworkState.NotAvailable -> Connection.CLOSED
                    },
                    tor = torState
                )
            }.collect {
                _connections.value = it
            }
        }
    }
}