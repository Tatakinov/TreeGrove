package io.github.tatakinov.treegrove.nostr

import fr.acinq.secp256k1.Secp256k1
import java.lang.Exception
import java.security.SecureRandom

class Keys {
    companion object {
        fun generatePrivateKey(): ByteArray {
            val srg = SecureRandom()
            val bytes = ByteArray(32)
            srg.nextBytes(bytes)
            return bytes
        }

        fun getPublicKey(privateKey: ByteArray): ByteArray {
            if (!Secp256k1.secKeyVerify(privateKey)) {
                throw Exception("invalid private key")
            }
            return Secp256k1.pubkeyCreate(privateKey).drop(1).take(32).toByteArray()
        }
    }
}