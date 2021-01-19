package fr.acinq.phoenix.app

import fr.acinq.eclair.db.PaymentsDb
import fr.acinq.eclair.db.WalletPayment
import fr.acinq.eclair.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsManager(
    loggerFactory: LoggerFactory,
    private val paymentsDb: PaymentsDb,
    private val peer: Peer
) : CoroutineScope by MainScope() {
    private val logger = newLogger(loggerFactory)

    /** Get a list of wallet payments from database, with hard coded parameters. */
    private suspend fun listPayments(): List<WalletPayment> = paymentsDb.listPayments(150, 0)

    /** Broadcasts an observable relevant list of payments. */
    private val payments = MutableStateFlow(emptyList<WalletPayment>())

    /**
     * Broadcasts the most recent incoming payment since the app was launched.
     *
     * If we haven't received any payments since app launch, the value will be null.
     * Value is refreshed when the peer emits a [PaymentReceived] event.
     *
     * This is currently used for push notification handling on iOS.
     * On iOS, when the app is in the background, and a push notification is received,
     * the app is required to tell the OS when it has finished processing the notification.
     * This channel is used for that purpose: when a payment is received, the app can be suspended again.
     *
     * As a side effect, this allows the app to show a notification when a payment has been received.
     */
    private val lastIncomingPayment = ConflatedBroadcastChannel<WalletPayment?>(null)

    init {
        launch {
            payments.value = listPayments()
            peer.openListenerEventSubscription().consumeEach { event ->
                when (event) {
                    is PaymentReceived, is PaymentSent, is PaymentNotSent, is PaymentProgress -> {
                        logger.debug { "refreshing payment history with event=$event" }
                        if (event is PaymentReceived) {
                            lastIncomingPayment.send(event.incomingPayment)
                        }
                        payments.value = listPayments()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun subscribeToPayments() = payments
    fun subscribeToLastIncomingPayment() = lastIncomingPayment.openSubscription()
}
