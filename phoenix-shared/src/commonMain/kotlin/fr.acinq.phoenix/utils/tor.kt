package fr.acinq.phoenix.utils

import fr.acinq.eclair.utils.Connection
import fr.acinq.tor.TorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun StateFlow<TorState>.connectionState(scope: CoroutineScope) = flow<Connection> {
    collect { torState ->
        val newState = when (torState) {
            TorState.STARTING -> Connection.ESTABLISHING
            TorState.RUNNING -> Connection.ESTABLISHED
            TorState.STOPPED -> Connection.CLOSED
        }
        emit(newState)
    }
}.stateIn(scope)
