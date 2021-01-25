package fr.acinq.phoenix.utils

import kotlinx.cinterop.*
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256


actual fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
    val result = ByteArray(CC_SHA256_DIGEST_LENGTH)
    result.usePinned { pinnedResult ->
        key.usePinned { pinnedKey ->
            message.usePinned { pinnedMessage ->
                CCHmac(
                    kCCHmacAlgSHA256,
                    pinnedKey.addressOf(0), key.size.convert(),
                    pinnedMessage.addressOf(0), message.size.convert(),
                    pinnedResult.addressOf(0)
                )
            }
        }
    }
    return result
}
