package io.github.tatakinov.treegrove.nostr

import android.util.Base64
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class NIP04 {
    companion object {
        fun encrypt(privateKey: ByteArray, publicKey: ByteArray, content: String): String {
            val key = Secp256k1.ecdh(privateKey, Hex.decode("02${Hex.encode(publicKey)}"))
            val normalizedKey = Secp256k1.signatureNormalize(key)
            val cryptoKey = SecretKeySpec(normalizedKey.first, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, cryptoKey)
            val cipherText = cipher.doFinal(content.toByteArray())
            val ctb64 = Base64.encode(cipherText, Base64.NO_WRAP)
            val ivb64 = Base64.encode(cipher.iv, Base64.NO_WRAP)
            return "${ctb64}?iv=${ivb64}"
        }

        fun decrypt(privateKey: ByteArray, publicKey: ByteArray, content: String): String {
            val (ctb64, ivb64) = content.split("?iv=")
            val cipherText = Base64.decode(ctb64, Base64.NO_WRAP)
            val iv = IvParameterSpec(Base64.decode(ivb64, Base64.NO_WRAP))
            val key = Secp256k1.ecdh(privateKey, Hex.decode("02${publicKey}"))
            val normalizedKey = Secp256k1.signatureNormalize(key)
            val cryptoKey = SecretKeySpec(normalizedKey.first, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, cryptoKey, iv)
            val text = cipher.doFinal(cipherText)
            return "$text"
        }
    }
}