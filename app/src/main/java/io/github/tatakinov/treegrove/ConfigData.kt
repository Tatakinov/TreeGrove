package io.github.tatakinov.treegrove

import fr.acinq.secp256k1.Hex
import io.github.tatakinov.treegrove.nostr.Keys
import org.json.JSONArray
import org.json.JSONObject

class ConfigData(
    var privateKey : String = "",
    var relayList : List<ConfigRelayData> = ArrayList(),
    var fetchSize : Long = 50,
    var displayProfilePicture : Boolean = true,
    var fetchProfilePictureOnlyWifi : Boolean = true
) {
    private var publicKey   = ""

    fun getPublicKey() : String {
        if (privateKey.isEmpty()) {
            return ""
        }
        if (publicKey.isEmpty()) {
            publicKey   = Hex.encode(Keys.getPublicKey(Hex.decode(privateKey)))
        }
        return publicKey
    }

    fun toJSONObject() : JSONObject {
        val obj = JSONObject()
        obj.put(PRIVATE_KEY, privateKey)
        var array1 = JSONArray()
        for (relay in relayList) {
            var o = JSONObject()
            o.put(URL, relay.url)
            o.put(READ, relay.read)
            o.put(WRITE, relay.write)
            array1.put(o)
        }
        obj.put(RELAY_LIST, array1)
        obj.put(FETCH_SIZE, fetchSize)
        obj.put(DISPLAY_PROFILE_PICTURE, displayProfilePicture)
        obj.put(FETCH_PROFILE_PICTURE_ONLY_WIFI, fetchProfilePictureOnlyWifi)
        return obj
    }

    companion object {
        const val PRIVATE_KEY = "private_key"
        const val URL = "url"
        const val READ = "read"
        const val WRITE = "write"
        const val RELAY_LIST = "relay_list"
        const val FETCH_SIZE = "fetch_size"
        const val DISPLAY_PROFILE_PICTURE = "display_profile_picture"
        const val FETCH_PROFILE_PICTURE_ONLY_WIFI = "fetch_profile_picture_only_wifi"
    }
}