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

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DbTypesHelper {

    /**
     * Unknown keys are ignored, because serialized sealed class contain a type field.
     * However, the data blob deserializer only knows the data class fields, and doesn't care for the type,
     * so it should ignore unknown keys.
     */
    val typeFormat = Json { ignoreUnknownKeys = true }

    /** Transform a serializable object into a ByteArray for database storage. */
    inline fun <reified T> any2blob(value: T): ByteArray {
        return typeFormat.encodeToString(value).toByteArray(Charsets.UTF_8)
    }

    /** Decode a byte array and apply a deserialization handler. */
    fun <T> decodeBlob(blob: ByteArray, handler: (String, Json) -> T) = handler(String(bytes = blob, charset = Charsets.UTF_8), typeFormat)
}