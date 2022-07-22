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

import fr.acinq.lightning.db.IncomingPayment
import fr.acinq.lightning.payment.PaymentRequest
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json


enum class IncomingOriginTypeVersion {
    KEYSEND_V0, // encoded as empty ByteArray
    INVOICE_V0, // encoded using JSON
    INVOICE_V1, // encoded using CBOR
    SWAPIN_V0,  // encoded using JSON
    SWAPIN_V1   // encoded using CBOR
}

sealed class IncomingOriginData {

    sealed class KeySend : IncomingOriginData() {
        @Serializable
        @SerialName("KEYSEND_V0")
        object V0 : KeySend()
    }

    sealed class Invoice : IncomingOriginData() {
        @Serializable
        data class V0(val paymentRequest: String) : Invoice()
    }

    sealed class SwapIn : IncomingOriginData() {
        @Serializable
        data class V0(val address: String?) : SwapIn()
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(
            typeVersion: IncomingOriginTypeVersion,
            blob: ByteArray
        ): IncomingPayment.Origin {
            return when (typeVersion) {
                IncomingOriginTypeVersion.KEYSEND_V0 -> {
                    IncomingPayment.Origin.KeySend
                }
                IncomingOriginTypeVersion.INVOICE_V0 -> {
                    Json.decodeFromString<Invoice.V0>(
                        String(bytes = blob, charset = Charsets.UTF_8)
                    ).let {
                        IncomingPayment.Origin.Invoice(PaymentRequest.read(it.paymentRequest))
                    }
                }
                IncomingOriginTypeVersion.INVOICE_V1 -> {
                    Cbor.decodeFromByteArray<Invoice.V0>(blob).let {
                        IncomingPayment.Origin.Invoice(PaymentRequest.read(it.paymentRequest))
                    }
                }
                IncomingOriginTypeVersion.SWAPIN_V0 -> {
                    Json.decodeFromString<SwapIn.V0>(
                        String(bytes = blob, charset = Charsets.UTF_8)
                    ).let {
                        IncomingPayment.Origin.SwapIn(it.address)
                    }
                }
                IncomingOriginTypeVersion.SWAPIN_V1 -> {
                    Cbor.decodeFromByteArray<SwapIn.V0>(blob).let {
                        IncomingPayment.Origin.SwapIn(it.address)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun IncomingPayment.Origin.mapToDb(
    useCbor: Boolean = false
): Pair<IncomingOriginTypeVersion, ByteArray> = when (this) {
    is IncomingPayment.Origin.KeySend -> {
        IncomingOriginTypeVersion.KEYSEND_V0 to
          ByteArray(size = 0)
    }
    is IncomingPayment.Origin.Invoice -> {
        val wrapper = IncomingOriginData.Invoice.V0(paymentRequest.write())
        if (useCbor) {
            IncomingOriginTypeVersion.INVOICE_V1 to
              Cbor.encodeToByteArray(wrapper)
        } else {
            IncomingOriginTypeVersion.INVOICE_V0 to
              Json.encodeToString(wrapper).toByteArray(Charsets.UTF_8)
        }
    }
    is IncomingPayment.Origin.SwapIn -> {
        val wrapper = IncomingOriginData.SwapIn.V0(address)
        if (useCbor) {
            IncomingOriginTypeVersion.SWAPIN_V1 to
              Cbor.encodeToByteArray(wrapper)
        } else {
            IncomingOriginTypeVersion.SWAPIN_V0 to
              Json.encodeToString(wrapper).toByteArray(Charsets.UTF_8)
        }
    }
}
