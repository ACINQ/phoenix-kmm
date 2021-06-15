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
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import fr.acinq.lightning.utils.*


enum class IncomingReceivedWithTypeVersion {
    @Deprecated("Not used anymore, received-with is now a list of payment parts") NEW_CHANNEL_V0,
    @Deprecated("Not used anymore, received-with is now a list of payment parts") LIGHTNING_PAYMENT_V0,
    MULTIPARTS_V0, // where receivedWith is a set of parts (new channel and htlcs)
}

sealed class IncomingReceivedWithData {

    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    sealed class NewChannel : IncomingReceivedWithData() {
        @Serializable
        data class V0(val fees: MilliSatoshi, @Serializable(with = ByteVector32KSerializer::class) val channelId: ByteVector32?) : NewChannel()
    }

    @Deprecated("Not used anymore, received-with is now a list of payment parts")
    sealed class LightningPayment : IncomingReceivedWithData() {
        @Serializable
        @SerialName("LIGHTNING_PAYMENT_V0")
        object V0 : LightningPayment()
    }

    @Serializable
    sealed class Part : IncomingReceivedWithData() {
        sealed class Htlc : Part() {
            @Serializable
            data class V0(val amount: MilliSatoshi, @Serializable(with = ByteVector32KSerializer::class) val channelId: ByteVector32, val htlcId: Long) : Htlc()
        }
        sealed class NewChannel : Part() {
            @Serializable
            data class V0(val amount: MilliSatoshi, val fees: MilliSatoshi, @Serializable(with = ByteVector32KSerializer::class) val channelId: ByteVector32?) : NewChannel()
        }
    }

    companion object {
        fun deserialize(typeVersion: IncomingReceivedWithTypeVersion, blob: ByteArray): Set<IncomingPayment.ReceivedWith> = DbTypesHelper.decodeBlob(blob) { json, format ->
            when (typeVersion) {
                IncomingReceivedWithTypeVersion.LIGHTNING_PAYMENT_V0 -> setOf(IncomingPayment.ReceivedWith.LightningPayment(0.msat, ByteVector32.Zeroes, 1L))
                IncomingReceivedWithTypeVersion.NEW_CHANNEL_V0 -> setOf(format.decodeFromString<NewChannel.V0>(json).let { IncomingPayment.ReceivedWith.NewChannel(0.msat, it.fees, it.channelId) })
                IncomingReceivedWithTypeVersion.MULTIPARTS_V0 -> format.decodeFromString<Set<Part>>(json).map {
                    when(it) {
                        is Part.Htlc.V0 -> IncomingPayment.ReceivedWith.LightningPayment(it.amount, ByteVector32.Zeroes, it.htlcId)
                        is Part.NewChannel.V0 -> IncomingPayment.ReceivedWith.NewChannel(it.amount, it.fees, it.channelId)
                    }
                }.toSet()
            }
        }
    }
}

fun Set<IncomingPayment.ReceivedWith>.mapToDb(): Pair<IncomingReceivedWithTypeVersion, ByteArray>? = map {
    when (it) {
        is IncomingPayment.ReceivedWith.LightningPayment -> IncomingReceivedWithData.Part.Htlc.V0(it.amount, it.channelId, it.htlcId)
        is IncomingPayment.ReceivedWith.NewChannel -> IncomingReceivedWithData.Part.NewChannel.V0(it.amount, it.fees, it.channelId)
    }
}.takeIf { it.isNotEmpty() }?.run {
    IncomingReceivedWithTypeVersion.MULTIPARTS_V0 to Json.encodeToString(this.toSet()).toByteArray(Charsets.UTF_8)
}
