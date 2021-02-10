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

package fr.acinq.phoenix.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.acinq.bitcoin.ByteVector
import fr.acinq.phoenix.android.security.EncryptedSeed
import fr.acinq.secp256k1.Hex
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import org.kodein.memory.text.toHexString
import java.io.File
import java.lang.RuntimeException

sealed class KeyState {
    fun isReady() = this is Present

    object Unknown : KeyState()
    object Absent : KeyState()
    object Writing : KeyState()
    data class Present(internal val encryptedSeed: EncryptedSeed.V2) : KeyState()
    sealed class Error : KeyState() {
        object Unreadable : Error()
        object UnhandledSeedType : Error()
    }
}

class SeedViewModel(val context: Context) : ViewModel() {
    val log: Logger = newLogger(LoggerFactory.default)
    var keyState: KeyState by mutableStateOf(KeyState.Unknown)
        private set

    init {
        refreshSeed()
    }

    private fun refreshSeed() {
        keyState = try {
            when (val seed = SeedManager.loadSeedFromDisk(context)) {
                null -> KeyState.Absent
                is EncryptedSeed.V2.NoAuth -> KeyState.Present(seed)
                else -> KeyState.Error.UnhandledSeedType
            }
        } catch (e: Exception) {
            KeyState.Error.Unreadable
        }
    }

    fun writeSeed(context: Context, mnemonics: List<String>) {
        try {
            val encrypted = EncryptedSeed.V2.NoAuth.encrypt(EncryptedSeed.fromMnemonics(mnemonics))
            SeedManager.writeSeedToDisk(context, encrypted)
            refreshSeed()
            log.info { "seed has been written to disk" }
        } catch (e: Exception) {
            log.error(e) { "failed to create new wallet: " }
        }
    }

    fun decryptSeed(): ByteArray? {
        return try {
            when (val seed = SeedManager.loadSeedFromDisk(context)) {
                is EncryptedSeed.V2.NoAuth -> seed.decrypt()
                else -> throw RuntimeException("no seed sorry")
            }
        } catch (e: Exception) {
            log.error(e) { "could not decrypt seed" }
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        log.info { "SeedViewModel cleared" }
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SeedViewModel(context) as T
        }
    }
}

object SeedManager {
    private val BASE_DATADIR = "node-data"
    private const val SEED_FILE = "seed.dat"
    private val log = newLogger(LoggerFactory.default)

    fun getDatadir(context: Context): File {
        return File(context.filesDir, BASE_DATADIR)
    }

    /** Extract the encrypted seed from app private dir. */
    fun loadSeedFromDisk(context: Context): EncryptedSeed? = loadSeedFromDir(getDatadir(context), SEED_FILE)

    /** Extract an encrypted seed contained in a given file/folder. */
    private fun loadSeedFromDir(dir: File, seedFileName: String): EncryptedSeed? {
        val seedFile = File(dir, seedFileName)
        return if (!seedFile.exists()) {
            null
        } else if (!seedFile.canRead()) {
            throw UnreadableSeed("file is unreadable")
        } else if (!seedFile.isFile) {
            throw UnreadableSeed("not a file")
        } else {
            seedFile.readBytes()
                .run {
                    log.info { "read decoded seed=$this" }
                    EncryptedSeed.deserialize(this)
                }
        }
    }

    fun writeSeedToDisk(context: Context, seed: EncryptedSeed.V2) = writeSeedToDir(getDatadir(context), seed)

    private fun writeSeedToDir(dir: File, seed: EncryptedSeed.V2) {
        // 1 - create dir
        if (!dir.exists()) {
            dir.mkdirs()
        }

        // 2 - encrypt and write in a temporary file
        val temp = File(dir, "temporary_seed.dat")
        temp.writeBytes(seed.serialize())

        // 3 - decrypt temp file and check validity; if correct, move temp file to final file
        val checkSeed = loadSeedFromDir(dir, temp.name) as EncryptedSeed.V2
        if (!checkSeed.ciphertext.contentEquals(seed.ciphertext)) {
            log.warning { "seed check do not match!" }
//            throw WriteErrorCheckDontMatch
        }
        temp.copyTo(File(dir, SEED_FILE))
        temp.delete()
    }

    object WriteErrorCheckDontMatch : RuntimeException("failed to write the seed to disk: temporary file do not match")
    class UnreadableSeed(msg: String) : RuntimeException(msg)
}