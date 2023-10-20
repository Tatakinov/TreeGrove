package io.github.tatakinov.treegrove.nostr

import android.util.Log
import java.lang.Exception

class NIP19 {
    companion object {
        fun parseTLV(data: ByteArray): Map<Byte, MutableList<ByteArray>> {
            var index = 0
            val result = mutableMapOf<Byte, MutableList<ByteArray>>()
            while (index < data.size) {
                val t = data[index + 0]
                val l = data[index + 1]
                if (l.toInt() == 0) {
                    throw Exception("malformed TLV $t")
                }
                val v = data.slice(index + 2 until index + 2 + l)
                index += 2 + l
                if (v.isEmpty()) {
                    throw Exception("not enough data to read on TLV $t")
                }
                if (!result.contains(t)) {
                    result[t] = mutableListOf()
                }
                result[t]!!.add(v.toByteArray())
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