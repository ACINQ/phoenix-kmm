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
import fr.acinq.lightning.db.OutgoingPayment
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.serialization.v1.ByteVector32KSerializer
import fr.acinq.lightning.utils.toByteVector32
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json


enum class OutgoingDetailsTypeVersion {
    NORMAL_V0,  // encoded using JSON
    NORMAL_V1,  // encoded using CBOR
    KEYSEND_V0, // encoded using JSON
    KEYSEND_V1, // encoded using CBOR
    SWAPOUT_V0, // encoded using JSON
    SWAPOUT_V1, // encoded using CBOR
    CLOSING_V0, // encoded using JSON
    CLOSING_V1, // encoded using CBOR
}

sealed class OutgoingDetailsData {

    sealed class Normal : OutgoingDetailsData() {
        @Serializable
        data class V0(
            val paymentRequest: String
        ) : Normal() {
            constructor(normal: OutgoingPayment.Details.Normal): this(
                paymentRequest = normal.paymentRequest.write()
            )
            fun unwrap() = OutgoingPayment.Details.Normal(
                paymentRequest = PaymentRequest.read(this.paymentRequest)
            )
        }
    }

    sealed class KeySend : OutgoingDetailsData() {
        @Serializable
        data class V0(
            @Serializable(with = ByteVector32KSerializer::class)
            val preimage: ByteVector32
        ) : KeySend() {
            constructor(keySend: OutgoingPayment.Details.KeySend) : this(
                preimage = keySend.preimage
            )

            fun unwrap() = OutgoingPayment.Details.KeySend(
                preimage = preimage
            )
        }
        @Serializable
        @OptIn(ExperimentalSerializationApi::class)
        data class V1(
            @ByteString
            val preimage: ByteArray
        ) : KeySend() {
            constructor(keySend: OutgoingPayment.Details.KeySend) : this(
                preimage = keySend.preimage.toByteArray()
            )

            fun unwrap() = OutgoingPayment.Details.KeySend(
                preimage = preimage.toByteVector32()
            )
        }
    }

    sealed class SwapOut : OutgoingDetailsData() {
        @Serializable
        data class V0(
            val address: String,
            @Serializable(with = ByteVector32KSerializer::class)
            val paymentHash: ByteVector32
        ) : SwapOut() {
            constructor(swapOut: OutgoingPayment.Details.SwapOut): this(
                address = swapOut.address,
                paymentHash = swapOut.paymentHash
            )
            fun unwrap() = OutgoingPayment.Details.SwapOut(
                address = address,
                paymentHash = paymentHash
            )
        }
        @Serializable
        @OptIn(ExperimentalSerializationApi::class)
        data class V1(
            val address: String,
            @ByteString
            val paymentHash: ByteArray
        ) : SwapOut() {
            constructor(swapOut: OutgoingPayment.Details.SwapOut): this(
                address = swapOut.address,
                paymentHash = swapOut.paymentHash.toByteArray()
            )
            fun unwrap() = OutgoingPayment.Details.SwapOut(
                address = address,
                paymentHash = paymentHash.toByteVector32()
            )
        }
    }

    sealed class Closing : OutgoingDetailsData() {
        @Serializable
        data class V0(
            @Serializable(with = ByteVector32KSerializer::class)
            val channelId: ByteVector32,
            val closingAddress: String,
            val isSentToDefaultAddress: Boolean
        ) : Closing() {
            constructor(channelClosing: OutgoingPayment.Details.ChannelClosing): this(
                channelId = channelClosing.channelId,
                closingAddress = channelClosing.closingAddress,
                isSentToDefaultAddress = channelClosing.isSentToDefaultAddress
            )
            fun unwrap() = OutgoingPayment.Details.ChannelClosing(
                channelId = channelId,
                closingAddress = closingAddress,
                isSentToDefaultAddress = isSentToDefaultAddress
            )
        }
        @Serializable
        @OptIn(ExperimentalSerializationApi::class)
        data class V1(
            @ByteString
            val channelId: ByteArray,
            val closingAddress: String,
            val isSentToDefaultAddress: Boolean
        ) : Closing() {
            constructor(channelClosing: OutgoingPayment.Details.ChannelClosing): this(
                channelId = channelClosing.channelId.toByteArray(),
                closingAddress = channelClosing.closingAddress,
                isSentToDefaultAddress = channelClosing.isSentToDefaultAddress
            )
            fun unwrap() = OutgoingPayment.Details.ChannelClosing(
                channelId = channelId.toByteVector32(),
                closingAddress = closingAddress,
                isSentToDefaultAddress = isSentToDefaultAddress
            )
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(
            typeVersion: OutgoingDetailsTypeVersion,
            blob: ByteArray
        ): OutgoingPayment.Details {
            val jsonStr = { String(bytes = blob, charset = Charsets.UTF_8) }
            return when (typeVersion) {
                OutgoingDetailsTypeVersion.NORMAL_V0 -> {
                    Json.decodeFromString<Normal.V0>(jsonStr()).unwrap()
                }
                OutgoingDetailsTypeVersion.NORMAL_V1 -> { // reusing V0 wrapper
                    Cbor.decodeFromByteArray<Normal.V0>(blob).unwrap()
                }
                OutgoingDetailsTypeVersion.KEYSEND_V0 -> {
                    Json.decodeFromString<KeySend.V0>(jsonStr()).unwrap()
                }
                OutgoingDetailsTypeVersion.KEYSEND_V1 -> {
                    Cbor.decodeFromByteArray<KeySend.V1>(blob).unwrap()
                }
                OutgoingDetailsTypeVersion.SWAPOUT_V0 -> {
                    Json.decodeFromString<SwapOut.V0>(jsonStr()).unwrap()
                }
                OutgoingDetailsTypeVersion.SWAPOUT_V1 -> {
                    Cbor.decodeFromByteArray<SwapOut.V1>(blob).unwrap()
                }
                OutgoingDetailsTypeVersion.CLOSING_V0 -> {
                    Json.decodeFromString<Closing.V0>(jsonStr()).unwrap()
                }
                OutgoingDetailsTypeVersion.CLOSING_V1 -> {
                    Cbor.decodeFromByteArray<Closing.V1>(blob).unwrap()
                }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun OutgoingPayment.Details.mapToDb(
    useCbor: Boolean = false
): Pair<OutgoingDetailsTypeVersion, ByteArray> = when (this) {
    is OutgoingPayment.Details.Normal -> {
        if (useCbor) {
            OutgoingDetailsTypeVersion.NORMAL_V1 to Cbor.encodeToByteArray(
                OutgoingDetailsData.Normal.V0(this) // reusing V0 wrapper
            )
        } else {
            OutgoingDetailsTypeVersion.NORMAL_V0 to Json.encodeToString(
                OutgoingDetailsData.Normal.V0(this)
            ).toByteArray(Charsets.UTF_8)
        }
    }
    is OutgoingPayment.Details.KeySend -> {
        if (useCbor) {
            OutgoingDetailsTypeVersion.KEYSEND_V1 to Cbor.encodeToByteArray(
                OutgoingDetailsData.KeySend.V1(this)
            )
        } else {
            OutgoingDetailsTypeVersion.KEYSEND_V0 to Json.encodeToString(
                OutgoingDetailsData.KeySend.V0(this)
            ).toByteArray(Charsets.UTF_8)
        }
    }
    is OutgoingPayment.Details.SwapOut -> {
        if (useCbor) {
            OutgoingDetailsTypeVersion.SWAPOUT_V1 to Cbor.encodeToByteArray(
                OutgoingDetailsData.SwapOut.V1(this)
            )
        } else {
            OutgoingDetailsTypeVersion.SWAPOUT_V0 to Json.encodeToString(
                OutgoingDetailsData.SwapOut.V0(this)
            ).toByteArray(Charsets.UTF_8)
        }
    }
    is OutgoingPayment.Details.ChannelClosing -> {
        if (useCbor) {
            OutgoingDetailsTypeVersion.CLOSING_V1 to Cbor.encodeToByteArray(
                OutgoingDetailsData.Closing.V1(this)
            )
        } else {
            OutgoingDetailsTypeVersion.CLOSING_V0 to Json.encodeToString(
                OutgoingDetailsData.Closing.V0(this)
            ).toByteArray(Charsets.UTF_8)
        }
    }
}
