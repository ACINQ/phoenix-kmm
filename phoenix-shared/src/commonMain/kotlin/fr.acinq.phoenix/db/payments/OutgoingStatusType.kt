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
import fr.acinq.eclair.db.ChannelClosingType
import fr.acinq.eclair.db.OutgoingPayment
import fr.acinq.eclair.payment.FinalFailure
import fr.acinq.eclair.serialization.ByteVector32KSerializer
import fr.acinq.eclair.serialization.SatoshiKSerializer
import fr.acinq.phoenix.db.payments.DbTypesHelper.decodeBlob
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

enum class OutgoingStatusTypeVersion {
    SUCCEEDED_OFFCHAIN_V0,
    SUCCEEDED_ONCHAIN_V0,
    FAILED_V0,
}

@Serializable
sealed class OutgoingStatusData {

    sealed class SucceededOffChain : OutgoingStatusData() {
        @Serializable
        data class V0(@Serializable(with = ByteVector32KSerializer::class) val preimage: ByteVector32) : SucceededOffChain()
    }

    sealed class SucceededOnChain : OutgoingStatusData() {
        @Serializable
        data class V0(
            val txIds: List<@Serializable(with = ByteVector32KSerializer::class) ByteVector32>,
            @Serializable(with = SatoshiKSerializer::class) val claimed: Satoshi,
            val closingType: ChannelClosingType
        ) : SucceededOnChain()
    }

    sealed class Failed : OutgoingStatusData() {
        @Serializable
        data class V0(val reason: FinalFailure) : Failed()
    }

    companion object {
        fun deserialize(typeVersion: OutgoingStatusTypeVersion, blob: ByteArray, completedAt: Long): OutgoingPayment.Status = decodeBlob(blob) { json, format ->
            when (typeVersion) {
                OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 -> format.decodeFromString<SucceededOffChain.V0>(json).let {
                    OutgoingPayment.Status.Completed.Succeeded.OffChain(it.preimage, completedAt)
                }
                OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0 -> format.decodeFromString<SucceededOnChain.V0>(json).let {
                    OutgoingPayment.Status.Completed.Succeeded.OnChain(it.txIds, it.claimed, it.closingType, completedAt)
                }
                OutgoingStatusTypeVersion.FAILED_V0 -> format.decodeFromString<Failed.V0>(json).let {
                    OutgoingPayment.Status.Completed.Failed(it.reason, completedAt)
                }
            }
        }
    }
}

fun OutgoingPayment.Status.Completed.mapToDb(): Pair<OutgoingStatusTypeVersion, OutgoingStatusData> = when (this) {
    is OutgoingPayment.Status.Completed.Succeeded.OffChain -> OutgoingStatusTypeVersion.SUCCEEDED_OFFCHAIN_V0 to OutgoingStatusData.SucceededOffChain.V0(preimage)
    is OutgoingPayment.Status.Completed.Succeeded.OnChain -> OutgoingStatusTypeVersion.SUCCEEDED_ONCHAIN_V0 to OutgoingStatusData.SucceededOnChain.V0(txids, claimed, closingType)
    is OutgoingPayment.Status.Completed.Failed -> OutgoingStatusTypeVersion.FAILED_V0 to OutgoingStatusData.Failed.V0(reason)
}
