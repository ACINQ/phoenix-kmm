/*
 * Copyright 2021 ACINQ SAS
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

package fr.acinq.phoenix.db.payments

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.utils.*
import fr.acinq.phoenix.db.IncomingPaymentNotFound
import fr.acinq.phoenix.db.PaymentRowId
import fr.acinq.phoenix.db.PaymentsDatabase
import fr.acinq.phoenix.db.didCompletePaymentRow
import fracinqphoenixdb.IncomingPaymentsQueries
import org.kodein.memory.text.toHexString

class IncomingQueries(private val database: PaymentsDatabase) {

    private val queries = database.incomingPaymentsQueries

    fun addIncomingPayment(preimage: ByteVector32, origin: IncomingPayment.Origin, createdAt: Long) {
        val (originType, originData) = origin.mapToDb()
        queries.insert(
            payment_hash = Crypto.sha256(preimage).toByteVector32().toByteArray(),
            preimage = preimage.toByteArray(),
            origin_type = originType,
            origin_blob = originData,
            created_at = createdAt
        )
    }

    fun receivePayment(paymentHash: ByteVector32, receivedWith: Set<IncomingPayment.ReceivedWith>, receivedAt: Long) {
        database.transaction {
            val existingReceivedWith: Set<IncomingPayment.ReceivedWith> = queries.get(
                payment_hash = paymentHash.toByteArray(),
                mapper = ::mapIncomingPayment
            ).executeAsOneOrNull()?.received?.receivedWith ?: emptySet()
            val (receivedWithType, receivedWithBlob) = (existingReceivedWith + receivedWith).mapToDb() ?: null to null
            queries.updateReceived(
                received_at = receivedAt,
                received_with_type = receivedWithType,
                received_with_blob = receivedWithBlob,
                payment_hash = paymentHash.toByteArray()
            )
            if (queries.changes().executeAsOne() != 1L) {
                throw IncomingPaymentNotFound(paymentHash)
            }
            didCompletePaymentRow(PaymentRowId.IncomingPaymentId(paymentHash), database)
        }
    }

    fun updateNewChannelReceivedWithChannelId(paymentHash: ByteVector32, channelId: ByteVector32) {
        database.transaction {
            val paymentInDb: IncomingPayment? = queries.get(
                payment_hash = paymentHash.toByteArray(),
                mapper = ::mapIncomingPayment
            ).executeAsOneOrNull()
            val (receivedWithType, receivedWithBlob) = paymentInDb?.received?.receivedWith?.map {
                when (it) {
                    is IncomingPayment.ReceivedWith.NewChannel -> it.copy(channelId = channelId)
                    else -> it
                }
            }?.toSet()?.mapToDb() ?: null to null
            queries.updateReceived(
                received_at = paymentInDb?.received?.receivedAt,
                received_with_type = receivedWithType,
                received_with_blob = receivedWithBlob,
                payment_hash = paymentHash.toByteArray()
            )
            if (queries.changes().executeAsOne() != 1L) {
                throw IncomingPaymentNotFound(paymentHash)
            }
            didCompletePaymentRow(PaymentRowId.IncomingPaymentId(paymentHash), database)
        }
    }

    fun addAndReceivePayment(
        preimage: ByteVector32,
        origin: IncomingPayment.Origin,
        receivedWith: Set<IncomingPayment.ReceivedWith>,
        createdAt: Long,
        receivedAt: Long
    ) {
        database.transaction {
            val paymentHash = Crypto.sha256(preimage).toByteVector32()
            val (originType, originData) = origin.mapToDb()
            val (receivedWithType, receivedWithBlob) = receivedWith.mapToDb() ?: null to null
            queries.insertAndReceive(
                payment_hash = paymentHash.toByteArray(),
                preimage = preimage.toByteArray(),
                origin_type = originType,
                origin_blob = originData,
                received_at = receivedAt,
                received_with_type = receivedWithType,
                received_with_blob = receivedWithBlob,
                created_at = createdAt
            )
            didCompletePaymentRow(PaymentRowId.IncomingPaymentId(paymentHash), database)
        }
    }

    fun getIncomingPayment(paymentHash: ByteVector32): IncomingPayment? {
        return queries.get(payment_hash = paymentHash.toByteArray(), ::mapIncomingPayment).executeAsOneOrNull()
    }

    fun listReceivedPayments(count: Int, skip: Int): List<IncomingPayment> {
        return queries.list(
            limit = count.toLong(),
            offset = skip.toLong(),
            mapper = ::mapIncomingPayment
        ).executeAsList()
    }

    fun testCborOptimizations(): String {
        var results = ""
        queries.list(
            limit = 100,
            offset = 0
        ).executeAsList().forEach {
            results += "IncomingPayment[${it.payment_hash.toHexString()}]:\n"

            if (true) {
                val origin = IncomingOriginData.deserialize(
                    typeVersion = it.origin_type,
                    blob = it.origin_blob
                )
                val (newTypeVersion, cborBlob) = origin.mapToDb(useCbor = true)

                results += "- Origin:\n"
                results += "  - ${it.origin_type.name}: ${it.origin_blob.size}\n"
                results += "  - ${newTypeVersion.name}: ${cborBlob.size}\n"
            }

            if (it.received_with_type != null &&
                it.received_with_blob != null
            ) {
                val received = IncomingReceivedWithData.deserialize(
                    typeVersion = it.received_with_type,
                    blob = it.received_with_blob,
                    amount = it.received_amount_msat?.msat,
                    originTypeVersion = it.origin_type
                )
                received.mapToDb(useCbor = true)?.let { pair ->
                    val newTypeVersion = pair.first
                    val cborBlob = pair.second

                    results += "- Received:\n"
                    results += "  - ${it.received_with_type.name}: ${it.received_with_blob.size}\n"
                    results += "  - ${newTypeVersion.name}: ${cborBlob.size}\n"
                }
            }
        }
        return results
    }

    companion object {
        fun mapIncomingPayment(
            @Suppress("UNUSED_PARAMETER") payment_hash: ByteArray,
            preimage: ByteArray,
            created_at: Long,
            origin_type: IncomingOriginTypeVersion,
            origin_blob: ByteArray,
            received_amount_msat: Long?,
            received_at: Long?,
            received_with_type: IncomingReceivedWithTypeVersion?,
            received_with_blob: ByteArray?,
        ): IncomingPayment {
            return IncomingPayment(
                preimage = ByteVector32(preimage),
                origin = IncomingOriginData.deserialize(origin_type, origin_blob),
                received = mapIncomingReceived(received_amount_msat?.msat, received_at, origin_type, received_with_type, received_with_blob),
                createdAt = created_at
            )
        }

        private fun mapIncomingReceived(
            amount: MilliSatoshi?,
            receivedAt: Long?,
            originTypeVersion: IncomingOriginTypeVersion,
            receivedWithTypeVersion: IncomingReceivedWithTypeVersion?,
            receivedWithBlob: ByteArray?
        ): IncomingPayment.Received? {
            return when {
                receivedAt == null && receivedWithTypeVersion == null && receivedWithBlob == null -> null
                receivedAt != null && receivedWithTypeVersion != null && receivedWithBlob != null -> {
                    IncomingPayment.Received(IncomingReceivedWithData.deserialize(receivedWithTypeVersion, receivedWithBlob, amount, originTypeVersion), receivedAt)
                }
                else -> throw UnreadableIncomingReceivedWith(receivedAt, receivedWithTypeVersion, receivedWithBlob)
            }
        }
    }
}

class UnreadableIncomingReceivedWith(receivedAt: Long?, receivedWithTypeVersion: IncomingReceivedWithTypeVersion?, receivedWithBlob: ByteArray?) :
    RuntimeException("unreadable received with data [ receivedAt=$receivedAt, receivedWithTypeVersion=$receivedWithTypeVersion, receivedWithBlob=$receivedWithBlob ]")