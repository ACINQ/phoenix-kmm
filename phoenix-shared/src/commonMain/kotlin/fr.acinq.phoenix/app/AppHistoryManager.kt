package fr.acinq.phoenix.app

import fr.acinq.eclair.io.PaymentNotSent
import fr.acinq.eclair.io.PaymentProgress
import fr.acinq.eclair.io.PaymentReceived
import fr.acinq.eclair.io.PaymentSent
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.utils.UUID
import fr.acinq.eclair.utils.currentTimestampMillis
import fr.acinq.phoenix.data.Transaction
import fr.acinq.phoenix.utils.TAG_APPLICATION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.kodein.db.DB
import org.kodein.db.find
import org.kodein.db.on
import org.kodein.db.useModels
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance


@OptIn(ExperimentalCoroutinesApi::class)
class AppHistoryManager(override val di: DI) : DIAware, CoroutineScope by MainScope() {

    private val db: DB by instance(tag = TAG_APPLICATION)
    private val peer: Peer by instance()

    private fun getList() = db.find<Transaction>().byIndex("timestamp").useModels(reverse = true) { it.toList() }

    private val transactions = ConflatedBroadcastChannel(getList())

    init {
        launch {
            peer.openListenerEventSubscription().consumeEach {
                when (it) {
                    is PaymentReceived -> {
                        db.put(
                            Transaction(
                                UUID.randomUUID().toString(),
                                it.incomingPayment.paymentRequest.amount?.toLong() ?: error("Received a payment without amount ?!?"),
                                it.incomingPayment.paymentRequest.description ?: "",
                                Transaction.Status.Success,
                                currentTimestampMillis()
                            )
                        )
                    }
                    is PaymentProgress -> {
                        val totalAmount = it.payment.paymentAmount + it.fees
                        db.put(
                            Transaction(
                                id = it.payment.paymentId.toString(),
                                amountSat = -totalAmount.toLong(), // Need to convert from MilliSatoshi to Satoshi ???
                                desc = it.payment.paymentRequest.description ?: "",
                                status = Transaction.Status.Pending,
                                timestamp = currentTimestampMillis()
                            )
                        )
                    }
                    is PaymentSent -> {
                        val totalAmount = it.payment.paymentAmount + it.fees
                        db.put(
                            Transaction(
                                id = it.payment.paymentId.toString(),
                                amountSat = -totalAmount.toLong(), // Need to convert from MilliSatoshi to Satoshi ???
                                desc = it.payment.paymentRequest.description ?: "",
                                status = Transaction.Status.Success,
                                timestamp = currentTimestampMillis()
                            )
                        )
                    }
                    is PaymentNotSent -> {
                        db.put(
                            Transaction(
                                id = it.payment.paymentId.toString(),
                                amountSat = -it.payment.paymentAmount.toLong(), // Need to convert from MilliSatoshi to Satoshi ???
                                desc = it.reason.name,
                                status = Transaction.Status.Failure,
                                timestamp = currentTimestampMillis()
                            )
                        )
                    }
                    else -> {}
                }
            }
        }

        fun updateChannel() = launch { transactions.send(getList()) }

        db.on<Transaction>().register {
            didPut { updateChannel() }
            didDelete { updateChannel() }
        }
    }

    fun openTransactionsSubscriptions() = transactions.openSubscription()
}
