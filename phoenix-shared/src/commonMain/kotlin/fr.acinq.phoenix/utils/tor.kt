package fr.acinq.phoenix.utils

import fr.acinq.eclair.utils.Connection
import fr.acinq.tor.TorState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun StateFlow<TorState>.connectionState() = flow<Connection> {
    collect { torState ->
        when (torState) {
            TorState.STARTING -> Connection.ESTABLISHING
            TorState.RUNNING -> Connection.ESTABLISHED
            TorState.STOPPED -> Connection.CLOSED
        }
    }
}.stateIn(MainScope())
