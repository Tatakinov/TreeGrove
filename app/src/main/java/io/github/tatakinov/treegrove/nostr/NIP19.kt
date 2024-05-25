package io.github.tatakinov.treegrove.nostr

import fr.acinq.secp256k1.Hex
import kotlin.Exception

object NIP19 {
    const val NPUB = "npub"
    const val NSEC = "nsec"
    const val NEVENT = "nevent"
    const val NOTE = "note"
    const val NPROFILE = "nprofile"

    sealed class Data {
        data class Sec(val id: String) : Data()
        data class Pub(val id: String) : Data()
        data class Note(val id: String) : Data()
        data class Event(
            val id: String,
            val relays: List<String>,
            val author: String?,
            val kind: Int?
        ) : Data()
        data class Profile(val id: String, val relays: List<String>): Data()
    }

    fun parse(bech32: String): Data? {
        val (hrp, data) = decode(bech32)
        return when (hrp) {
            NSEC -> {
                if (data.size == 32) {
                    Data.Sec(Hex.encode(data))
                } else {
                    null
                }
            }

            NPUB -> {
                if (data.size == 32) {
                    Data.Pub(Hex.encode(data))
                } else {
                    null
                }
            }

            NOTE -> {
                if (data.size == 32) {
                    Data.Note(Hex.encode(data))
                } else {
                    null
                }
            }

            NEVENT -> {
                val tlv = parseTLV(data)
                if (tlv[0] == null) {
                    return null
                }
                if (tlv[0]!!.size == 0) {
                    return null
                }
                val id = Hex.encode(tlv[0]!![0])
                val relays = tlv[1]?.map { String(it) } ?: listOf()
                val author = tlv[2]?.getOrNull(0)?.let {
                    Hex.encode(it)
                }
                val kind = tlv[3]?.getOrNull(0)?.let {
                    Hex.encode(it).toIntOrNull(16)
                }
                Data.Event(id = id, relays = relays, author = author, kind = kind)
            }

            NPROFILE -> {
                val tlv = parseTLV(data)
                if (tlv[0] == null) {
                    return null
                }
                if (tlv[0]!!.size == 0) {
                    return null
                }
                val id = Hex.encode(tlv[0]!![0])
                val relays = tlv[1]?.map { String(it) } ?: listOf()
                Data.Profile(id = id, relays = relays)
            }

            else -> {
                null
            }
        }
    }

    fun parseTLV(data: ByteArray): Map<Byte, MutableList<ByteArray>> {
        var index = 0
        val result = mutableMapOf<Byte, MutableList<ByteArray>>()
        while (index < data.size) {
            val t = data[index + 0]
            val l = data[index + 1]
            if (l.toInt() == 0) {
                throw Exception("malformed TLV $t")
            }
            val v = if (index + 2 + l > data.size) {
                throw Exception("malformed TLV: ${index + 2 + l} > ${data.size}")
            } else {
                data.slice(index + 2 until index + 2 + l)
            }
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

    fun encode(hrp: String, data: ByteArray): String {
        val data5Bit = BitConverter.convert(data, 8, 5, true)
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