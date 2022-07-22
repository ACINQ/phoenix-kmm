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
import fr.acinq.bitcoin.Satoshi
import fr.acinq.lightning.db.ChannelClosingType
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.payment.FinalFailure
import fr.acinq.lightning.serialization.v1.ByteVector32KSerializer
import fr.acinq.lightning.serialization.v1.SatoshiKSerializer
import fr.acinq.lightning.utils.toByteVector32
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json

enum class OutgoingStatusTypeVersion {
    SUCCEEDED_OFFCHAIN_V0, // encoded using JSON
    SUCCEEDED_OFFCHAIN_V1, // encoded using CBOR
    SUCCEEDED_ONCHAIN_V0,  // encoded using JSON
    SUCCEEDED_ONCHAIN_V1,  // encoded using CBOR
    FAILED_V0,             // encoded using JSON
    FAILED_V1,             // encoded using CBOR
}

sealed class OutgoingStatusData {

    sealed class SucceededOffChain : OutgoingStatusData() {
        @Serializable
        data class V0(
            @Serializable(with = ByteVector32KSerializer::class)
            val preimage: ByteVector32
        ) : SucceededOffChain() {
            constructor(offChain: OutgoingPayment.Status.Completed.Succeeded.OffChain): this(
                preimage = offChain.preimage
            )
            fun unwrap(completedAt: Long) = OutgoingPayment.Status.Completed.Succeeded.OffChain(
                preimage = preimage,
                completedAt = completedAt
            )
        }
        @Serializable
        @OptIn(ExperimentalSerializationApi::class)
        data class V1(
            @ByteString
            val preimage: ByteArray
        ) : SucceededOffChain() {
            constructor(offChain: OutgoingPayment.Status.Completed.Succeeded.OffChain): this(
                preimage = offChain.preimage.toByteArray()
            )
            fun unwrap(completedAt: Long) = OutgoingPayment.Status.Completed.Succeeded.OffChain(
                preimage = preimage.toByteVector32(),
                completedAt = completedAt
            )
        }
    }

    sealed class SucceededOnChain : OutgoingStatusData() {
        companion object {
            internal fun serializeClosingType(closingType: ChannelClosingType) = closingType.name

            internal fun deserializeClosingType(closingType: String): ChannelClosingType = try {
                ChannelClosingType.valueOf(closingType)
            } catch (e: Exception) {
                ChannelClosingType.Other
            }
        }

        @Serializable
        data class V0(
            val txIds: List<@Serializable(with = ByteVector32KSerializer::class) ByteVector32>,
            @Serializable(with = SatoshiKSerializer::class)
            val claimed: Satoshi,
            val closingType: String
        ) : SucceededOnChain() {
            constructor(onChain: OutgoingPayment.Status.Completed.Succeeded.OnChain): this(
                txIds = onChain.txids,
                claimed = onChain.claimed,
                closingType = serializeClosingType(onChain.closingType)
            )
            fun unwrap(completedAt: Long) = OutgoingPayment.Status.Completed.Succeeded.OnChain(
                txids = txIds,
                claimed = claimed,
                closingType = deserializeClosingType(closingType),
                completedAt = completedAt
            )
        }
        @Serializable
        @OptIn(ExperimentalSerializationApi::class)
        data class V1(
            @ByteString
            val txIds: ByteArray,
            @Serializable(with = SatoshiKSerializer::class)
            val claimed: Satoshi,
            val closingType: String
        ) : SucceededOnChain() {
            constructor(onChain: OutgoingPayment.Status.Completed.Succeeded.OnChain): this(
                txIds = flattenTxids(onChain.txids),
                claimed = onChain.claimed,
                closingType = serializeClosingType(onChain.closingType)
            )
            fun unwrap(completedAt: Long) = OutgoingPayment.Status.Completed.Succeeded.OnChain(
                txids = unflattenTxids(txIds),
                claimed = claimed,
                closingType = deserializeClosingType(closingType),
                completedAt = completedAt
            )
            companion object {
                fun flattenTxids(list: List<ByteVector32>): ByteArray {
                    var buffer = ByteArray(size = 0)
                    list.forEach {
                        buffer += it.toByteArray()
                    }
                    return buffer
                }
                fun unflattenTxids(buffer: ByteArray): List<ByteVector32> {
                    if (buffer.size % 32 != 0) {
                        throw Exception("OutgoingStatusData.SucceededOnChain.V1: invalid txids")
                    }
                    val list: MutableList<ByteVector32> = mutableListOf()
                    var offset = 0
                    while (offset < buffer.size) {
                        list.add(buffer.copyOfRange(offset, offset+32).toByteVector32())
                        offset += 32
                    }
                    return list.toList()
                }
            }
        }
    }

    sealed class Failed : OutgoingStatusData() {
        companion object {
            internal fun serializeFinalFailure(failure: FinalFailure): String = failure::class.simpleName ?: "UnknownError"

            internal fun deserializeFinalFailure(failure: String): FinalFailure = when (failure) {
                FinalFailure.InvalidPaymentAmount::class.simpleName -> FinalFailure.InvalidPaymentAmount
                FinalFailure.InvalidPaymentId::class.simpleName -> FinalFailure.InvalidPaymentId
                FinalFailure.NoAvailableChannels::class.simpleName -> FinalFailure.NoAvailableChannels
                FinalFailure.InsufficientBalance::class.simpleName -> FinalFailure.InsufficientBalance
                FinalFailure.NoRouteToRecipient::class.simpleName -> FinalFailure.NoRouteToRecipient
                FinalFailure.RecipientUnreachable::class.simpleName -> FinalFailure.RecipientUnreachable
                FinalFailure.RetryExhausted::class.simpleName -> FinalFailure.RetryExhausted
                FinalFailure.WalletRestarted::class.simpleName -> FinalFailure.WalletRestarted
                else -> FinalFailure.UnknownError
            }
        }
        @Serializable
        data class V0(val reason: String) : Failed() {
            constructor(failed: OutgoingPayment.Status.Completed.Failed): this(
                reason = serializeFinalFailure(failed.reason)
            )
            fun unwrap(completedAt: Long) = OutgoingPayment.Status.Completed.Failed(
                reason = deserializeFinalFailure(reason),
                completedAt = completedAt
            )
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(
            typeVersion: OutgoingStatusTypeVersion,
            blob: ByteArray,
            completedAt: Long
        ): OutgoingPayment.Status {
            val jsonStr = { String(bytes = blob, charset = Charsets.UTF_8) }
            return when (typeVersion) {
                OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 -> {
                    Json.decodeFromString<SucceededOffChain.V0>(jsonStr()).unwrap(completedAt)
                }
                OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V1 -> {
                    Cbor.decodeFromByteArray<SucceededOffChain.V1>(blob).unwrap(completedAt)
                }
                OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0 -> {
                    Json.decodeFromString<SucceededOnChain.V0>(jsonStr()).unwrap(completedAt)
                }
                OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V1 -> {
                    Cbor.decodeFromByteArray<SucceededOnChain.V1>(blob).unwrap(completedAt)
                }
                OutgoingStatusTypeVersion.FAILED_V0 -> {
                    Json.decodeFromString<Failed.V0>(jsonStr()).unwrap(completedAt)
                }
                OutgoingStatusTypeVersion.FAILED_V1 -> { // reusing V0 wrapper
                    Cbor.decodeFromByteArray<Failed.V0>(blob).unwrap(completedAt)
                }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun OutgoingPayment.Status.Completed.mapToDb(
    useCbor: Boolean = false
): Pair<OutgoingStatusTypeVersion, ByteArray> = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded.OffChain -> {
        if (useCbor) {
            OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V1 to Cbor.encodeToByteArray(
                OutgoingStatusData.SucceededOffChain.V1(this)
            )
        } else {
            OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 to Json.encodeToString(
                OutgoingStatusData.SucceededOffChain.V0(this)
            ).toByteArray(Charsets.UTF_8)
        }
    }
    is OutgoingPayment.Status.Completed.Succeeded.OnChain -> {
        if (useCbor) {
            OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V1 to Cbor.encodeToByteArray(
                OutgoingStatusData.SucceededOnChain.V1(this)
            )
        } else {
            OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0 to Json.encodeToString(
                OutgoingStatusData.SucceededOnChain.V0(this)
            ).toByteArray(Charsets.UTF_8)
        }
    }
    is OutgoingPayment.Status.Completed.Failed -> {
        if (useCbor) {
            OutgoingStatusTypeVersion.FAILED_V1 to Cbor.encodeToByteArray(
                OutgoingStatusData.Failed.V0(this) // reusing V0 wrapper
            )
        } else {
            OutgoingStatusTypeVersion.FAILED_V0 to Json.encodeToString(
                OutgoingStatusData.Failed.V0(this)
            ).toByteArray(Charsets.UTF_8)
        }
    }
}
