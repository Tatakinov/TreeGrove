package io.github.tatakinov.treegrove.nostr

import io.github.tatakinov.treegrove.LoadingData
import org.json.JSONException
import org.json.JSONObject

sealed class ReplaceableEvent {
    abstract val createdAt: Long

    data class MetaData(val name: String, val about: String, val picture: String, val nip05: NIP05.Data, override val createdAt: Long): ReplaceableEvent()
    data class Contacts(val list: List<Map<String, String>>, override val createdAt: Long): ReplaceableEvent() {
        object Key {
            const val key = "key"
            const val relay = "relay"
            const val petname = "petname"
        }
    }
    data class ChannelMetaData(val name: String, val about: String, val picture: String, val recommendRelay: String? = null, override val createdAt: Long): ReplaceableEvent()
    data class ChannelList(val list: List<String>, override val createdAt: Long): ReplaceableEvent()

    companion object {
        fun parse(event: Event): ReplaceableEvent? {
            when (event.kind) {
                Kind.Metadata.num -> {
                    return try {
                        val json = JSONObject(event.content)
                        MetaData(name = json.optString("name", "nil"), about = json.optString("about", ""),
                            picture = json.optString("picture", ""),
                            nip05 = NIP05.Data(json.optString("nip05", ""), LoadingData.NotLoading()), createdAt = event.createdAt)
                    } catch (e: JSONException) {
                        null
                    }
                }
                Kind.Contacts.num -> {
                    return try {
                        val list = mutableListOf<Map<String, String>>()
                        for (tag in event.tags) {
                            if (tag.size == 3 && tag[0] == "p") {
                                list.add(mapOf(Contacts.Key.key to tag[1], Contacts.Key.relay to tag[2]))
                            }
                            else if (tag.size == 4 && tag[0] == "p") {
                                list.add(mapOf(Contacts.Key.key to tag[1], Contacts.Key.relay to tag[2], Contacts.Key.petname to tag[3]))
                            }
                        }
                        Contacts(list, event.createdAt)
                    }
                    catch (e: JSONException) {
                        null
                    }
                }
                Kind.ChannelCreation.num -> {
                    return try {
                        val json = JSONObject(event.content)
                        ChannelMetaData(name = json.optString("name", "nil"),
                            about = json.optString("about", ""),
                            picture = json.optString("picture", ""), createdAt = event.createdAt)
                    } catch (e: JSONException) {
                        null
                    }
                }
                Kind.ChannelMetadata.num -> {
                    return try {
                        val recommendRelay: String? = event.tags.filter {
                            it.size >= 3 && it[0] == "e"
                        }.map {it[2]}.firstOrNull()
                        val json = JSONObject(event.content)
                        ChannelMetaData(name = json.optString("name", "nil"),
                            about = json.optString("about", ""),
                            picture = json.optString("picture", ""),
                            recommendRelay = recommendRelay, createdAt = event.createdAt)
                    } catch (e: JSONException) {
                        null
                    }
                }
                Kind.ChannelList.num -> {
                    val list = mutableListOf<String>()
                    for (tag in event.tags) {
                        if (tag.size >= 2) {
                            if (tag[0] == "e") {
                                list.add(tag[1])
                            }
                        }
                    }
                    return ChannelList(list = list, createdAt = event.createdAt)
                }
                else -> {
                    return null
                }
            }
        }
    }
}
