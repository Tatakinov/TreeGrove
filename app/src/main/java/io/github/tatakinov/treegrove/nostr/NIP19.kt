package io.github.tatakinov.treegrove.nostr

import android.util.Log
import java.lang.Exception

class NIP19 {
    companion object {
        private fun parseTLV(data: ByteArray): HashMap<Byte, ArrayList<ByteArray>> {
            var remain = data
            var result = HashMap<Byte, ArrayList<ByteArray>>()
            while (remain.isNotEmpty()) {
                val t = remain[0]
                val l = remain[1]
                if (l.toInt() == 0) {
                    throw Exception("malformed TLV $t")
                }
                val v = remain.slice(2..2 + l)
                remain.slice(2 + l until remain.size).also { remain = it.toByteArray() }
                if (v.isEmpty()) {
                    throw Exception("not enough data to read on TLV $t")
                }
                if (!result.containsKey(t)) {
                    result[t] = ArrayList<ByteArray>()
                }
                result[t]?.add(v.toByteArray())
            }
            return result
        }

        fun encode(hrp : String, data : ByteArray) : String {
            val data5Bit    = BitConverter.convert(data, 8, 5, true)
            return Bech32.encode(hrp, data5Bit)
        }

        fun decode(bech32: String): Pair<String, ByteArray> {
            val (result, prefix, data5Bit) = Bech32.decode(bech32)
            if (result) {
                val data = BitConverter.convert(data5Bit, 5, 8, false)
                return Pair(prefix, data)
            }
            return Pair("", ByteArray(0))
        }
    }
}