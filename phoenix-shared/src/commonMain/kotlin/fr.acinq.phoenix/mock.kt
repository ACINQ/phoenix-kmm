package fr.acinq.phoenix

import fr.acinq.eclair.Eclair
import fr.acinq.eclair.Eclair.randomBytes32
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.db.HopDesc
import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.db.WalletPayment
import fr.acinq.eclair.payment.FinalFailure
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.UUID
import fr.acinq.eclair.utils.currentTimestampMillis
import fr.acinq.eclair.utils.msat

object Mock {
    fun incomingPaymentReceived(): WalletPayment {
        val now = currentTimestampMillis()
        return IncomingPayment(
            preimage = randomBytes32(),
            origin = IncomingPayment.Origin.Invoice(PaymentRequest.read("lntb15u1psqtnwzpp57u4wnvjx6z94j39gvpkgc9x6trmwz7sq90pu28f9fq8svcr4tpjqdq4xysyymr0vd4kzcmrd9hx7cqp7xqrrss9qy9qsqsp5s8ky7hr7rzl3gk2vvpgcv6y9mdyqt69gmuu6fgqnjtj3ygjkc6gsqxnvjtrjg9g22kxrfje4cu2xkk2vvyzmj476ke6767z4z8aymunna369angdf2mfrukn6m697nr2mga2ytlewmws3mstez7865k2w2qqullvf2")),
            createdAt = now - 1000 * 60,
            received = IncomingPayment.Received(
                amount = 678_000.msat,
                receivedWith = IncomingPayment.ReceivedWith.LightningPayment,
                receivedAt = now
            )
        )
    }

    private fun parts(amount: MilliSatoshi, status: OutgoingPayment.Part.Status): List<OutgoingPayment.Part> {
        val (a, b) = listOf(Eclair.randomKey().publicKey(), Eclair.randomKey().publicKey())
        return listOf(OutgoingPayment.Part(
            id = UUID.randomUUID(),
            amount = amount,
            route = listOf(HopDesc(a,b)),
            status = status
        ))
    }

    fun outgoingPending(): WalletPayment {
        return OutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 200_000.msat,
            recipient = Eclair.randomKey().publicKey(),
            details = OutgoingPayment.Details.Normal(
                paymentRequest = PaymentRequest.read("lntb19u1psqtnuspp5cmck9rzrt00wpggydwahplql258txejwlwjvn520txy84chq5ttqdp8xys9xcmpd3sjqsmgd9czq3njv9c8qatrvd5kumccqp7xqrrss9qy9qsqsp55psaxqvh3ayk7atgpneck8fxqfdg848vu5fkp5adp3359cnlu4aq73y3w0t2fcv9vexvq9lkj6gdkwqzk4agqwuzh9dkzczqqgva3fr8s8q6wcwucvcz3x9ycr5pllnhgxprdh4j0706ncvl48kq8uqh6egpdeekrz")
            ),
            parts = parts(200_000.msat, OutgoingPayment.Part.Status.Pending),
            status = OutgoingPayment.Status.Pending
        )
    }

    fun outgoingSuccessful(): WalletPayment {
        val now = currentTimestampMillis()
        return OutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 200_000.msat,
            recipient = Eclair.randomKey().publicKey(),
            details = OutgoingPayment.Details.Normal(
                paymentRequest = PaymentRequest.read("lntb19u1psqtnuspp5cmck9rzrt00wpggydwahplql258txejwlwjvn520txy84chq5ttqdp8xys9xcmpd3sjqsmgd9czq3njv9c8qatrvd5kumccqp7xqrrss9qy9qsqsp55psaxqvh3ayk7atgpneck8fxqfdg848vu5fkp5adp3359cnlu4aq73y3w0t2fcv9vexvq9lkj6gdkwqzk4agqwuzh9dkzczqqgva3fr8s8q6wcwucvcz3x9ycr5pllnhgxprdh4j0706ncvl48kq8uqh6egpdeekrz")
            ),
            parts = parts(200_000.msat, OutgoingPayment.Part.Status.Pending),
            status = OutgoingPayment.Status.Succeeded(
                preimage = randomBytes32()
            )
        )
    }

    fun outgoingFailed(): WalletPayment {
        val now = currentTimestampMillis()
        return OutgoingPayment(
            id = UUID.randomUUID(),
            recipientAmount = 200_000.msat,
            recipient = Eclair.randomKey().publicKey(),
            details = OutgoingPayment.Details.Normal(
                paymentRequest = PaymentRequest.read("lntb19u1psqtnuspp5cmck9rzrt00wpggydwahplql258txejwlwjvn520txy84chq5ttqdp8xys9xcmpd3sjqsmgd9czq3njv9c8qatrvd5kumccqp7xqrrss9qy9qsqsp55psaxqvh3ayk7atgpneck8fxqfdg848vu5fkp5adp3359cnlu4aq73y3w0t2fcv9vexvq9lkj6gdkwqzk4agqwuzh9dkzczqqgva3fr8s8q6wcwucvcz3x9ycr5pllnhgxprdh4j0706ncvl48kq8uqh6egpdeekrz")
            ),
            parts = parts(200_000.msat, OutgoingPayment.Part.Status.Failed(
                remoteFailureCode = null, details = "mocked payment part failure message"
            )),
            status = OutgoingPayment.Status.Failed(
                reason = FinalFailure.InvalidPaymentAmount
            )
        )
    }
}

fun WalletPayment.desc(): String = "placeholder"
fun WalletPayment.amountMsat(): Long = 12345
fun WalletPayment.id(): String = "placeholder-id"
fun WalletPayment.status(): String = "status"
fun WalletPayment.timestamp(): Long = 0

