package fr.acinq.phoenix.utils

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


actual fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(message)
}
