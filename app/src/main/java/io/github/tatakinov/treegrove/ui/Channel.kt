package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.StreamUpdater
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.flow.StateFlow

@Composable
fun Channel(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?, id: String, pubkey: String,
            onSubscribeStreamEvent: (Filter) -> StreamUpdater<List<Event>>,
            onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
            onSubscribeOneShotEvent: (Filter) -> StateFlow<List<Event>>,
            onRepost: (Event) -> Unit, onPost: (Int, String, List<List<String>>) -> Unit,
            onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    val listState = rememberLazyListState()
    val metaDataFilter = Filter(kinds = listOf(Kind.ChannelMetadata.num), authors = listOf(pubkey), tags = mapOf("e" to listOf(id)))
    val metaData by onSubscribeReplaceableEvent(metaDataFilter).collectAsState()
    val eventFilter = Filter(kinds = listOf(Kind.ChannelMessage.num), tags = mapOf("e" to listOf(id)))
    val eventListUpdater = remember { onSubscribeStreamEvent(eventFilter) }
    val eventList by eventListUpdater.flow.collectAsState()
    Column {
        val m = metaData
        if (m is LoadingData.Valid && m.data is ReplaceableEvent.ChannelMetaData) {
            ChannelTitle(priv = priv, pub = pub, id = id, name = m.data.name, about = m.data.about,
                onSubscribeReplaceableEvent = onSubscribeReplaceableEvent, onPost = onPost)
        } else {
            ChannelTitle(priv = priv, pub = pub, id = id, name = id, about = null,
                onSubscribeReplaceableEvent = onSubscribeReplaceableEvent, onPost = onPost)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
            itemsIndexed(items = eventList, key = { _, event ->
                event.toJSONObject().toString()
            }) { index, event ->
                EventContainer(
                    priv = priv, pub = pub,
                    event = event,
                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                    onRepost = onRepost, onPost = onPost,
                    onNavigate = onNavigate,
                    onAddScreen = onAddScreen,
                    onNavigateImage = onNavigateImage,
                    isFocused = false,
                    suppressDetail = true
                )
                LaunchedEffect(Unit) {
                    eventListUpdater.fetch(event.createdAt)
                }
            }
            item {
                HorizontalDivider()
                LoadMoreEventsButton(fetch = eventListUpdater.fetch)
            }
        }
    }
    DisposableEffect(id) {
        if (eventList.isEmpty()) {
            eventListUpdater.fetch(0)
        }
        onDispose {
            eventListUpdater.unsubscribe()
        }
    }
}
