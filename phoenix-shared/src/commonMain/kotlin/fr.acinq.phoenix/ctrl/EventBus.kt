package fr.acinq.phoenix.ctrl

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

abstract class Event

@OptIn(ExperimentalCoroutinesApi::class)
class EventBus(
    loggerFactory: LoggerFactory,
): CoroutineScope {

	private val job = Job()
    override val coroutineContext = MainScope().coroutineContext + job

	private val logger = newLogger(loggerFactory)

	private val channel = BroadcastChannel<Event>(10)

	suspend fun send(event: Event) {
		channel.send(event)
    }

	fun subscribe(onEvent: (Event) -> Unit): () -> Unit {
        val subscription = launch {
            channel.openSubscription().consumeEach { onEvent(it) }
        }

        return ({ subscription.cancel() })
    }

	fun stop() {
        job.cancel()
    }
}