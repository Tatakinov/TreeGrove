package io.github.tatakinov.treegrove.nostr

import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom

object Signature {
    fun sign(privateKey : ByteArray, content : ByteArray) : ByteArray {
        val srg = SecureRandom()
        val bytes = ByteArray(32)
        srg.nextBytes(bytes)
        return Secp256k1.signSchnorr(content, privateKey, bytes)
    }

    fun verify(signature : ByteArray, publicKey : ByteArray, content : ByteArray) : Boolean {
        return Secp256k1.verifySchnorr(signature, content, publicKey)
    }
}