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
import fr.acinq.lightning.serialization.v1.ByteVector32KSerializer
import fr.acinq.lightning.utils.toByteVector32
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json

enum class OutgoingPartStatusTypeVersion {
    SUCCEEDED_V0, // encoded using JSON
    SUCCEEDED_V1, // encoded using CBOR
    FAILED_V0,    // encoded using JSON
    FAILED_V1     // encoded using CBOR
}

sealed class OutgoingPartStatusData {

    sealed class Succeeded : OutgoingPartStatusData() {
        @Serializable
        data class V0(
            @Serializable(with = ByteVector32KSerializer::class)
            val preimage: ByteVector32
        ) : Succeeded() {
            constructor(succeeded: OutgoingPayment.Part.Status.Succeeded): this(
                preimage = succeeded.preimage
            )
            fun unwrap(completedAt: Long) = OutgoingPayment.Part.Status.Succeeded(
                preimage = preimage,
                completedAt = completedAt
            )
        }
        @Serializable
        @OptIn(ExperimentalSerializationApi::class)
        data class V1(
            @ByteString
            val preimage: ByteArray
        ) : Succeeded() {
            constructor(succeeded: OutgoingPayment.Part.Status.Succeeded): this(
                preimage = succeeded.preimage.toByteArray()
            )
            fun unwrap(completedAt: Long) = OutgoingPayment.Part.Status.Succeeded(
                preimage = preimage.toByteVector32(),
                completedAt = completedAt
            )
        }
    }

    sealed class Failed : OutgoingPartStatusData() {
        @Serializable
        data class V0(
            val remoteFailureCode: Int?,
            val details: String
        ) : Failed() {
            constructor(failed: OutgoingPayment.Part.Status.Failed): this(
                remoteFailureCode = failed.remoteFailureCode,
                details = failed.details
            )
            fun unwrap(completedAt: Long) = OutgoingPayment.Part.Status.Failed(
                remoteFailureCode = remoteFailureCode,
                details = details,
                completedAt = completedAt
            )
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun deserialize(
            typeVersion: OutgoingPartStatusTypeVersion,
            blob: ByteArray,
            completedAt: Long
        ): OutgoingPayment.Part.Status {
            val jsonStr = { String(bytes = blob, charset = Charsets.UTF_8) }
            return when (typeVersion) {
                OutgoingPartStatusTypeVersion.SUCCEEDED_V0 -> {
                    Json.decodeFromString<Succeeded.V0>(jsonStr()).unwrap(completedAt)
                }
                OutgoingPartStatusTypeVersion.SUCCEEDED_V1 -> {
                    Cbor.decodeFromByteArray<Succeeded.V1>(blob).unwrap(completedAt)
                }
                OutgoingPartStatusTypeVersion.FAILED_V0 -> {
                    Json.decodeFromString<Failed.V0>(jsonStr()).unwrap(completedAt)
                }
                OutgoingPartStatusTypeVersion.FAILED_V1 -> { // reusing V0 wrapper
                    Cbor.decodeFromByteArray<Failed.V0>(blob).unwrap(completedAt)
                }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun OutgoingPayment.Part.Status.Succeeded.mapToDb(
    useCbor: Boolean = false
) = if (useCbor) {
    OutgoingPartStatusTypeVersion.SUCCEEDED_V1 to Cbor.encodeToByteArray(
        OutgoingPartStatusData.Succeeded.V1(this)
    )
} else {
    OutgoingPartStatusTypeVersion.SUCCEEDED_V0 to Json.encodeToString(
        OutgoingPartStatusData.Succeeded.V0(this)
    ).toByteArray(Charsets.UTF_8)
}

@OptIn(ExperimentalSerializationApi::class)
fun OutgoingPayment.Part.Status.Failed.mapToDb(
    useCbor: Boolean = false
) = if (useCbor) {
    OutgoingPartStatusTypeVersion.FAILED_V1 to Cbor.encodeToByteArray(
        OutgoingPartStatusData.Failed.V0(this) // reusing V0 wrapper
    )
} else {
    OutgoingPartStatusTypeVersion.FAILED_V0 to Json.encodeToString(
        OutgoingPartStatusData.Failed.V0(this)
    ).toByteArray(Charsets.UTF_8)
}
