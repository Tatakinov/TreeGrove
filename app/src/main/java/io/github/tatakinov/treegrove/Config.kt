package io.github.tatakinov.treegrove

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object Config {
    private const val FILENAME = "config.json"
    private var _isLoaded   = false
    var config  = ConfigData()

    suspend fun load(context: Context, onLoad : (ConfigData) -> Unit)  = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.openFileInput(FILENAME)
            BufferedReader(InputStreamReader(inputStream, "UTF-8"))?.use {
                val data : String = it.readLine() ?: return@withContext ConfigData()
                val obj = JSONObject(data)
                val privateKey = obj.optString(ConfigData.PRIVATE_KEY, "")
                val relayList = ArrayList<ConfigRelayData>()
                val array1 = obj.optJSONArray(ConfigData.RELAY_LIST) ?: JSONArray()
                for (i in 0 until array1.length()) {
                    val o = array1.getJSONObject(i)
                    relayList.add(ConfigRelayData(o.getString(ConfigData.URL), o.getBoolean(ConfigData.READ), o.getBoolean(ConfigData.WRITE)))
                }
                val fetchSize   = obj.optLong(ConfigData.FETCH_SIZE, 10)
                val displayProfilePicture = obj.optBoolean(ConfigData.DISPLAY_PROFILE_PICTURE, true)
                val fetchProfilePictureOnlyWifi = obj.optBoolean(ConfigData.FETCH_PROFILE_PICTURE_ONLY_WIFI, true)
                config  = ConfigData(privateKey = privateKey, relayList = relayList, fetchSize = fetchSize,
                    displayProfilePicture = displayProfilePicture, fetchProfilePictureOnlyWifi = fetchProfilePictureOnlyWifi)
                _isLoaded   = true
                onLoad(config)
            }
        } catch (e: IOException) {
            // 初回起動地
        } catch (e : JSONException) {
            e.printStackTrace()
        }
        _isLoaded   = true
        onLoad(config)
    }

    suspend fun save(context: Context)  = withContext(Dispatchers.IO) {
        val outputStream = context.openFileOutput(FILENAME, Context.MODE_PRIVATE)
        BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use {
            it.write(config.toJSONObject().toString())
        }
    }

    fun isLoaded() : Boolean {
        return _isLoaded
    }
}