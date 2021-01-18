package fr.acinq.phoenix.app.ctrl

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eclair.Feature
import fr.acinq.eclair.Features
import fr.acinq.eclair.channel.ChannelState
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.SendPayment
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.UUID
import fr.acinq.phoenix.app.PeerManager
import fr.acinq.phoenix.ctrl.Scan
import fr.acinq.phoenix.data.toMilliSatoshi
import fr.acinq.phoenix.utils.localCommitmentSpec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.kodein.log.LoggerFactory

class AppScanController(loggerFactory: LoggerFactory, private val peerManager: PeerManager) : AppController<Scan.Model, Scan.Intent>(loggerFactory, Scan.Model.Ready) {

    init {
        launch {
            peerManager.peer().channelsFlow.collect { channels ->
                val balanceMsat = balanceMsat(channels)
                model {
                    if (this is Scan.Model.Validate) {
                        this.copy(balanceMsat = balanceMsat)
                    } else {
                        this
                    }
                }
            }
        }
    }

    private fun balanceMsat(channels: Map<ByteVector32, ChannelState>): Long {
        return channels.values.sumOf { it.localCommitmentSpec?.toLocal?.toLong() ?: 0 }
    }

    override fun process(intent: Scan.Intent) {
        when (intent) {
            is Scan.Intent.Parse -> launch {
                readPaymentRequest(intent.request)?.run {
                    var isDangerous = if (amount != null) {
                        false
                    } else {
                        // amountless invoice -> dangerous unless full trampoline is in effect
                        !Features(features ?: ByteVector.empty).hasFeature(Feature.TrampolinePayment)
                    }
                    if (isDangerous)
                        model(Scan.Model.DangerousRequest(intent.request))
                    else
                        validatePaymentRequest(intent.request, this)
                }
            }
            is Scan.Intent.ConfirmDangerousRequest -> launch {
                readPaymentRequest(intent.request)?.run {
                    validatePaymentRequest(intent.request, this)
                }
            }
            is Scan.Intent.Send -> {
                launch {
                    readPaymentRequest(intent.request)?.let {
                        val paymentAmount = intent.amount.toMilliSatoshi(intent.unit)
                        val paymentId = UUID.randomUUID()

                        peerManager.peer().send(SendPayment(paymentId, paymentAmount, it.nodeId, OutgoingPayment.Details.Normal(it)))

                        model(Scan.Model.Sending)
                    }
                }
            }
        }
    }

    private suspend fun readPaymentRequest(request: String) : PaymentRequest? {
        return try {
            PaymentRequest.read(request.cleanUpInvoice())
        } catch (t: Throwable) { // TODO Throwable is not a good choice, analyze the possible output of PaymentRequest.read(...)
            model(Scan.Model.BadRequest)
            null
        }
    }

    private suspend fun validatePaymentRequest(request: String, paymentRequest: PaymentRequest) {
        val balanceMsat = balanceMsat(peerManager.peer().channels)
        model(
            Scan.Model.Validate(
                request = request,
                amountMsat = paymentRequest.amount?.toLong(),
                requestDescription = paymentRequest.description,
                balanceMsat = balanceMsat
            )
        )
    }

    private fun String.cleanUpInvoice(): String {
        val trimmed = replace("\\u00A0", "").trim()
        return when {
            trimmed.startsWith("lightning://", true) -> trimmed.drop(12)
            trimmed.startsWith("lightning:", true) -> trimmed.drop(10)
            trimmed.startsWith("bitcoin://", true) -> trimmed.drop(10)
            trimmed.startsWith("bitcoin:", true) -> trimmed.drop(8)
            else -> trimmed
        }
    }

}
