package io.github.tatakinov.treegrove

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.tatakinov.treegrove.connection.RelayConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class UserPreferences(
    val privateKey: String,
    val publicKey: String,
    val relayList: List<RelayConfig>,
    val fetchSize: Long
)

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {
    private val tag = "UserPreferencesRepository"

    object Default {
        const val privateKey = ""
        const val publicKey = ""
        val relayList = listOf<RelayConfig>()
        const val fetchSize: Long = 100
    }

    private object PreferencesKey {
        val PRIVATE_KEY = stringPreferencesKey("private_key")
        val PUBLIC_KEY = stringPreferencesKey("public_key")
        val RELAY_LIST = stringPreferencesKey("relay_list")
        val FETCH_SIZE = longPreferencesKey("fetch_size")
    }

    val privateKeyFlow: Flow<String> = dataStore.data.catch {
            exception ->
        if (exception is IOException) {
            Log.e(tag, "read error")
        }
    }.map {
            preferences ->
        preferences[PreferencesKey.PRIVATE_KEY] ?: Default.privateKey
    }

    val publicKeyFlow: Flow<String> = dataStore.data.catch {
            exception ->
        if (exception is IOException) {
            Log.e(tag, "read error")
        }
    }.map {
            preferences ->
        preferences[PreferencesKey.PUBLIC_KEY] ?: Default.publicKey
    }

    val relayConfigListFlow: Flow<List<RelayConfig>> = dataStore.data.catch {
            exception ->
        if (exception is IOException) {
            Log.e(tag, "read error")
        }
    }.map {
            preferences ->
        val jsonArray = JSONArray(preferences[PreferencesKey.RELAY_LIST] ?: "[]")
        val relayConfigList = mutableListOf<RelayConfig>()
        for (i in 0..jsonArray.length() - 1) {
            val json = jsonArray.getJSONObject(i)
            relayConfigList.add(RelayConfig(json.getString("url"), json.getBoolean("read"), json.getBoolean("write")))
        }
        relayConfigList
    }
    val fetchSizeFlow: Flow<Long> = dataStore.data.catch {
            exception ->
        if (exception is IOException) {
            Log.e(tag, "read error")
        }
    }.map {
            preferences ->
        preferences[PreferencesKey.FETCH_SIZE] ?: Default.fetchSize
    }

    suspend fun updatePrivateKey(key: String) {
        dataStore.edit {
                preferences ->
            preferences[PreferencesKey.PRIVATE_KEY] = key
        }
    }

    suspend fun updatePublicKey(key: String) {
        dataStore.edit {
                preferences ->
            preferences[PreferencesKey.PUBLIC_KEY] = key
        }
    }

    suspend fun updateRelayList(relayConfigList: List<RelayConfig>) {
        val jsonArray = JSONArray()
        for (relay in relayConfigList) {
            val json = JSONObject()
            json.put("url", relay.url)
            json.put("read", relay.read)
            json.put("write", relay.write)
            jsonArray.put(json)
        }
        dataStore.edit {
            preferences ->
            preferences[PreferencesKey.RELAY_LIST] = jsonArray.toString()
        }
    }

    suspend fun updateFetchSize(fetchSize: Long) {
        dataStore.edit {
            preferences ->
            preferences[PreferencesKey.FETCH_SIZE] = fetchSize
        }
    }
}