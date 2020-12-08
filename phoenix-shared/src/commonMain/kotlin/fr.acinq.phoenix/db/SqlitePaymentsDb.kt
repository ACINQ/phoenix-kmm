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
import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PublicKey
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.channel.ChannelException
import fr.acinq.eclair.db.*
import fr.acinq.eclair.payment.FinalFailure
import fr.acinq.eclair.utils.Either
import fr.acinq.eclair.utils.UUID
import fr.acinq.eclair.utils.currentTimestampMillis
import fr.acinq.eclair.wire.FailureMessage
import fracinqphoenixdb.Outgoing_payment_parts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.log.platformSimpleName

class SqlitePaymentsDb(private val driver: SqlDriver) : PaymentsDb {

    val milliSatoshiAdapter = object : ColumnAdapter<MilliSatoshi, Long> {
        override fun decode(databaseValue: Long): MilliSatoshi = MilliSatoshi(databaseValue)
        override fun encode(value: MilliSatoshi): Long = value.msat
    }

    val hopDescAdapter = object : ColumnAdapter<List<HopDesc>, String> {
        override fun decode(databaseValue: String): List<HopDesc> = databaseValue.split(";").map {
            val els = it.split(":")
            val n1 = els[0]
            val n2 = els[1]
            HopDesc(PublicKey(ByteVector(n1).toByteArray()), PublicKey(ByteVector(n2).toByteArray()))
        }

        override fun encode(value: List<HopDesc>): String = value.joinToString(";") {
            "${it.nodeId}:${it.nextNodeId}:${it.shortChannelId}"
        }
    }

    private val database = PaymentsDatabase(
        driver = driver,
        outgoing_payment_partsAdapter = Outgoing_payment_parts.Adapter(routeAdapter = hopDescAdapter)
    )
    private val inQueries = database.incomingPaymentsQueries
    private val outQueries = database.outgoingPaymentsQueries

    // ---- create outgoing payment

    override suspend fun addOutgoingParts(parentId: UUID, parts: List<OutgoingPayment.Part>) {
        withContext(Dispatchers.Default) {
            outQueries.transaction {
                parts.map {
                    outQueries.addOutgoingPart(
                        id = it.id.toString(),
                        parent_id = parentId.toString(),
                        amount_msat = it.amount.msat,
                        route = it.route,
                        created_at = currentTimestampMillis())
                }
            }
        }
    }

    override suspend fun addOutgoingPayment(outgoingPayment: OutgoingPayment) {
        withContext(Dispatchers.Default) {
            when (outgoingPayment.details) {
                is OutgoingPayment.Details.Normal -> outQueries.addOutgoingPaymentNormal(outgoingPayment.id.toString(), outgoingPayment.recipientAmount.msat, outgoingPayment.recipient.toUncompressedBin(),
                    outgoingPayment.details.paymentHash.toByteArray(), currentTimestampMillis(), outgoingPayment.details::class.platformSimpleName, (outgoingPayment.details as OutgoingPayment.Details.Normal).paymentRequest.write())
                is OutgoingPayment.Details.KeySend -> outQueries.addOutgoingPaymentKeysend(outgoingPayment.id.toString(), outgoingPayment.recipientAmount.msat, outgoingPayment.recipient.toUncompressedBin(),
                    outgoingPayment.details.paymentHash.toByteArray(), currentTimestampMillis(), outgoingPayment.details::class.platformSimpleName, (outgoingPayment.details as OutgoingPayment.Details.KeySend).preimage.toByteArray())
                is OutgoingPayment.Details.SwapOut -> outQueries.addOutgoingPaymentSwapOut(outgoingPayment.id.toString(), outgoingPayment.recipientAmount.msat, outgoingPayment.recipient.toUncompressedBin(),
                    outgoingPayment.details.paymentHash.toByteArray(), currentTimestampMillis(), outgoingPayment.details::class.platformSimpleName, (outgoingPayment.details as OutgoingPayment.Details.SwapOut).address)
            }
        }
    }

    // ---- successful outgoing payment

    override suspend fun updateOutgoingPart(partId: UUID, preimage: ByteVector32, completedAt: Long) {
        withContext(Dispatchers.Default) {
            outQueries.succeedOutgoingPart(id = partId.toString(), preimage = preimage.toByteArray(), completed_at = completedAt)
        }
    }

    override suspend fun updateOutgoingPayment(id: UUID, preimage: ByteVector32, completedAt: Long) {
        TODO("Not yet implemented")
    }

    // ---- fail outgoing payment

    override suspend fun updateOutgoingPayment(id: UUID, failure: FinalFailure, completedAt: Long) {
        TODO("Not yet implemented")
    }

    override suspend fun updateOutgoingPart(partId: UUID, failure: Either<ChannelException, FailureMessage>, completedAt: Long) {
        TODO("Not yet implemented")
    }

    // ---- get outgoing payment details

    override suspend fun getOutgoingPart(partId: UUID): OutgoingPayment? {
        TODO("Not yet implemented")
    }

    override suspend fun getOutgoingPayment(id: UUID): OutgoingPayment? {
        TODO("Not yet implemented")
    }

    // ---- list outgoing

    override suspend fun listOutgoingPayments(paymentHash: ByteVector32): List<OutgoingPayment> {
        TODO("Not yet implemented")
    }

    override suspend fun listOutgoingPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<OutgoingPayment> {
        TODO("Not yet implemented")
    }

    // ---- incoming payments

    override suspend fun addIncomingPayment(preimage: ByteVector32, origin: IncomingPayment.Origin, createdAt: Long) {
        withContext(Dispatchers.Default) {
            when (origin) {
                is IncomingPayment.Origin.KeySend -> inQueries.addIncomingKeysend(preimage.toByteArray(), origin::class.platformSimpleName, currentTimestampMillis())
                is IncomingPayment.Origin.Invoice -> inQueries.addIncomingInvoice(preimage.toByteArray(), origin::class.platformSimpleName, origin.paymentRequest.write(), currentTimestampMillis())
                is IncomingPayment.Origin.SwapIn -> inQueries.addIncomingSwapin(preimage.toByteArray(), origin::class.platformSimpleName, origin.amount.msat, origin.address, currentTimestampMillis())
            }
        }
    }

    override suspend fun receivePayment(paymentHash: ByteVector32, amount: MilliSatoshi, receivedWith: IncomingPayment.ReceivedWith, receivedAt: Long) {
        TODO("Not yet implemented")
    }

    override suspend fun listReceivedPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<IncomingPayment> {
        TODO("Not yet implemented")
    }

    override suspend fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        TODO("Not yet implemented")
    }

    // ---- list all payments

    override suspend fun listPayments(count: Int, skip: Int, filters: Set<PaymentTypeFilter>): List<WalletPayment> {
        TODO("Not yet implemented")
    }
}