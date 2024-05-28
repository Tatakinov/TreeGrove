package io.github.tatakinov.treegrove.ui

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import org.json.JSONException
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun Main(viewModel: TreeGroveViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            Home(viewModel, onNavigateSetting = {
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
                }
            }, onNavigateProfile = {
                navController.navigate("profile")
            }, onNavigateImage = { url ->
                navController.navigate("image?url=${URLEncoder.encode(url, "UTF-8")}")
            })
        }
        composable("setting") {
            Setting(viewModel, onNavigate = {
                navController.popBackStack()
            })
        }
        composable("profile") {
            Profile(viewModel, onNavigate = {
                navController.popBackStack()
            })
        }
        composable("image?url={url}") {
            val url = URLDecoder.decode(it.arguments?.getString("url") ?: "", "UTF-8")
            if (url.isNotEmpty()) {
                ImageViewer(viewModel, url)
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
                else -> { null }
            }
            if (screen != null) {
                Post(viewModel, screen, event, onNavigate = {
                    navController.popBackStack()
                })
            }
            else {
                Text(stringResource(id = R.string.error_failed_to_open_post_screen))
            }
        }
    }
}
