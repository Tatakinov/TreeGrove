package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import org.json.JSONException
import org.json.JSONObject

@Composable
fun RepostEvent(viewModel: TreeGroveViewModel, event: Event, onNavigate: ((Event) -> Unit)?, onAddScreen: ((Screen) -> Unit)?,
           onNavigateImage: ((String) -> Unit)?, isFocused: Boolean) {
    val eTag = event.tags.firstOrNull { it.size >= 2 && it[0] == "e" }
    val kTag = event.tags.firstOrNull { it.size >= 2 && it[0] == "e" }
    val metaFilter = Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(event.pubkey))
    val metaData by viewModel.subscribeReplaceableEvent(metaFilter).collectAsState()
    val m = metaData
    val name = if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
        m.data.name
    }
    else {
        event.pubkey
    }
    Column {
        Text(stringResource(id = R.string.repost_by, name), fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp, end = 10.dp))
        if (eTag != null) {
            val kinds = if (event.kind == Kind.GenericRepost.num && kTag != null && kTag[1].toIntOrNull() != null) {
                listOf(kTag[1].toInt())
            }
            else {
                listOf()
            }
            val filter = Filter(ids = listOf(eTag[1]), kinds = kinds)
            val el by viewModel.subscribeOneShotEvent(filter).collectAsState()
            if (el.isNotEmpty()) {
                val e = el.first()
                TextEvent(
                    viewModel = viewModel,
                    event = e,
                    onNavigate = onNavigate,
                    onAddScreen = onAddScreen,
                    onNavigateImage = onNavigateImage,
                    isFocused = isFocused
                )
            }
            else {
                val e = try {
                    val json = JSONObject(event.content)
                    Event.parse(json)
                } catch (e: JSONException) {
                    null
                }
                if (e != null) {
                    TextEvent(
                        viewModel = viewModel,
                        event = e,
                        onNavigate = onNavigate,
                        onAddScreen = onAddScreen,
                        onNavigateImage = onNavigateImage,
                        isFocused = isFocused
                    )
                }
            }
        }
        else {
            val e = try {
                val json = JSONObject(event.content)
                Event.parse(json)
            } catch (e: JSONException) {
                null
            }
            if (e != null) {
                TextEvent(
                    viewModel = viewModel,
                    event = e,
                    onNavigate = onNavigate,
                    onAddScreen = onAddScreen,
                    onNavigateImage = onNavigateImage,
                    isFocused = isFocused
                )
            }
        }
    }
}
