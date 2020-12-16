/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.db

import com.squareup.sqldelight.ColumnAdapter
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.ShortChannelId
import fr.acinq.eclair.channel.ChannelException
import fr.acinq.eclair.db.*
import fr.acinq.eclair.payment.FinalFailure
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.utils.Either
import fr.acinq.eclair.utils.UUID
import fr.acinq.eclair.utils.currentTimestampMillis
import fr.acinq.eclair.utils.toByteVector32
import fr.acinq.eclair.wire.FailureMessage
import fracinqphoenixdb.Incoming_payments
import fracinqphoenixdb.Outgoing_payment_parts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.log.platformSimpleName
import org.kodein.memory.text.toHexString

class SqlitePaymentsDb(private val driver: SqlDriver) : PaymentsDb {

    enum class IncomingOriginDbEnum {
        KeySend, Invoice, SwapIn;

        companion object {
            fun toDb(origin: IncomingPayment.Origin) = when (origin) {
                is IncomingPayment.Origin.KeySend -> KeySend
                is IncomingPayment.Origin.Invoice -> Invoice
                is IncomingPayment.Origin.SwapIn -> SwapIn
                else -> throw UnhandledIncomingOrigin(origin)
            }
        }
    }

    enum class IncomingReceivedWithDbEnum {
        LightningPayment, NewChannel;

        companion object {
            fun toDb(value: IncomingPayment.ReceivedWith) = when (value) {
                is IncomingPayment.ReceivedWith.NewChannel -> NewChannel
                is IncomingPayment.ReceivedWith.LightningPayment -> LightningPayment
                else -> throw UnhandledIncomingReceivedWith(value)
            }
        }
    }

    private fun parseIncomingOriginFromDb(origin: IncomingOriginDbEnum, swapAmount: Long?, swapAddress: String?, paymentRequest: String?) = when {
        origin == IncomingOriginDbEnum.KeySend -> IncomingPayment.Origin.KeySend
        origin == IncomingOriginDbEnum.SwapIn && swapAmount != null && swapAddress != null -> IncomingPayment.Origin.SwapIn(MilliSatoshi(swapAmount), swapAddress, null)
        origin == IncomingOriginDbEnum.Invoice && paymentRequest != null -> IncomingPayment.Origin.Invoice(PaymentRequest.read(paymentRequest))
        else -> throw UnreadableIncomingOriginInDatabase(origin, swapAmount, swapAddress, paymentRequest)
    }

    private fun parseIncomingReceivedFromDb(amount: Long?, receivedAt: Long?, receivedWithEnum: IncomingReceivedWithDbEnum?, receivedWithAmount: Long?, receivedWithChannelId: ByteArray?): IncomingPayment.Received? {
        return when {
            amount == null && receivedAt == null && receivedWithEnum == null -> null
            amount != null && receivedAt != null && receivedWithEnum == IncomingReceivedWithDbEnum.LightningPayment ->
                IncomingPayment.Received(MilliSatoshi(amount), IncomingPayment.ReceivedWith.LightningPayment, receivedAt)
            amount != null && receivedAt != null && receivedWithEnum == IncomingReceivedWithDbEnum.NewChannel && receivedWithAmount != null ->
                IncomingPayment.Received(MilliSatoshi(amount), IncomingPayment.ReceivedWith.NewChannel(MilliSatoshi(receivedWithAmount), receivedWithChannelId?.run { ByteVector32(this) }), receivedAt)
            else -> throw UnreadableIncomingPaymentStatusInDatabase(amount, receivedAt, receivedWithEnum, receivedWithAmount, receivedWithChannelId)
        }
    }

    private val hopDescAdapter = object : ColumnAdapter<List<HopDesc>, String> {
        override fun decode(databaseValue: String): List<HopDesc> = databaseValue.split(";").map { hop ->
            val els = hop.split(":")
            val n1 = PublicKey(ByteVector(els[0]))
            val n2 = PublicKey(ByteVector(els[1]))
            val cid = els[2].takeIf { it.isNotBlank() }?.run { ShortChannelId(this) }
            HopDesc(n1, n2, cid)
        }

        override fun encode(value: List<HopDesc>): String = value.joinToString(";") {
            "${it.nodeId}:${it.nextNodeId}:${it.shortChannelId ?: ""}"
        }
    }

    private val database = PaymentsDatabase(
        driver = driver,
        outgoing_payment_partsAdapter = Outgoing_payment_parts.Adapter(routeAdapter = hopDescAdapter),
        incoming_paymentsAdapter = Incoming_payments.Adapter(payment_typeAdapter = EnumColumnAdapter(), received_withAdapter = EnumColumnAdapter())
    )
    private val inQueries = database.incomingPaymentsQueries
    private val outQueries = database.outgoingPaymentsQueries
    private val aggrQueries = database.aggregatedQueriesQueries

    // ---- insert new outgoing payments

    override suspend fun addOutgoingParts(parentId: UUID, parts: List<OutgoingPayment.Part>) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                parts.map {
                    outQueries.addOutgoingPart(
                        part_id = it.id.toString(),
                        parent_id = parentId.toString(),
                        amount_msat = it.amount.msat,
                        route = it.route,
                        created_at = it.createdAt)
                }
            }
        }
    }

    override suspend fun addOutgoingPayment(outgoingPayment: OutgoingPayment) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                outQueries.addOutgoingPayment(
                    id = outgoingPayment.id.toString(),
                    recipient_amount_msat = outgoingPayment.recipientAmount.msat,
                    recipient_node_id = outgoingPayment.recipient.toString(),
                    payment_hash = outgoingPayment.details.paymentHash.toByteArray(),
                    created_at = currentTimestampMillis(),
                    normal_payment_request = (outgoingPayment.details as? OutgoingPayment.Details.Normal)?.paymentRequest?.write(),
                    keysend_preimage = (outgoingPayment.details as? OutgoingPayment.Details.KeySend)?.preimage?.toByteArray(),
                    swapout_address = (outgoingPayment.details as? OutgoingPayment.Details.SwapOut)?.address,
                )
                outgoingPayment.parts.map {
                    outQueries.addOutgoingPart(
                        part_id = it.id.toString(),
                        parent_id = outgoingPayment.id.toString(),
                        amount_msat = it.amount.msat,
                        route = it.route,
                        created_at = it.createdAt)
                }
            }
        }
    }

    // ---- successful outgoing payment

    override suspend fun updateOutgoingPart(partId: UUID, preimage: ByteVector32, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.succeedOutgoingPart(part_id = partId.toString(), preimage = preimage.toByteArray(), completed_at = completedAt)
        }
    }

    override suspend fun updateOutgoingPayment(id: UUID, preimage: ByteVector32, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.succeedOutgoingPayment(id = id.toString(), preimage = preimage.toByteArray(), completed_at = completedAt)
        }
    }

    // ---- fail outgoing payment

    override suspend fun updateOutgoingPart(partId: UUID, failure: Either<ChannelException, FailureMessage>, completedAt: Long) {
        withContext(Dispatchers.Default) {
            when (failure) {
                is Either.Left -> outQueries.failOutgoingPartWithChannelException(part_id = partId.toString(), err_type = failure.left!!::class.platformSimpleName,
                    err_channelex_channel_id = failure.left?.channelId?.toByteArray(), err_channelex_message = failure.left?.message, completed_at = completedAt)
                else -> outQueries.failOutgoingPartWithFailureMessage(part_id = partId.toString(), err_type = failure.right!!::class.platformSimpleName,
                    err_failure_message = FailureMessage.encode(failure.right!!), completed_at = completedAt)
            }
        }
    }

    override suspend fun updateOutgoingPayment(id: UUID, failure: FinalFailure, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.failOutgoingPayment(id = id.toString(), final_failure = failure::class.platformSimpleName, completed_at = completedAt)
        }
    }

    // ---- get outgoing payment details

    override suspend fun getOutgoingPart(partId: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPart(part_id = partId.toString()).executeAsOneOrNull()?.run {
                outQueries.getOutgoingPayment(id = parent_id, ::outgoingPaymentRawMapper).executeAsList()
            }?.run {
                groupByRawOutgoing(this).firstOrNull()
            }
        }
    }

    override suspend fun getOutgoingPayment(id: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPayment(id = id.toString(), ::outgoingPaymentRawMapper).executeAsList().run {
                groupByRawOutgoing(this).firstOrNull()
            }
        }
    }

    // ---- list outgoing

    override suspend fun listOutgoingPayments(paymentHash: ByteVector32): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            outQueries.listOutgoingForPaymentHash(paymentHash.toByteArray(), ::outgoingPaymentRawMapper).executeAsList()
                .run { groupByRawOutgoing(this) }
        }
    }

    @Deprecated("This method uses offset and has bad performances, use seek method instead when possible")
    override suspend fun listOutgoingPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            // LIMIT ?, ? : "the first expression is used as the OFFSET expression and the second as the LIMIT expression."
            outQueries.listOutgoingInOffset(skip.toLong(), count.toLong(), ::outgoingPaymentRawMapper).executeAsList()
                .run { groupByRawOutgoing(this) }
        }
    }

    // ---- incoming payments

    override suspend fun addIncomingPayment(preimage: ByteVector32, origin: IncomingPayment.Origin, createdAt: Long) {
        withContext(Dispatchers.Default) {
            inQueries.insert(
                payment_hash = Crypto.sha256(preimage).toByteVector32().toByteArray(),
                preimage = preimage.toByteArray(),
                payment_type = IncomingOriginDbEnum.toDb(origin),
                payment_request = if (origin is IncomingPayment.Origin.Invoice) origin.paymentRequest.write() else null,
                swap_amount_msat = if (origin is IncomingPayment.Origin.SwapIn) origin.amount.msat else null,
                swap_address = if (origin is IncomingPayment.Origin.SwapIn) origin.address else null,
                created_at = createdAt
            )
        }
    }

    override suspend fun receivePayment(paymentHash: ByteVector32, amount: MilliSatoshi, receivedWith: IncomingPayment.ReceivedWith, receivedAt: Long) {
        if (amount == MilliSatoshi(0)) throw CannotReceiveZero
        withContext(Dispatchers.Default) {
            inQueries.transaction {
                inQueries.receive(
                    value = amount.msat,
                    received_at = receivedAt,
                    received_with = IncomingReceivedWithDbEnum.toDb(receivedWith),
                    received_with_fees = receivedWith.fees.msat,
                    received_with_channel_id = if (receivedWith is IncomingPayment.ReceivedWith.NewChannel) receivedWith.channelId?.toByteArray() else null,
                    payment_hash = paymentHash.toByteArray())
                if (inQueries.changes().executeAsOne() != 1L) throw IncomingPaymentNotFound(paymentHash)
            }
        }
    }

    override suspend fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        return withContext(Dispatchers.Default) {
            inQueries.get(payment_hash = paymentHash.toByteArray(), ::incomingPaymentRawMapper).executeAsOneOrNull()
        }
    }

    override suspend fun listReceivedPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<IncomingPayment> {
        return withContext(Dispatchers.Default) {
            inQueries.list(skip.toLong(), count.toLong(), ::incomingPaymentRawMapper).executeAsList()
        }
    }

    // ---- list ALL payments

    override suspend fun listPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<WalletPayment> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPayments(count.toLong(), ::allPaymentsMapper).executeAsList()
        }
    }

    // ---- mappers & utilities

    private fun groupByRawOutgoing(payments: List<OutgoingPayment>) = payments.takeIf { it.isNotEmpty() }?.groupBy { it.id }?.values?.map { group ->
        group.first().copy(parts = group.flatMap { it.parts })
    } ?: listOf()

    private fun outgoingPaymentRawMapper(
        // outgoing payment
        id: String, recipient_amount_msat: Long, recipient_node_id: String, payment_hash: ByteArray, created_at: Long,
        normal_payment_request: String?, keysend_preimage: ByteArray?, swapout_address: String?,
        final_failure: String?, preimage: ByteArray?, completed_at: Long?,
        // payment part
        part_id: String, amount_msat: Long, route: List<HopDesc>, part_created_at: Long, part_preimage: ByteArray?, part_completed_at: Long?,
        err_type: String?, err_failure_message: ByteArray?, err_channelex_channel_id: ByteArray?, err_channelex_message: String?
    ): OutgoingPayment {
        val part = OutgoingPayment.Part(
            id = UUID.fromString(part_id),
            amount = MilliSatoshi(amount_msat),
            route = route,
            status = when {
                part_preimage != null && part_completed_at != null -> OutgoingPayment.Part.Status.Succeeded(ByteVector32(part_preimage), part_completed_at)
                err_type == ChannelException::class.platformSimpleName && err_channelex_channel_id != null && err_channelex_message != null && part_completed_at != null ->
                    OutgoingPayment.Part.Status.Failed(Either.Left(ChannelException(ByteVector32(err_channelex_channel_id), err_channelex_message)), part_completed_at)
                err_type == FailureMessage::class.platformSimpleName && err_failure_message != null && part_completed_at != null ->
                    OutgoingPayment.Part.Status.Failed(Either.Right(FailureMessage.decode(err_failure_message)), part_completed_at)
                else -> OutgoingPayment.Part.Status.Pending
            },
            createdAt = part_created_at
        )
        return OutgoingPayment(
            id = UUID.fromString(id),
            recipientAmount = MilliSatoshi(recipient_amount_msat),
            recipient = PublicKey(ByteVector(recipient_node_id)),
            details = parseOutgoingDetails(ByteVector32(payment_hash), swapout_address, keysend_preimage, normal_payment_request),
            parts = listOf(part),
            status = when {
                preimage != null && completed_at != null -> OutgoingPayment.Status.Succeeded(ByteVector32(preimage), completed_at)
                final_failure != null && completed_at != null -> OutgoingPayment.Status.Failed(reason = parseFinalFailure(final_failure), completedAt = completed_at)
                else -> OutgoingPayment.Status.Pending
            }
        )
    }

    private fun parseOutgoingDetails(paymentHash: ByteVector32, swapoutAddress: String?, keysendPreimage: ByteArray?, normalPaymentRequest: String?): OutgoingPayment.Details = when {
        swapoutAddress != null -> OutgoingPayment.Details.SwapOut(address = swapoutAddress, paymentHash = ByteVector32(paymentHash))
        keysendPreimage != null -> OutgoingPayment.Details.KeySend(preimage = ByteVector32(keysendPreimage))
        normalPaymentRequest != null -> OutgoingPayment.Details.Normal(paymentRequest = PaymentRequest.read(normalPaymentRequest))
        else -> throw UnhandledOutgoingDetails
    }

    private fun parseFinalFailure(finalFailure: String): FinalFailure = when (finalFailure) {
        FinalFailure.InvalidPaymentAmount::class.platformSimpleName -> FinalFailure.InvalidPaymentAmount
        FinalFailure.InsufficientBalance::class.platformSimpleName -> FinalFailure.InsufficientBalance
        FinalFailure.InvalidPaymentId::class.platformSimpleName -> FinalFailure.InvalidPaymentId
        FinalFailure.NoAvailableChannels::class.platformSimpleName -> FinalFailure.NoAvailableChannels
        FinalFailure.NoRouteToRecipient::class.platformSimpleName -> FinalFailure.NoRouteToRecipient
        FinalFailure.RetryExhausted::class.platformSimpleName -> FinalFailure.RetryExhausted
        FinalFailure.UnknownError::class.platformSimpleName -> FinalFailure.UnknownError
        FinalFailure.WalletRestarted::class.platformSimpleName -> FinalFailure.WalletRestarted
        else -> throw UnhandledOutgoingPaymentFailure(finalFailure)
    }

    private fun incomingPaymentRawMapper(
        payment_hash: ByteArray,
        created_at: Long,
        preimage: ByteArray,
        payment_type: IncomingOriginDbEnum,
        payment_request: String?,
        swap_amount_msat: Long?,
        swap_address: String?,
        received_amount_msat: Long?,
        received_at: Long?,
        received_with: IncomingReceivedWithDbEnum?,
        received_with_fees: Long?,
        received_with_channel_id: ByteArray?
    ): IncomingPayment {
        return IncomingPayment(
            preimage = ByteVector32(preimage),
            origin = parseIncomingOriginFromDb(payment_type, swap_amount_msat, swap_address, payment_request),
            received = parseIncomingReceivedFromDb(received_amount_msat, received_at, received_with, received_with_fees, received_with_channel_id),
            createdAt = created_at
        )
    }

    private fun allPaymentsMapper(
        direction: String,
        outgoing_payment_id: String?,
        payment_hash: ByteArray,
        preimage: ByteArray?,
        amount: Long?,
        outgoing_recipient: String?,
        outgoing_normal_payment_request: String?,
        outgoing_keysend_preimage: ByteArray?,
        outgoing_swapout_address: String?,
        outgoing_failure: String?,
        incoming_payment_type: IncomingOriginDbEnum?,
        incoming_payment_request: String?,
        incoming_swap_address: String?,
        incoming_received_with: IncomingReceivedWithDbEnum?,
        incoming_received_with_fees: Long?,
        created_at: Long,
        completed_at: Long?
    ): WalletPayment = when (direction.toLowerCase()) {
        "outgoing" -> OutgoingPayment(
            id = UUID.fromString(outgoing_payment_id!!),
            recipientAmount = MilliSatoshi(amount!!), // hack! recipient amount usually contains the amount received by the end target, but here the listAll query actually sums amounts from parts.
            recipient = PublicKey(ByteVector(outgoing_recipient!!)),
            details = parseOutgoingDetails(ByteVector32(payment_hash), outgoing_swapout_address, outgoing_keysend_preimage, outgoing_normal_payment_request),
            parts = listOf(),
            status = when {
                outgoing_failure != null && completed_at != null -> OutgoingPayment.Status.Failed(reason = parseFinalFailure(outgoing_failure), completedAt = completed_at)
                preimage != null && completed_at != null -> OutgoingPayment.Status.Succeeded(preimage = ByteVector32(preimage), completedAt = completed_at)
                else -> OutgoingPayment.Status.Pending
            }
        )
        "incoming" -> incomingPaymentRawMapper(payment_hash, created_at, preimage!!, incoming_payment_type!!, incoming_payment_request, 0L, incoming_swap_address,
            amount, completed_at, incoming_received_with, incoming_received_with_fees, null)
        else -> throw UnhandledDirection(direction)
    }
}

class UnreadableIncomingPaymentStatusInDatabase(
    amount: Long?,
    receivedAt: Long?,
    receivedWithEnum: SqlitePaymentsDb.IncomingReceivedWithDbEnum?,
    receivedWithAmount: Long?,
    receivedWithChannelId: ByteArray?
) : RuntimeException("unreadable data [ amount=$amount, receivedAt=$receivedAt, receivedWithEnum=$receivedWithEnum, receivedWithAmount=$receivedWithAmount, receivedWithChannelId=${receivedWithChannelId?.toHexString()} ]")

class UnreadableIncomingOriginInDatabase(
    origin: SqlitePaymentsDb.IncomingOriginDbEnum, swapAmount: Long?, swapAddress: String?, paymentRequest: String?
) : RuntimeException("unreadable data [ origin=$origin, swapAmount=$swapAmount, swapAddress=$swapAddress, paymentRequest=$paymentRequest ]")

object CannotReceiveZero : RuntimeException()
class IncomingPaymentNotFound(paymentHash: ByteVector32) : RuntimeException("missing payment for payment_hash=$paymentHash")
class UnhandledIncomingOrigin(val origin: IncomingPayment.Origin) : RuntimeException("unhandled origin=$origin")
class UnhandledIncomingReceivedWith(receivedWith: IncomingPayment.ReceivedWith) : RuntimeException("unhandled receivedWith=$receivedWith")
object UnhandledOutgoingDetails : RuntimeException("unhandled outgoing details")
class UnhandledOutgoingPaymentFailure(finalFailure: String) : RuntimeException("unhandled failure=$finalFailure")
class UnhandledDirection(direction: String) : RuntimeException("unhandled direction=$direction")