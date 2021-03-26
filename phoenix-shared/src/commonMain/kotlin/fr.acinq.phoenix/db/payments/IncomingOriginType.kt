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

import fr.acinq.eclair.db.IncomingPayment
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.db.payments.DbTypesHelper.decodeBlob
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString


enum class IncomingOriginTypeVersion {
    KEYSEND_V0,
    INVOICE_V0,
    SWAPIN_V0
}

@Serializable
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
        fun deserialize(typeVersion: IncomingOriginTypeVersion, blob: ByteArray): IncomingPayment.Origin = decodeBlob(blob) { json, format ->
            when (typeVersion) {
                IncomingOriginTypeVersion.KEYSEND_V0 -> IncomingPayment.Origin.KeySend
                IncomingOriginTypeVersion.INVOICE_V0 -> format.decodeFromString<Invoice.V0>(json).let { IncomingPayment.Origin.Invoice(PaymentRequest.read(it.paymentRequest)) }
                IncomingOriginTypeVersion.SWAPIN_V0 -> format.decodeFromString<SwapIn.V0>(json).let { IncomingPayment.Origin.SwapIn(it.address) }
            }
        }
    }
}

fun IncomingPayment.Origin.mapToDb() = when (this) {
    is IncomingPayment.Origin.KeySend -> IncomingOriginTypeVersion.KEYSEND_V0 to IncomingOriginData.KeySend.V0
    is IncomingPayment.Origin.Invoice -> IncomingOriginTypeVersion.INVOICE_V0 to IncomingOriginData.Invoice.V0(paymentRequest.write())
    is IncomingPayment.Origin.SwapIn -> IncomingOriginTypeVersion.SWAPIN_V0 to IncomingOriginData.SwapIn.V0(address)
}
