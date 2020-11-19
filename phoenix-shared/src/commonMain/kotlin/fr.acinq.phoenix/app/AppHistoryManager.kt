package fr.acinq.phoenix.app

import fr.acinq.eclair.io.PaymentNotSent
import fr.acinq.eclair.io.PaymentProgress
import fr.acinq.eclair.io.PaymentReceived
import fr.acinq.eclair.io.PaymentSent
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.utils.UUID
import fr.acinq.eclair.utils.currentTimestampMillis
import fr.acinq.phoenix.data.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.kodein.db.DB
import org.kodein.db.find
import org.kodein.db.on
import org.kodein.db.useModels


@OptIn(ExperimentalCoroutinesApi::class)
class AppHistoryManager(private val appDb: DB, private val peer: Peer) : CoroutineScope by MainScope() {

    private fun getList() = appDb.find<Transaction>().byIndex("timestamp").useModels(reverse = true) { it.toList() }

    private val _transactions = MutableStateFlow(getList())
    fun transactions(): StateFlow<List<Transaction>> = _transactions

    init {
        launch {
            peer.openListenerEventSubscription().consumeEach {
                when (it) {
                    is PaymentReceived -> {
                        appDb.put(
                            Transaction(
                                id = UUID.randomUUID().toString(),
                                amountMsat = it.incomingPayment.paymentRequest.amount?.toLong() ?: error("Received a payment without amount ?!?"),
                                desc = it.incomingPayment.paymentRequest.description ?: "",
                                status = Transaction.Status.Success,
                                timestamp = currentTimestampMillis()
                            )
                        )
                    }
                    is PaymentProgress -> {
                        val totalAmount = it.payment.paymentAmount + it.fees
                        appDb.put(
                            Transaction(
                                id = it.payment.paymentId.toString(),
                                amountMsat = -totalAmount.toLong(), // storing value in MilliSatoshi
                                desc = it.payment.paymentRequest.description ?: "",
                                status = Transaction.Status.Pending,
                                timestamp = currentTimestampMillis()
                            )
                        )
                    }
                    is PaymentSent -> {
                        val totalAmount = it.payment.paymentAmount + it.fees
                        appDb.put(
                            Transaction(
                                id = it.payment.paymentId.toString(),
                                amountMsat = -totalAmount.toLong(), // storing value in MilliSatoshi
                                desc = it.payment.paymentRequest.description ?: "",
                                status = Transaction.Status.Success,
                                timestamp = currentTimestampMillis()
                            )
                        )
                    }
                    is PaymentNotSent -> {
                        appDb.put(
                            Transaction(
                                id = it.payment.paymentId.toString(),
                                amountMsat = -it.payment.paymentAmount.toLong(), // storing value in MilliSatoshi
                                desc = it.reason.message(),
                                status = Transaction.Status.Failure,
                                timestamp = currentTimestampMillis()
                            )
                        )
                    }
                    else -> {}
                }
            }
        }

        fun updateChannel() = launch { _transactions.value = getList() }

        appDb.on<Transaction>().register {
            didPut { updateChannel() }
            didDelete { updateChannel() }
        }
    }
}
