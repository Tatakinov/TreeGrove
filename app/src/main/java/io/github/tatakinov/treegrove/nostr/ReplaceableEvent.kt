package io.github.tatakinov.treegrove.nostr

import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.LoadingDataStatus
import org.json.JSONException
import org.json.JSONObject

sealed class ReplaceableEvent {
    abstract val createdAt: Long

    data class MetaData(val name: String, val about: String, val nip05: NIP05.Data, override val createdAt: Long): ReplaceableEvent()
    data class ChannelMetaData(val name: String, val about: String, override val createdAt: Long): ReplaceableEvent()

    companion object {
        fun parse(event: Event): ReplaceableEvent? {
            when (event.kind) {
                Kind.Metadata.num -> {
                    return try {
                        val json = JSONObject(event.content)
                        MetaData(name = json.optString("name", "nil"), about = json.optString("about", ""),
                            nip05 = NIP05.Data(json.optString("nip05", ""), LoadingData.NotLoading()), createdAt = event.createdAt)
                    } catch (e: JSONException) {
                        null
                    }
                }
                Kind.ChannelCreation.num -> {
                    return try {
                        val json = JSONObject(event.content)
                        ChannelMetaData(name = json.optString("name", "nil"),
                            about = json.optString("about", ""), createdAt = event.createdAt)
                    } catch (e: JSONException) {
                        null
                    }
                }
                Kind.ChannelMetadata.num -> {
                    return try {
                        val json = JSONObject(event.content)
                        ChannelMetaData(name = json.optString("name", "nil"),
                            about = json.optString("about", ""), createdAt = event.createdAt)
                    } catch (e: JSONException) {
                        null
                    }
                }
                else -> {
                    return null
                }
            }
        }
    }
}
