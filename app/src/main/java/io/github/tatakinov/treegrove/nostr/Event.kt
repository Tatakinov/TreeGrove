package io.github.tatakinov.treegrove.nostr

import com.fasterxml.jackson.databind.ObjectMapper
import fr.acinq.secp256k1.Hex
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class Event(val kind : Int, val content : String, val createdAt : Long, val pubkey : String, val tags : List<List<String>> = listOf(), var id : String = "", var sig : String = "") {

    fun verify() : Boolean {
        val s   = Hex.decode(sig)
        val p   = Hex.decode(pubkey)
        val c   = Hex.decode(id)
        if (s.size != 64 || p.size != 32 || c.size != 32) {
            return false
        }
        return Signature.verify(s, p, c)
    }

    private fun serialize(escapeSlash: Boolean) : String {
        if (escapeSlash) {
            val obj = JSONArray()
            obj.put(0)
            obj.put(pubkey)
            obj.put(createdAt)
            obj.put(kind)
            val array = JSONArray()
            for (tag in tags) {
                val a = JSONArray()
                for (v in tag) {
                    a.put(v)
                }
                array.put(a)
            }
            obj.put(array)
            obj.put(content)
            return obj.toString()
        }
        else {
            val mapper = ObjectMapper()
            val node = mapper.createArrayNode()
            node.add(0)
            node.add(pubkey)
            node.add(createdAt)
            node.add(kind)
            val tagsNode = mapper.createArrayNode()
            for (tag in tags) {
                val tagNode = mapper.createArrayNode()
                for (v in tag) {
                    tagNode.add(v)
                }
                tagsNode.add(tagNode)
            }
            node.add(tagsNode)
            node.add(content)
            return node.toString()
        }
    }

    fun toJSONObject() : JSONObject {
        val obj = JSONObject()
        if (id.isNotEmpty()) {
            obj.put(ID, id)
        }
        obj.put(PUBKEY, pubkey)
        obj.put(CREATED_AT, createdAt)
        obj.put(KIND, kind)
        val array = JSONArray()
        for (tag in tags) {
            val a = JSONArray()
            for (v in tag) {
                a.put(v)
            }
            array.put(a)
        }
        obj.put(TAGS, array)
        obj.put(CONTENT, content)
        if (sig.isNotEmpty()) {
            obj.put(SIG, sig)
        }
        return obj
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Event) {
            toJSONObject().toString() == other.toJSONObject().toString()
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return toJSONObject().toString().hashCode()
    }

    companion object {
        const val KIND = "kind"
        const val CONTENT = "content"
        const val CREATED_AT = "created_at"
        const val PUBKEY = "pubkey"
        const val TAGS = "tags"
        const val ID = "id"
        const val SIG = "sig"

        fun parse(json : JSONObject): Event {
            val kind = json.getInt(KIND)
            val content = json.getString(CONTENT)
            val createdAt = json.getLong(CREATED_AT)
            val pubkey = json.getString(PUBKEY)
            val array = json.optJSONArray(TAGS) ?: JSONArray()
            val tags: MutableList<List<String>> = mutableListOf()
            for (i in 0 until array.length()) {
                val v = array.getJSONArray(i)
                val list = mutableListOf<String>()
                for (j in 0 until v.length()) {
                    list.add(v.getString(j))
                }
                tags.add(list)
            }
            val id  = json.getString(ID)
            val sig = json.getString(SIG)
            return Event(kind = kind, content = content, createdAt = createdAt, pubkey = pubkey, tags = tags, id = id, sig = sig)
        }

        fun generateHash(event : Event, escapeSlash : Boolean) : String {
            val eventHash   = MessageDigest.getInstance("SHA256").digest(event.serialize(escapeSlash).toByteArray())
            return Hex.encode(eventHash)
        }

        fun sign(event : Event, privateKey : String) : String {
            return Hex.encode(Signature.sign(Hex.decode(privateKey), Hex.decode(event.id)))
        }

    }
}