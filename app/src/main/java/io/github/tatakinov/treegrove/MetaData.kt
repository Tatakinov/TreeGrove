package io.github.tatakinov.treegrove

import io.github.tatakinov.treegrove.nostr.Event
import org.json.JSONException
import org.json.JSONObject

data class MetaData(val name : String, val about : String = "",
                    val nip05Address : String = "",
                    var nip05 : LoadingDataStatus<Boolean> = LoadingDataStatus()
) {

    override fun equals(other: Any?): Boolean {
        if (other is MetaData) {
            return name == other.name && about == other.about &&
                    nip05Address == other.nip05Address && nip05 == other.nip05
        }
        return false
    }

    companion object {
        const val NAME    = "name"
        const val ABOUT   = "about"
        const val PICTURE = "picture"
        const val NIP05   = "nip05"
        fun parse(event: Event, fallback: Event? = null): MetaData {
            val metaData = try {
                val json = JSONObject(event.content)
                MetaData(name = json.optString(NAME, "nil"),
                    about = json.optString(ABOUT, ""), nip05Address = json.optString(NIP05, ""))
            }
            catch (e: JSONException) {
                if (fallback != null) {
                    parse(fallback)
                }
                else {
                    MetaData(name = "nil")
                }
            }
            return metaData
        }
    }
}