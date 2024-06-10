package io.github.tatakinov.treegrove.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.tatakinov.treegrove.Misc
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.UserPreferencesRepository
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.NIP19
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Composable
fun Main() {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val viewModel: TreeGroveViewModel = viewModel {
        TreeGroveViewModel(UserPreferencesRepository(context.dataStore))
    }
    val privateKey by viewModel.privateKeyFlow.collectAsState()
    val publicKey by viewModel.publicKeyFlow.collectAsState()
    val priv = if (privateKey.isNotEmpty()) {
        val n = NIP19.parse(privateKey)
        if (n is NIP19.Data.Sec) {
            n
        }
        else {
            null
        }
    }
    else {
        null
    }
    val pub = if (publicKey.isNotEmpty()) {
        val n = NIP19.parse(publicKey)
        if (n is NIP19.Data.Pub) {
            n
        }
        else {
            null
        }
    }
    else {
        null
    }
    val relayConfigList = viewModel.relayConfigListFlow.collectAsState()
    val fetchSize = viewModel.fetchSizeFlow.collectAsState()
    val relayInfoList = viewModel.relayInfoListFlow.collectAsState()
    val tabList = viewModel.tabList
    val transmittedSize = viewModel.transmittedSizeFlow.collectAsState()
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModel.connectRelay()
        }
    })
    val onConnectRelay = {
        viewModel.connectRelay()
    }
    val onSubscribeStreamEvent = { filter: Filter ->
        viewModel.subscribeStreamEvent(filter)
    }
    val onSubscribeOneShotEvent = { filter: Filter ->
        viewModel.subscribeOneShotEvent(filter)
    }
    val onSubscribeStreamReplaceableEvent = { filter: Filter ->
        viewModel.subscribeStreamReplaceableEvent(filter)
    }
    val onSubscribeReplaceableEvent = { filter: Filter ->
        viewModel.subscribeReplaceableEvent(filter)
    }
    val onRepost = { event: Event ->
        if (priv is NIP19.Data.Sec && pub is NIP19.Data.Pub) {
            Misc.repost(viewModel, event, priv, pub, onSuccess = {}, onFailure = { url, reason ->
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.error_failed_to_post, reason),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }
    val onPost = { kind: Int, content: String, tags: List<List<String>> ->
        if (priv is NIP19.Data.Sec && pub is NIP19.Data.Pub) {
            Misc.post(
                viewModel,
                kind,
                content,
                tags,
                priv,
                pub,
                onSuccess = {},
                onFailure = { url, reason ->
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.error_failed_to_post, reason),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        }
    }
    val onDownload = { url: String ->
        viewModel.fetchImage(url)
    }
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            Home(priv, pub, tabList, relayInfoList, transmittedSize,
                onConnectRelay = onConnectRelay,
                onSubscribeStreamEvent = onSubscribeStreamEvent,
                onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                onSubscribeStreamReplaceableEvent = onSubscribeStreamReplaceableEvent,
                onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                onRepost = onRepost, onPost = onPost,
                onNavigateSetting = {
                navController.navigate("setting")
            }, onNavigatePost = { s, event ->
                val e = URLEncoder.encode(event?.toJSONObject().toString() ?: "", "UTF-8")
                when (s) {
                    is Screen.OwnTimeline -> {
                        navController.navigate("post?class=OwnTimeline&id=${s.id}&event=${e}")
                    }
                    is Screen.Timeline -> {
                        navController.navigate("post?class=Timeline&id=${s.id}&event=${e}")
                    }
                    is Screen.Channel -> {
                        navController.navigate("post?class=Channel&id=${s.id}&pubkey=${s.pubkey}&event=${e}")
                    }
                    is Screen.EventDetail -> {
                        navController.navigate("post?class=EventDetail&id=${s.id}&pubkey=${s.pubkey}&event=${e}")
                    }
                    is Screen.ChannelEventDetail -> {
                        navController.navigate("post?class=ChannelEventDetail&id=${s.id}&pubkey=${s.pubkey}&channel=${s.channelID}&event=${e}")
                    }
                    is Screen.Notification -> {
                        navController.navigate("post?class=Notification&id=${s.id}&event=${e}")
                    }
                }
            }, onNavigateProfile = {
                navController.navigate("profile")
            }, onNavigateImage = { url ->
                navController.navigate("image?url=${URLEncoder.encode(url, "UTF-8")}")
            })
            LaunchedEffect(relayConfigList.value) {
                viewModel.setRelayConfigList(relayConfigList.value)
            }
        }
        composable("setting") {
            Setting(privateKey = privateKey, publicKey = publicKey, relayConfigListState = relayConfigList, fetchSizeState = fetchSize, onNavigate = { preference ->
                coroutineScope.launch(Dispatchers.IO) {
                    viewModel.updatePreferences(preference)
                }
                navController.popBackStack()
            })
        }
        composable("profile") {
            Profile(priv = priv, pub = pub,
                onSubscribeStreamEvent = onSubscribeStreamEvent,
                onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                onPost = onPost,
                onNavigate = {
                navController.popBackStack()
            })
        }
        composable("image?url={url}") {
            val url = URLDecoder.decode(it.arguments?.getString("url") ?: "", "UTF-8")
            if (url.isNotEmpty()) {
                ImageViewer(onDownload = onDownload, url)
            }
            else {
                Toast.makeText(context, stringResource(id = R.string.error_no_valid_URL), Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }
        composable("post?class={class}&id={id}&pubkey={pubkey}&channel={channel}&event={event}") {
            val c = it.arguments?.getString("class") ?: ""
            val id = it.arguments?.getString("id") ?: ""
            val pubkey = it.arguments?.getString("pubkey") ?: ""
            val channel = it.arguments?.getString("channel") ?: ""
            val e = URLDecoder.decode(it.arguments?.getString("event") ?: "", "UTF-8")
            val event = try {
                val json = JSONObject(e)
                Event.parse(json)
            }
            catch (e: JSONException) {
                null
            }
            val screen = when (c) {
                "OwnTimeline" -> { Screen.OwnTimeline(id) }
                "Timeline" -> { Screen.Timeline(id) }
                "Channel" -> {
                    if (pubkey.isNotEmpty()) {
                        Screen.Channel(id, pubkey)
                    }
                    else {
                        null
                    }
                }
                "EventDetail" -> {
                    if (pubkey.isNotEmpty()) {
                        Screen.EventDetail(id = id, pubkey = pubkey)
                    }
                    else {
                        null
                    }
                }
                "ChannelEventDetail" -> {
                    if (pubkey.isNotEmpty() && channel.isNotEmpty()) {
                        Screen.ChannelEventDetail(id = id, pubkey = pubkey, channelID = channel)
                    }
                    else {
                        null
                    }
                }
                "Notification" -> {
                    Screen.Notification(id = id)
                }
                else -> { null }
            }
            if (screen != null) {
                Post(priv = priv, pub = pub, screen, event,
                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                    onNavigate = { kind, text, tags ->
                        onPost(kind, text, tags)
                        navController.popBackStack()
                    })
            }
            else {
                Text(stringResource(id = R.string.error_failed_to_open_post_screen))
            }
        }
    }
}
