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
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.ShortChannelId
import fr.acinq.eclair.channel.ChannelException
import fr.acinq.eclair.db.*
import fr.acinq.eclair.db.OutgoingPayment.Status.Completed.Succeeded.OnChain.ChannelClosingType
import fr.acinq.eclair.payment.FinalFailure
import fr.acinq.eclair.payment.OutgoingPaymentFailure
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.eclair.serialization.ByteVector32KSerializer
import fr.acinq.eclair.utils.*
import fr.acinq.eclair.wire.FailureMessage
import fracinqphoenixdb.Incoming_payments
import fracinqphoenixdb.Outgoing_payment_parts
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.kodein.memory.text.toHexString

class SqlitePaymentsDb(private val driver: SqlDriver) : PaymentsDb {

    // The database table has these columns:
    // - details_type INTEGER NOT NULL
    // - details BLOB NOT NULL
    //
    // The `details_type` column is populated with a value from this enum.
    //
    enum class OutgoingDetailsType(val type: Long) {
        // WARNING: These values are saved to disk. Do not modify.
        //
        // Versioning: Each type supports up to 100 versions.
        // For example: Normal_v1(type = 101)
        //
        Normal_v0(100),
        KeySend_v0(200),
        SwapOut_v0(300),
        ChannelClosing_v0(400),
        ChannelClosing_v1(401);

        fun version(): Long = type % 100L
    }

    // The database table has these columns:
    // - details_type INTEGER NOT NULL
    // - details BLOB NOT NULL
    //
    // When storing `OutgoingPayment.details.ChannelClosing`, we map the data to
    // this serializable class, and use JSON for serialization & deserialization.
    //
    sealed class ChannelClosingJSON {
        @Serializable
        data class V0(
            val closingAddress: String,
            val isLocalWallet: Boolean
        ) {
            companion object {
            //  fun serialize(src: OutgoingPayment.Details.ChannelClosing): Pair<OutgoingDetailsType, ByteArray> {
            //      val json = ChannelClosingJSON.V0(src.closingAddress, src.isLocalWallet)
            //      val blob = Json.encodeToString(json).toByteArray(Charsets.UTF_8)
            //      return Pair(OutgoingDetailsType.ChannelClosing_v0, blob)
            //  }
                fun deserialize(blob: ByteArray, paymentHash: ByteVector32): OutgoingPayment.Details.ChannelClosing {
                    val str = String(bytes = blob, charset = Charsets.UTF_8)
                    val json = Json.decodeFromString<ChannelClosingJSON.V0>(str)
                    return OutgoingPayment.Details.ChannelClosing(
                        channelId = ByteVector32.Zeroes,
                        closingAddress = json.closingAddress,
                        isLocalWallet = json.isLocalWallet,
                        paymentHash = paymentHash
                    )
                }
            }
        }
        @Serializable
        data class V1(
            @Serializable(with = ByteVector32KSerializer::class) val channelId: ByteVector32,
            val closingAddress: String,
            val isLocalWallet: Boolean
        ) {
            companion object {
                fun serialize(src: OutgoingPayment.Details.ChannelClosing): Pair<OutgoingDetailsType, ByteArray> {
                    val json = ChannelClosingJSON.V1(src.channelId, src.closingAddress, src.isLocalWallet)
                    val blob = Json.encodeToString(json).toByteArray(Charsets.UTF_8)
                    return Pair(OutgoingDetailsType.ChannelClosing_v1, blob)
                }
                fun deserialize(blob: ByteArray, paymentHash: ByteVector32): OutgoingPayment.Details.ChannelClosing {
                    val str = String(bytes = blob, charset = Charsets.UTF_8)
                    val json = Json.decodeFromString<ChannelClosingJSON.V1>(str)
                    return OutgoingPayment.Details.ChannelClosing(
                        channelId = json.channelId,
                        closingAddress = json.closingAddress,
                        isLocalWallet = json.isLocalWallet,
                        paymentHash = paymentHash
                    )
                }
            }
        }
    }

    // The database table has these columns:
    // - status_type INTEGER DEFAULT 0
    // - status BLOB DEFAULT NULL
    //
    // The `status_type` column is populated with a value from this enum.
    //
    enum class OutgoingStatusType(val type: Long) {
        // WARNING: These values are saved to disk. Do not modify.
        //
        // Versioning: Each type supports up to 100 versions.
        // For example, Succeeded_v1(type = 101)
        //
        Succeeded_v0(100),
        Failed_v0(200),
        Mined_v0(300),
        Mined_v1(301),
        Mined_v2(302);

        fun version(): Long = type % 100L
    }

    // The database table has these columns:
    // - status_type INTEGER DEFAULT 0
    // - status BLOB DEFAULT NULL
    //
    // If the payment fails, we need to store the `sealed class FinalFailure`
    // in the `status BLOB`. We accomplish this by mapping subclasses to an enum,
    // and then storing the enum.name.toByteArray() in the column.
    //
    enum class OutgoingFinalFailureDbEnum(val finalFailure: FinalFailure) {
        InvalidPaymentAmount(FinalFailure.InvalidPaymentAmount),
        InsufficientBalance(FinalFailure.InsufficientBalance),
        InvalidPaymentId(FinalFailure.InvalidPaymentId),
        NoAvailableChannels(FinalFailure.NoAvailableChannels),
        NoRouteToRecipient(FinalFailure.NoRouteToRecipient),
        RetryExhausted(FinalFailure.RetryExhausted),
        UnknownError(FinalFailure.UnknownError),
        WalletRestarted(FinalFailure.WalletRestarted),
        RecipientUnreachable(FinalFailure.RecipientUnreachable);

        companion object {
            // This function could be implemented via looping over OutgoingFinalFailure.values(),
            // but then we'd lose our compiler check to ensure we map all possible inputs.
            fun from(failure: FinalFailure): OutgoingFinalFailureDbEnum = when (failure) {
                FinalFailure.InvalidPaymentAmount -> InvalidPaymentAmount
                FinalFailure.InsufficientBalance -> InsufficientBalance
                FinalFailure.InvalidPaymentId -> InvalidPaymentId
                FinalFailure.NoAvailableChannels -> NoAvailableChannels
                FinalFailure.NoRouteToRecipient -> NoRouteToRecipient
                FinalFailure.RetryExhausted -> RetryExhausted
                FinalFailure.UnknownError -> UnknownError
                FinalFailure.WalletRestarted -> WalletRestarted
                FinalFailure.RecipientUnreachable -> RecipientUnreachable
            }
            fun serialize(src: FinalFailure): ByteArray {
                val dbEnum = OutgoingFinalFailureDbEnum.from(src)
                return dbEnum.name.toByteArray(Charsets.UTF_8)
            }
            fun deserialize(blob: ByteArray): FinalFailure {
                val str = String(bytes = blob, charset = Charsets.UTF_8)
                val dbEnum = OutgoingFinalFailureDbEnum.valueOf(str)
                return dbEnum.finalFailure
            }
        }
    }

    // The database table has these columns:
    // - status_type INTEGER DEFAULT 0
    // - status BLOB DEFAULT NULL
    //
    // If we need to store a `OutgoingPayment.Status.Completed.Mined`
    // in the `status BLOB`, we use JSON serialization.
    //
    sealed class MinedJSON {
        object V0 {
            fun deserialize(
                @Suppress("UNUSED_PARAMETER")
                blob: ByteArray,
                completedAt: Long
            ): OutgoingPayment.Status.Completed.Succeeded.OnChain {
                return OutgoingPayment.Status.Completed.Succeeded.OnChain(
                    txids = listOf(),
                    claimed = 0.sat,
                    type = ChannelClosingType.Other,
                    completedAt = completedAt
                )
            }
        }
        @Serializable
        data class V1(
            val txids: List<@Serializable(with = ByteVector32KSerializer::class) ByteVector32>,
            val claimed: Long
        ) {
            companion object {
            //  fun serialize(
            //      src: OutgoingPayment.Status.Completed.Succeeded.OnChain
            //  ): Pair<OutgoingStatusType, ByteArray> {
            //      val json = MinedJSON.V1(src.txids, src.claimed.toLong())
            //      val blob = Json.encodeToString(json).toByteArray(Charsets.UTF_8)
            //      return Pair(OutgoingStatusType.Mined_v1, blob)
            //  }
                fun deserialize(
                    blob: ByteArray,
                    completedAt: Long
                ): OutgoingPayment.Status.Completed.Succeeded.OnChain {
                    val str = String(bytes = blob, charset = Charsets.UTF_8)
                    val json = Json.decodeFromString<MinedJSON.V1>(str)
                    return OutgoingPayment.Status.Completed.Succeeded.OnChain(
                        txids = json.txids,
                        claimed = json.claimed.sat,
                        type = ChannelClosingType.Other,
                        completedAt = completedAt
                    )
                }
            }
        }
        @Serializable
        data class V2(
            val txids: List<@Serializable(with = ByteVector32KSerializer::class) ByteVector32>,
            val claimed: Long,
            val type: String
        ) {
            companion object {
                fun serialize(
                    src: OutgoingPayment.Status.Completed.Succeeded.OnChain
                ): Pair<OutgoingStatusType, ByteArray> {
                    val json = MinedJSON.V2(src.txids, src.claimed.toLong(), src.type.name)
                    val blob = Json.encodeToString(json).toByteArray(Charsets.UTF_8)
                    return Pair(OutgoingStatusType.Mined_v2, blob)
                }
                fun deserialize(
                    blob: ByteArray,
                    completedAt: Long
                ): OutgoingPayment.Status.Completed.Succeeded.OnChain {
                    val str = String(bytes = blob, charset = Charsets.UTF_8)
                    val json = Json.decodeFromString<MinedJSON.V2>(str)
                    return OutgoingPayment.Status.Completed.Succeeded.OnChain(
                        txids = json.txids,
                        claimed = json.claimed.sat,
                        type = deserializeType(json.type),
                        completedAt = completedAt
                    )
                }
                private fun deserializeType(str: String): ChannelClosingType = when (str) {
                    ChannelClosingType.Mutual.name -> ChannelClosingType.Mutual
                    ChannelClosingType.Local.name -> ChannelClosingType.Local
                    ChannelClosingType.Remote.name -> ChannelClosingType.Remote
                    else -> ChannelClosingType.Other
                }
            }
        }
    }

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

    private val hopDescAdapter: ColumnAdapter<List<HopDesc>, String> = object : ColumnAdapter<List<HopDesc>, String> {
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
                val details = outgoingPayment.details
                val (details_type, details_blob) = when (details) {
                    is OutgoingPayment.Details.Normal -> {
                        val blob = details.paymentRequest.write().toByteArray(Charsets.UTF_8)
                        Pair(OutgoingDetailsType.Normal_v0, blob)
                    }
                    is OutgoingPayment.Details.KeySend -> {
                        val blob = details.preimage.toByteArray()
                        Pair(OutgoingDetailsType.KeySend_v0, blob)
                    }
                    is OutgoingPayment.Details.SwapOut -> {
                        val blob = details.address.toByteArray(Charsets.UTF_8)
                        Pair(OutgoingDetailsType.SwapOut_v0, blob)
                    }
                    is OutgoingPayment.Details.ChannelClosing -> {
                        ChannelClosingJSON.V1.serialize(details)
                    }
                }
                outQueries.addOutgoingPayment(
                    id = outgoingPayment.id.toString(),
                    recipient_amount_msat = outgoingPayment.recipientAmount.msat,
                    recipient_node_id = outgoingPayment.recipient.toString(),
                    payment_hash = outgoingPayment.details.paymentHash.toByteArray(),
                    created_at = currentTimestampMillis(),
                    details_type = details_type.type,
                    details = details_blob
                )
                outgoingPayment.parts.map {
                    outQueries.addOutgoingPart(
                        part_id = it.id.toString(),
                        parent_id = outgoingPayment.id.toString(),
                        amount_msat = it.amount.msat,
                        route = it.route,
                        created_at = it.createdAt
                    )
                }
            }
        }
    }

    // ---- complete outgoing payment details

    override suspend fun completeOutgoingPayment(id: UUID, completed: OutgoingPayment.Status.Completed) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                val (statusType, statusBlob) = when (completed) {
                    is OutgoingPayment.Status.Completed.Failed -> { Pair(
                        OutgoingStatusType.Failed_v0,
                        OutgoingFinalFailureDbEnum.serialize(completed.reason)
                    )}
                    is OutgoingPayment.Status.Completed.Succeeded.OffChain -> { Pair(
                        OutgoingStatusType.Succeeded_v0,
                        completed.preimage.toByteArray()
                    )}
                    is OutgoingPayment.Status.Completed.Succeeded.OnChain -> {
                        MinedJSON.V2.serialize(completed)
                    }
                }
                outQueries.updateOutgoingPayment(
                    id = id.toString(),
                    completed_at = completed.completedAt,
                    status_type = statusType.type,
                    status = statusBlob
                )
                if (outQueries.changes().executeAsOne() != 1L) throw OutgoingPaymentPartNotFound(id)
            }
        }
    }

    // ---- successful outgoing part

    override suspend fun updateOutgoingPart(partId: UUID, preimage: ByteVector32, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                outQueries.succeedOutgoingPart(part_id = partId.toString(), preimage = preimage.toByteArray(), completed_at = completedAt)
                if (outQueries.changes().executeAsOne() != 1L) throw OutgoingPaymentPartNotFound(partId)
            }
        }
    }

    // ---- fail outgoing part

    override suspend fun updateOutgoingPart(partId: UUID, failure: Either<ChannelException, FailureMessage>, completedAt: Long) {
        withContext(Dispatchers.Default) {
            val f = OutgoingPaymentFailure.convertFailure(failure)
            outQueries.transaction {
                outQueries.failOutgoingPart(
                    part_id = partId.toString(),
                    err_code = f.remoteFailureCode?.toLong(),
                    err_message = f.details,
                    completed_at = completedAt)
                if (outQueries.changes().executeAsOne() != 1L) throw OutgoingPaymentPartNotFound(partId)
            }
        }
    }

    // ---- get outgoing payment details

    override suspend fun getOutgoingPart(partId: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPart(part_id = partId.toString()).executeAsOneOrNull()?.run {
                outQueries.getOutgoingPayment(id = parent_id, ::mapOutgoingPayment).executeAsList()
            }?.run {
                groupByRawOutgoing(this).firstOrNull()
            }?.run {
                filterUselessParts(this)
                    // resulting payment must contain the request part id, or should be null
                    .takeIf { p -> p.parts.map { it.id }.contains(partId) }
            }
        }
    }

    override suspend fun getOutgoingPayment(id: UUID): OutgoingPayment? {
        return withContext(Dispatchers.Default) {
            outQueries.getOutgoingPayment(id = id.toString(), ::mapOutgoingPayment).executeAsList().run {
                groupByRawOutgoing(this).firstOrNull()
            }?.run {
                filterUselessParts(this)
            }
        }
    }

    // ---- list outgoing

    override suspend fun listOutgoingPayments(paymentHash: ByteVector32): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            outQueries.listOutgoingForPaymentHash(paymentHash.toByteArray(), ::mapOutgoingPayment).executeAsList()
                .run { groupByRawOutgoing(this) }
        }
    }

    @Deprecated("This method uses offset and has bad performances, use seek method instead when possible")
    override suspend fun listOutgoingPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<OutgoingPayment> {
        return withContext(Dispatchers.Default) {
            // LIMIT ?, ? : "the first expression is used as the OFFSET expression and the second as the LIMIT expression."
            outQueries.listOutgoingInOffset(skip.toLong(), count.toLong(), ::mapOutgoingPayment).executeAsList()
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
                    received_with_fees = receivedWith.fees?.msat,
                    received_with_channel_id = if (receivedWith is IncomingPayment.ReceivedWith.NewChannel) receivedWith.channelId?.toByteArray() else null,
                    payment_hash = paymentHash.toByteArray())
                if (inQueries.changes().executeAsOne() != 1L) throw IncomingPaymentNotFound(paymentHash)
            }
        }
    }

    override suspend fun addAndReceivePayment(preimage: ByteVector32, origin: IncomingPayment.Origin, amount: MilliSatoshi, receivedWith: IncomingPayment.ReceivedWith, createdAt: Long, receivedAt: Long) {
        withContext(Dispatchers.Default) {
            inQueries.insertAndReceive(
                payment_hash = Crypto.sha256(preimage).toByteVector32().toByteArray(),
                preimage = preimage.toByteArray(),
                payment_type = IncomingOriginDbEnum.toDb(origin),
                payment_request = if (origin is IncomingPayment.Origin.Invoice) origin.paymentRequest.write() else null,
                swap_address = if (origin is IncomingPayment.Origin.SwapIn) origin.address else null,
                received_amount_msat = amount.msat,
                received_at = receivedAt,
                received_with = IncomingReceivedWithDbEnum.toDb(receivedWith),
                received_with_fees = receivedWith.fees?.msat,
                received_with_channel_id = if (receivedWith is IncomingPayment.ReceivedWith.NewChannel) receivedWith.channelId?.toByteArray() else null,
                created_at = createdAt
            )
        }
    }

    override suspend fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        return withContext(Dispatchers.Default) {
            inQueries.get(payment_hash = paymentHash.toByteArray(), ::mapIncomingPayment).executeAsOneOrNull()
        }
    }

    override suspend fun listReceivedPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<IncomingPayment> {
        return withContext(Dispatchers.Default) {
            inQueries.list(skip.toLong(), count.toLong(), ::mapIncomingPayment).executeAsList()
        }
    }

    // ---- list ALL payments

    override suspend fun listPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<WalletPayment> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPayments(skip.toLong(), count.toLong(), ::allPaymentsMapper).executeAsList()
        }
    }

    suspend fun listPaymentsFlow(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): Flow<List<WalletPayment>> {
        return withContext(Dispatchers.Default) {
            aggrQueries.listAllPayments(skip.toLong(), count.toLong(), ::allPaymentsMapper).asFlow().mapToList()
        }
    }

    // ---- mappers & utilities

    /** Group a list of outgoing payments by parent id and parts. */
    private fun groupByRawOutgoing(payments: List<OutgoingPayment>) = payments
        .takeIf { it.isNotEmpty() }
        ?.groupBy { it.id }
        ?.values
        ?.map { group -> group.first().copy(parts = group.flatMap { it.parts }) }
        ?: listOf()

    // if a payment is successful do not take into accounts failed/pending parts.
    private fun filterUselessParts(payment: OutgoingPayment): OutgoingPayment = when (payment.status) {
        is OutgoingPayment.Status.Completed.Succeeded.OffChain -> {
            payment.copy(parts = payment.parts.filter { it.status is OutgoingPayment.Part.Status.Succeeded })
        }
        else -> payment
    }

    private fun mapOutgoingPayment(
        id: String,
        recipient_amount_msat: Long,
        recipient_node_id: String,
        payment_hash: ByteArray,
        created_at: Long,
        details_type: Long,
        details_blob: ByteArray,
        completed_at: Long?,
        status_type: Long?,
        status: ByteArray?,
        // part
        part_id: String?,
        amount_msat: Long?,
        route: List<HopDesc>?,
        part_created_at: Long?,
        part_preimage: ByteArray?,
        part_completed_at: Long?,
        err_code: Long?,
        err_message: String?
    ): OutgoingPayment {
        val parts = if (part_id != null && amount_msat != null && route != null && part_created_at != null) {
            listOf(OutgoingPayment.Part(
                id = UUID.fromString(part_id),
                amount = MilliSatoshi(amount_msat),
                route = route,
                status = mapOutgoingPartStatus(part_preimage, err_code, err_message, part_completed_at),
                createdAt = part_created_at
            ))
        } else emptyList()

        return OutgoingPayment(
            id = UUID.fromString(id),
            recipientAmount = MilliSatoshi(recipient_amount_msat),
            recipient = PublicKey(ByteVector(recipient_node_id)),
            details = mapOutgoingDetails(
                paymentHash = ByteVector32(payment_hash),
                detailsType = details_type,
                detailsBlob = details_blob
            ),
            parts = parts,
            status = mapOutgoingPaymentStatus(
                completed_at = completed_at,
                status_type = status_type ?: 0,
                status = status ?: ByteArray(0)
            ),
            createdAt = created_at
        )
    }

    private fun mapOutgoingPaymentStatus(
        completed_at: Long?,
        status_type: Long,
        status: ByteArray
    ): OutgoingPayment.Status {
        if (completed_at == null) {
            return OutgoingPayment.Status.Pending
        }
        return when (status_type) {
            OutgoingStatusType.Succeeded_v0.type -> {
                OutgoingPayment.Status.Completed.Succeeded.OffChain(
                    preimage = ByteVector32(status),
                    completedAt = completed_at
                )
            }
            OutgoingStatusType.Failed_v0.type -> {
                OutgoingPayment.Status.Completed.Failed(
                    reason = OutgoingFinalFailureDbEnum.deserialize(status),
                    completedAt = completed_at
                )
            }
            OutgoingStatusType.Mined_v0.type -> {
                MinedJSON.V0.deserialize(
                    blob = status,
                    completedAt = completed_at
                )
            }
            OutgoingStatusType.Mined_v1.type -> {
                MinedJSON.V1.deserialize(
                    blob = status,
                    completedAt = completed_at
                )
            }
            OutgoingStatusType.Mined_v2.type -> {
                MinedJSON.V2.deserialize(
                    blob = status,
                    completedAt = completed_at
                )
            }
            else -> throw UnhandledOutgoingStatus
        }
    }

    private fun mapOutgoingPartStatus(
        preimage: ByteArray?,
        errCode: Long?,
        errMessage: String?,
        completedAt: Long?
    ) = when {
        preimage != null && completedAt != null -> OutgoingPayment.Part.Status.Succeeded(ByteVector32(preimage), completedAt)
        errMessage != null && completedAt != null -> OutgoingPayment.Part.Status.Failed(errCode?.toInt(), errMessage, completedAt)
        else -> OutgoingPayment.Part.Status.Pending
    }

    private fun mapOutgoingDetails(
        paymentHash: ByteVector32,
        detailsType: Long,
        detailsBlob: ByteArray
    ): OutgoingPayment.Details = when (detailsType) {
        OutgoingDetailsType.Normal_v0.type -> {
            OutgoingPayment.Details.Normal(
                paymentRequest = PaymentRequest.read(String(detailsBlob))
            )
        }
        OutgoingDetailsType.KeySend_v0.type -> {
            OutgoingPayment.Details.KeySend(
                preimage = ByteVector32(detailsBlob)
            )
        }
        OutgoingDetailsType.SwapOut_v0.type -> {
            OutgoingPayment.Details.SwapOut(
                address = String(detailsBlob),
                paymentHash = ByteVector32(paymentHash)
            )
        }
        OutgoingDetailsType.ChannelClosing_v0.type -> {
            ChannelClosingJSON.V0.deserialize(
                blob = detailsBlob,
                paymentHash = ByteVector32(paymentHash)
            )
        }
        OutgoingDetailsType.ChannelClosing_v1.type -> {
            ChannelClosingJSON.V1.deserialize(
                blob = detailsBlob,
                paymentHash = ByteVector32(paymentHash)
            )
        }
        else -> throw UnhandledOutgoingDetails
    }

    private fun mapIncomingPayment(
        payment_hash: ByteArray,
        created_at: Long,
        preimage: ByteArray,
        payment_type: IncomingOriginDbEnum,
        payment_request: String?,
        swap_address: String?,
        received_amount_msat: Long?,
        received_at: Long?,
        received_with: IncomingReceivedWithDbEnum?,
        received_with_fees: Long?,
        received_with_channel_id: ByteArray?
    ): IncomingPayment {
        return IncomingPayment(
            preimage = ByteVector32(preimage),
            origin = mapIncomingOrigin(payment_type, swap_address, payment_request),
            received = mapIncomingReceived(received_amount_msat, received_at, received_with, received_with_fees, received_with_channel_id),
            createdAt = created_at
        )
    }

    private fun mapIncomingOrigin(origin: IncomingOriginDbEnum, swapAddress: String?, paymentRequest: String?) = when {
        origin == IncomingOriginDbEnum.KeySend -> IncomingPayment.Origin.KeySend
        origin == IncomingOriginDbEnum.SwapIn && swapAddress != null -> IncomingPayment.Origin.SwapIn(swapAddress)
        origin == IncomingOriginDbEnum.Invoice && paymentRequest != null -> IncomingPayment.Origin.Invoice(PaymentRequest.read(paymentRequest))
        else -> throw UnreadableIncomingOriginInDatabase(origin, swapAddress, paymentRequest)
    }

    private fun mapIncomingReceived(amount: Long?, receivedAt: Long?, receivedWithEnum: IncomingReceivedWithDbEnum?, receivedWithFees: Long?, receivedWithChannelId: ByteArray?): IncomingPayment.Received? {
        return when {
            amount == null && receivedAt == null && receivedWithEnum == null -> null
            amount != null && receivedAt != null && receivedWithEnum == IncomingReceivedWithDbEnum.LightningPayment ->
                IncomingPayment.Received(MilliSatoshi(amount), IncomingPayment.ReceivedWith.LightningPayment, receivedAt)
            amount != null && receivedAt != null && receivedWithEnum == IncomingReceivedWithDbEnum.NewChannel ->
                IncomingPayment.Received(MilliSatoshi(amount), IncomingPayment.ReceivedWith.NewChannel(MilliSatoshi(receivedWithFees ?: 0), receivedWithChannelId?.toByteVector32()), receivedAt)
            else -> throw UnreadableIncomingPaymentStatusInDatabase(amount, receivedAt, receivedWithEnum, receivedWithFees, receivedWithChannelId)
        }
    }

    private fun allPaymentsMapper(
        direction: String,
        outgoing_payment_id: String?,
        payment_hash: ByteArray,
        parts_amount: Long?,
        amount: Long,
        outgoing_recipient: String?,
        outgoing_details_type: Long,
        outgoing_details_blob: ByteArray?,
        outgoing_status_type: Long?,
        outgoing_status_blob: ByteArray?,
        incoming_preimage: ByteArray?,
        incoming_payment_type: IncomingOriginDbEnum?,
        incoming_payment_request: String?,
        incoming_swap_address: String?,
        incoming_received_with: IncomingReceivedWithDbEnum?,
        incoming_received_with_fees: Long?,
        created_at: Long,
        completed_at: Long?
    ): WalletPayment = when (direction.toLowerCase()) {
        "outgoing" -> {
            val details = mapOutgoingDetails(
                paymentHash = ByteVector32(payment_hash),
                detailsType = outgoing_details_type,
                detailsBlob = outgoing_details_blob ?: ByteArray(0)
            )
            // An OutgoingPayment is split between 2 tables:
            // - outgoing_payments
            // - outgoing_payment_parts
            //
            // But for this query, we're only fetching the SUM from outgoing_payment_parts.
            // This means we will not have a proper OutgoingPayment.parts list.
            // But we do have the SUM, which we can use to relay information about the fees.
            //
            val parts: List<OutgoingPayment.Part> =
                if (parts_amount != null && details is OutgoingPayment.Details.Normal) {
                    // For normal payments, we create a FAKE parts list.
                    // This allows us to properly calculate the fees associated with the payment.
                    // Note that we need to know ALL of the following:
                    // - totalAmount
                    // - feesAmount
                    //
                    listOf(OutgoingPayment.Part(
                        id = UUID.fromString("00000000-0000-0000-0000-000000000000"),
                        amount = MilliSatoshi(parts_amount),
                        route = listOf(),
                        status = OutgoingPayment.Part.Status.Succeeded(
                            preimage = ByteVector32.Zeroes,
                            completedAt = 0
                        ),
                        createdAt = 0 // <= always zero for fake parts
                    ))
                } else {
                    listOf()
                }
            OutgoingPayment(
                id = UUID.fromString(outgoing_payment_id!!),
                recipientAmount = MilliSatoshi(amount),
                recipient = PublicKey(ByteVector(outgoing_recipient!!)),
                details = details,
                parts = parts,
                status = mapOutgoingPaymentStatus(
                    completed_at = completed_at,
                    status_type = outgoing_status_type ?: 0,
                    status = outgoing_status_blob ?: ByteArray(0)
                ),
                createdAt = created_at
            )
        }
        "incoming" -> mapIncomingPayment(
            payment_hash = payment_hash,
            created_at = created_at,
            preimage =  incoming_preimage!!,
            payment_type = incoming_payment_type!!,
            payment_request = incoming_payment_request,
            swap_address = incoming_swap_address,
            received_amount_msat = amount,
            received_at = completed_at,
            received_with = incoming_received_with,
            received_with_fees = incoming_received_with_fees,
            received_with_channel_id = null
        )
        else -> throw UnhandledDirection(direction)
    }
}

class UnreadableIncomingPaymentStatusInDatabase(
    amount: Long?,
    receivedAt: Long?,
    receivedWithEnum: SqlitePaymentsDb.IncomingReceivedWithDbEnum?,
    receivedWithFees: Long?,
    receivedWithChannelId: ByteArray?
) : RuntimeException("unreadable data [ amount=$amount, receivedAt=$receivedAt, receivedWithEnum=$receivedWithEnum, receivedWithFees=$receivedWithFees, receivedWithChannelId=${receivedWithChannelId?.toHexString()} ]")

class UnreadableIncomingOriginInDatabase(
    origin: SqlitePaymentsDb.IncomingOriginDbEnum, swapAddress: String?, paymentRequest: String?
) : RuntimeException("unreadable data [ origin=$origin, swapAddress=$swapAddress, paymentRequest=$paymentRequest ]")

object CannotReceiveZero : RuntimeException()
class OutgoingPaymentNotFound(id: UUID) : RuntimeException("could not find outgoing payment with id=$id")
class OutgoingPaymentPartNotFound(partId: UUID) : RuntimeException("could not find outgoing payment part with part_id=$partId")
class IncomingPaymentNotFound(paymentHash: ByteVector32) : RuntimeException("missing payment for payment_hash=$paymentHash")
class UnhandledIncomingOrigin(origin: IncomingPayment.Origin) : RuntimeException("unhandled origin=$origin")
class UnhandledIncomingReceivedWith(receivedWith: IncomingPayment.ReceivedWith) : RuntimeException("unhandled receivedWith=$receivedWith")
object UnhandledOutgoingDetails : RuntimeException("unhandled outgoing details")
object UnhandledOutgoingStatus: RuntimeException("unhandled outgoing status")
class UnhandledDirection(direction: String) : RuntimeException("unhandled direction=$direction")