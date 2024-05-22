package io.github.tatakinov.treegrove.nostr

import org.json.JSONArray
import org.json.JSONObject

data class Filter(
    val ids : List<String> = listOf(),
    val authors : List<String> = listOf(),
    val kinds : List<Int>   = listOf(),
    val tags : Map<String, List<String>>    = mapOf(),
    val since : Long    = 0,
    val until : Long    = 0,
    val limit : Long    = -1
    ) {
    fun toJSONObject() : JSONObject {
        val obj = JSONObject()
        if (ids.isNotEmpty()) {
            val array   = JSONArray()
            for (id in ids) {
                array.put(id)
            }
            obj.put("ids", array)
        }
        if (authors.isNotEmpty()) {
            val array   = JSONArray()
            for (author in authors) {
                array.put(author)
            }
            obj.put("authors", array)
        }
        if (kinds.isNotEmpty()) {
            val array   = JSONArray()
            for (kind in kinds) {
                array.put(kind)
            }
            obj.put("kinds", array)
        }
        for ((k, list) in tags) {
            val array   = JSONArray()
            for (v in list) {
                array.put(v)
            }
            obj.put("#$k", array)
        }
        if (since > 0) {
            obj.put("since", since)
        }
        if (until > 0) {
            obj.put("until", until)
        }
        if (limit >= 0) {
            obj.put("limit", limit)
        }
        return obj
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Filter) {
            toJSONObject().toString() == other.toJSONObject().toString()
        } else {
            false
        }
    }

    fun cond(e: Event): Boolean {
        if (ids.isNotEmpty()) {
            if (ids.none { it == e.id }) {
                return false
            }
        }
        if (authors.isNotEmpty()) {
            if (authors.none { it == e.pubkey }) {
                return false
            }
        }
        if (kinds.isNotEmpty()) {
            if (kinds.none { it == e.kind }) {
                return false
            }
        }
        if (tags.isNotEmpty()) {
            for ((k, v) in tags) {
                val list = e.tags.filter { it.size >= 2 && it[0] == k }.map { it[1] }
                for (id in v) {
                    if (!list.contains(id)) {
                        return false
                    }
                }
            }
        }
        if (since > 0) {
            if (e.createdAt < since) {
                return false
            }
        }
        if (until > 0) {
            if (e.createdAt > until) {
                return false
            }
        }
        return true
    }
}