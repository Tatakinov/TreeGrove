package io.github.tatakinov.treegrove.nostr

import android.util.Log

object Bech32 {
    private val hexToBech32  = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val bech32ToHex  = mapOf<String, Byte>(
        "q" to 0x00, "p" to 0x01, "z" to 0x02, "r" to 0x03,
        "y" to 0x04, "9" to 0x05, "x" to 0x06, "8" to 0x07,
        "g" to 0x08, "f" to 0x09, "2" to 0x0a, "t" to 0x0b,
        "v" to 0x0c, "d" to 0x0d, "w" to 0x0e, "0" to 0x0f,
        "s" to 0x10, "3" to 0x11, "j" to 0x12, "n" to 0x13,
        "5" to 0x14, "4" to 0x15, "k" to 0x16, "h" to 0x17,
        "c" to 0x18, "e" to 0x19, "6" to 0x1a, "m" to 0x1b,
        "u" to 0x1c, "a" to 0x1d, "7" to 0x1e, "l" to 0x1f
    )
    fun encode(prefix: String, data: ByteArray): String {
        val hrp = prefix.toByteArray()
        val checksum    = checksum(hrp, data)
        var result  = prefix + "1"
        for (v in data + checksum) {
            result  += hexToBech32[v.toInt()]
        }
        return result
    }

    fun decode(bech32: String): Triple<Boolean, String, ByteArray> {
        val pos = bech32.indexOfLast { it.code == 0x31 }
        if (pos < 1 || pos > 83) {
            Log.d("Bech32.decode", "failed to indexOfLast")
            return Triple(false, "", ByteArray(0))
        }
        val hrp = bech32.substring(0, pos)
        val remain  = bech32.substring(pos + 1)
        val data    = ByteArray(remain.length)
        for (i in remain.indices) {
            data[i] = bech32ToHex[remain.substring(i, i + 1)]?.toByte() ?:
                return Triple(false, "", ByteArray(0))
        }
        if ( ! verify(hrp.toByteArray(), data)) {
            Log.d("Bech32.decode", "failed to verify")
            return Triple(false, "", ByteArray(0))
        }
        val bytes   = ByteArray(data.size - 6)
        for (i in 0 .. data.size - 7) {
            bytes[i]    = data[i]
        }
        return Triple(true, hrp, bytes)
    }

    private fun expand(prefix : ByteArray) : ByteArray {
        val result = ByteArray(prefix.size * 2 + 1)
        for (i in prefix.indices) {
            result[i]   = (prefix[i].toInt() shr 5).toByte()
            result[i + prefix.size + 1] = (prefix[i].toInt() and 0x1f).toByte()
        }
        result[prefix.size] = 0
        return result
    }

    private fun polymod(data : ByteArray) : Int {
        val gen = listOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in data) {
            val n   = v.toInt()
            val b   = (chk shr 25)
            chk = ((chk and 0x1ffffff) shl 5) xor n
            for (i in 0 .. 4) {
                if (((b shr i) and 1) > 0) {
                    chk = chk xor (gen[i])
                }
            }
        }
        return chk
    }

    private fun verify(prefix : ByteArray, data : ByteArray) : Boolean {
        return polymod(expand(prefix) + data) == 1
    }

    private fun checksum(hrp : ByteArray, data : ByteArray) : ByteArray {
        val values  = expand(hrp) + data
        val buffer : ByteArray  = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val polymod = polymod(values + buffer) xor 1
        val result  = ByteArray(6)
        for (i in 0 .. 5) {
            result[i]   = ((polymod shr (5 * (5 - i))) and 0x1f).toByte()
        }
        return result
    }
}