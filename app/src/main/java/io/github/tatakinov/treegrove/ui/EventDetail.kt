package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.StreamUpdater
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.flow.StateFlow

@Composable
fun EventDetail(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?, id: String, pubkey: String,
                onSubscribeStreamEvent: (Filter) -> StreamUpdater<List<Event>>,
                onSubscribeOneShotEvent: (Filter) -> StateFlow<List<Event>>,
                onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
                onRepost: (Event) -> Unit, onPost: (Int, String, List<List<String>>) -> Unit,
                onNavigate: (Event) -> Unit,
                onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    val filter = Filter(ids = listOf(id), authors = listOf(pubkey))
    val eventList by onSubscribeOneShotEvent(filter).collectAsState()
    val list = eventList

    if (list.isNotEmpty()) {
        val listState = rememberLazyListState()
        val event = list.first()
        val parentIDList = event.tags.filter {
            it.size >= 2 && it[0] == "e"
        }.map { it[1] }.distinctBy { it }
        val childFilter = Filter(
            kinds = listOf(Kind.Text.num, Kind.ChannelMessage.num),
            tags = mapOf("e" to listOf(event.id))
        )
        val childEventUpdater = remember { onSubscribeStreamEvent(childFilter) }
        val childEventList by childEventUpdater.flow.collectAsState()
        if (parentIDList.isNotEmpty()) {
            val parentFilter =
                Filter(ids = parentIDList, kinds = listOf(Kind.Text.num, Kind.ChannelMessage.num))
            val parentEventList by onSubscribeOneShotEvent(parentFilter).collectAsState()
            LazyColumn(state = listState) {
                items(items = parentEventList.sortedBy { it.createdAt },
                    key = { it.toJSONObject().toString() }) {
                    EventContainer(
                        priv = priv, pub = pub,
                        event = it,
                        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                        onRepost = onRepost, onPost = onPost,
                        onNavigate, onAddScreen = onAddScreen, onNavigateImage, false)
                }
                item {
                    EventContainer(
                        priv = priv, pub = pub,
                        event = event,
                        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                        onRepost = onRepost, onPost = onPost,
                        onNavigate,
                        onAddScreen,
                        onNavigateImage,
                        isFocused = true
                    )
                }
                val el = childEventList
                items(
                    items = el.sortedBy { it.createdAt },
                    key = { it.toJSONObject().toString() }) {
                    EventContainer(
                        priv = priv, pub = pub,
                        event = it,
                        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                        onRepost = onRepost, onPost = onPost,
                        onNavigate, onAddScreen, onNavigateImage, false)
                }
            }
        }
        else {
            LazyColumn(state = listState) {
                item {
                    EventContainer(
                        priv = priv, pub = pub,
                        event = event,
                        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                        onRepost = onRepost, onPost = onPost,
                        onNavigate,
                        onAddScreen,
                        onNavigateImage,
                        isFocused = true
                    )
                }
                itemsIndexed(
                    items = childEventList.sortedBy { it.createdAt },
                    key = { _, event -> event.toJSONObject().toString() }) { index, event ->
                    EventContainer(
                        priv = priv, pub = pub,
                        event = event,
                        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                        onRepost = onRepost, onPost = onPost,
                        onNavigate, onAddScreen, onNavigateImage, false)
                    LaunchedEffect(Unit) {
                        childEventUpdater.fetch(event.createdAt)
                    }
                }
            }
        }
        DisposableEffect(event.id) {
            if (childEventList.isEmpty()) {
                childEventUpdater.fetch(0)
            }
            onDispose {
                childEventUpdater.unsubscribe()
            }
        }
    }
    else {
        Text(stringResource(id = R.string.event_not_found), modifier = Modifier.padding(start = 10.dp, end = 10.dp))
    }
}