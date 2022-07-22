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
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.serialization.v1.ByteVector32KSerializer
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toByteVector32
import fr.acinq.phoenix.db.cloud.MilliSatoshiSerializer
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass


enum class IncomingReceivedWithTypeVersion {
    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    NEW_CHANNEL_V0,
    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    LIGHTNING_PAYMENT_V0,
    // multiparts payments are when receivedWith is a set of parts (new channel and htlcs)
    @Deprecated("MULTIPARTS_V0 had an issue where the incoming amount of pay-to-open (new channels over LN) contained the fee, " +
            "instead of only the pushed amount. V1 fixes this by convention, when deserializing the object. No new [IncomingReceivedWithData.Part.xxx.V1] is needed.")
    MULTIPARTS_V0,
    MULTIPARTS_V1,
    MULTIPARTS_V2,
}

sealed class IncomingReceivedWithData {

    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    sealed class NewChannel : IncomingReceivedWithData() {
        @Serializable
        @Suppress("DEPRECATION")
        data class V0(
            val fees: MilliSatoshi,
            @Serializable(with = ByteVector32KSerializer::class)
            val channelId: ByteVector32?
        ) : NewChannel()
    }

    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    sealed class LightningPayment : IncomingReceivedWithData() {
        @Serializable
        @SerialName("LIGHTNING_PAYMENT_V0")
        @Suppress("DEPRECATION")
        object V0 : LightningPayment()
    }

    @Serializable
    sealed class Part : IncomingReceivedWithData() {
        sealed class Htlc : Part() {
            @Serializable
            data class V0(
                val amount: MilliSatoshi,
                @Serializable(with = ByteVector32KSerializer::class)
                val channelId: ByteVector32,
                val htlcId: Long
            ) : Htlc() {
                constructor(lightningPayment: IncomingPayment.ReceivedWith.LightningPayment): this(
                    amount = lightningPayment.amount,
                    channelId = lightningPayment.channelId,
                    htlcId = lightningPayment.htlcId
                )
                fun unwrap() = IncomingPayment.ReceivedWith.LightningPayment(
                    amount = this.amount,
                    channelId = this.channelId,
                    htlcId = this.htlcId
                )
            }

            @Serializable
            @OptIn(ExperimentalSerializationApi::class)
            data class V1(
                @Serializable(with = MilliSatoshiSerializer::class)
                val amount: MilliSatoshi,
                @ByteString
                val channelId: ByteArray,
                val htlcId: Long
            ) : Htlc() {
                constructor(lightningPayment: IncomingPayment.ReceivedWith.LightningPayment): this(
                    amount = lightningPayment.amount,
                    channelId = lightningPayment.channelId.toByteArray(),
                    htlcId = lightningPayment.htlcId
                )
                fun unwrap() = IncomingPayment.ReceivedWith.LightningPayment(
                    amount = this.amount,
                    channelId = this.channelId.toByteVector32(),
                    htlcId = this.htlcId
                )
            }
        }

        sealed class NewChannel : Part() {
            @Serializable
            data class V0(
                val amount: MilliSatoshi,
                val fees: MilliSatoshi,
                @Serializable(with = ByteVector32KSerializer::class)
                val channelId: ByteVector32?
            ) : NewChannel() {
                constructor(newChannel: IncomingPayment.ReceivedWith.NewChannel): this(
                    amount = newChannel.amount,
                    fees = newChannel.fees,
                    channelId = newChannel.channelId
                )
                @Suppress("DEPRECATION")
                fun unwrap(
                    typeVersion: IncomingReceivedWithTypeVersion,
                    originTypeVersion: IncomingOriginTypeVersion?
                ): IncomingPayment.ReceivedWith.NewChannel {
                    var newChannel = IncomingPayment.ReceivedWith.NewChannel(
                        amount = this.amount,
                        fees = this.fees,
                        channelId = this.channelId
                    )
                    if (typeVersion == IncomingReceivedWithTypeVersion.MULTIPARTS_V0 &&
                        originTypeVersion != IncomingOriginTypeVersion.SWAPIN_V0) {
                        newChannel = newChannel.copy(amount = this.amount - this.fees)
                    }
                    return newChannel
                }
            }

            @Serializable
            @OptIn(ExperimentalSerializationApi::class)
            data class V1(
                @Serializable(with = MilliSatoshiSerializer::class)
                val amount: MilliSatoshi,
                @Serializable(with = MilliSatoshiSerializer::class)
                val fees: MilliSatoshi,
                @ByteString
                val channelId: ByteArray?
            ) : NewChannel() {
                constructor(newChannel: IncomingPayment.ReceivedWith.NewChannel): this(
                    amount = newChannel.amount,
                    fees = newChannel.fees,
                    channelId = newChannel.channelId?.toByteArray()
                )
                fun unwrap() = IncomingPayment.ReceivedWith.NewChannel(
                    amount = this.amount,
                    fees = this.fees,
                    channelId = this.channelId?.toByteVector32()
                )
            }
        }

        companion object {
            val jsonPolymorphicV0 = Json { serializersModule = SerializersModule {
                polymorphic(Part::class) {
                    subclass(Htlc.V0::class)
                    subclass(NewChannel.V0::class)
                }
            }}

            @OptIn(ExperimentalSerializationApi::class)
            val cborPolymorphicV1 = Cbor { serializersModule = SerializersModule {
                polymorphic(Part::class) {
                    subclass(Htlc.V1::class)
                    subclass(NewChannel.V1::class)
                }
            }}
        }
    }

    companion object {
        /**
         * Deserializes a received-with blob from the database using the typeVersion given.
         *
         * @param amount This parameter is only used if the typeVersion is [IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0] or
         *               [IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0]. In that case we make a list of parts made of only one
         *               part and containing this amount.
         * @param originTypeVersion This parameter is only used if the typeVersion is [IncomingReceivedWithTypeVersion.MULTIPARTS_V0],
         *               in which case if a part is [Part.NewChannel] then the fee must be subtracted from the amount.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(
            typeVersion: IncomingReceivedWithTypeVersion,
            blob: ByteArray,
            amount: MilliSatoshi?,
            originTypeVersion: IncomingOriginTypeVersion
        ):  Set<IncomingPayment.ReceivedWith> {
            @Suppress("DEPRECATION")
            return when (typeVersion) {
                IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0 -> {
                    setOf(IncomingPayment.ReceivedWith.LightningPayment(
                        amount = amount ?: 0.msat,
                        channelId = ByteVector32.Zeroes,
                        htlcId = 0L
                    ))
                }
                IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0 -> {
                    setOf(Json.decodeFromString<NewChannel.V0>(
                        String(bytes = blob, charset = Charsets.UTF_8)
                    ).let {
                        IncomingPayment.ReceivedWith.NewChannel(
                            amount = amount ?: 0.msat,
                            fees = it.fees,
                            channelId = it.channelId
                        )
                    })
                }
                IncomingReceivedWithTypeVersion.MULTIPARTS_V0,
                IncomingReceivedWithTypeVersion.MULTIPARTS_V1 -> {
                    Part.jsonPolymorphicV0.decodeFromString(
                        deserializer = SetSerializer(PolymorphicSerializer(Part::class)),
                        string = String(bytes = blob, charset = Charsets.UTF_8)
                    ).map {
                        when (it) {
                            is Part.Htlc.V0 -> it.unwrap()
                            is Part.NewChannel.V0 -> it.unwrap(typeVersion, originTypeVersion)
                            else -> throw Exception("unreachable code")
                        }
                    }.toSet()
                }
                IncomingReceivedWithTypeVersion.MULTIPARTS_V2 -> {
                    Part.cborPolymorphicV1.decodeFromByteArray(
                        deserializer = SetSerializer(PolymorphicSerializer(Part::class)),
                        bytes = blob
                    ).map {
                        when (it) {
                            is Part.Htlc.V1 -> it.unwrap()
                            is Part.NewChannel.V1 -> it.unwrap()
                            else -> throw Exception("unreachable code")
                        }
                    }.toSet()
                }
            }
        }
    }
}

/** Only serialize received_with into the [IncomingReceivedWithTypeVersion.MULTIPARTS_V1] type. */
@OptIn(ExperimentalSerializationApi::class)
fun Set<IncomingPayment.ReceivedWith>.mapToDb(
    useCbor: Boolean = false
): Pair<IncomingReceivedWithTypeVersion, ByteArray>? {
    return if (useCbor) {
        map {
            when (it) {
                is IncomingPayment.ReceivedWith.LightningPayment -> {
                    IncomingReceivedWithData.Part.Htlc.V1(it)
                }
                is IncomingPayment.ReceivedWith.NewChannel -> {
                    IncomingReceivedWithData.Part.NewChannel.V1(it)
                }
            }
        }.takeIf { it.isNotEmpty() }?.toSet()?.let {
            IncomingReceivedWithTypeVersion.MULTIPARTS_V2 to
              IncomingReceivedWithData.Part.cborPolymorphicV1.encodeToByteArray(
                  serializer = SetSerializer(PolymorphicSerializer(IncomingReceivedWithData.Part::class)),
                  value = it
              )
        }
    } else {
        map {
            when (it) {
                is IncomingPayment.ReceivedWith.LightningPayment -> {
                    IncomingReceivedWithData.Part.Htlc.V0(it)
                }
                is IncomingPayment.ReceivedWith.NewChannel -> {
                    IncomingReceivedWithData.Part.NewChannel.V0(it)
                }
            }
        }.takeIf { it.isNotEmpty() }?.toSet()?.let {
            IncomingReceivedWithTypeVersion.MULTIPARTS_V1 to
              IncomingReceivedWithData.Part.jsonPolymorphicV0.encodeToString(
                  serializer = SetSerializer(PolymorphicSerializer(IncomingReceivedWithData.Part::class)),
                  value = it
              ).toByteArray(Charsets.UTF_8)
        }
    }
}
