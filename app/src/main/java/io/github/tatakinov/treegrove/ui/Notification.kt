package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.StreamUpdater
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.flow.StateFlow

@Composable
fun Notification(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?,
                 onSubscribeStreamEvent: (Filter) -> StreamUpdater<List<Event>>,
                 onSubscribeOneShotEvent: (Filter) -> StateFlow<List<Event>>,
                 onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
                 onRepost: (Event) -> Unit, onPost: (Int, String, List<List<String>>) -> Unit,
                 onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage:(String) -> Unit) {
    if (pub is NIP19.Data.Pub) {
        // TODO support contacts
        val filter = Filter(kinds = listOf(Kind.Text.num, Kind.Repost.num, Kind.GenericRepost.num, Kind.ChannelMessage.num), tags = mapOf("e" to listOf(pub.id)))
        val eventListUpdater = remember { onSubscribeStreamEvent(filter) }
        val eventList by eventListUpdater.flow.collectAsState()
        val listState = rememberLazyListState()
        LazyColumn(state = listState) {
            itemsIndexed (items = eventList, key = { _, e -> e.toJSONObject().toString() }) { index, event ->
                EventContainer(
                    priv = priv,
                    pub = pub,
                    event = event,
                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                    onRepost = onRepost, onPost = onPost,
                    onNavigate = onNavigate,
                    onAddScreen = onAddScreen,
                    onNavigateImage = onNavigateImage,
                    isFocused = false
                )
                LaunchedEffect(Unit) {
                    eventListUpdater.fetch(event.createdAt)
                }
            }
            item {
                LoadMoreEventsButton(fetch = eventListUpdater.fetch)
            }
        }
        DisposableEffect(pub.id) {
            onDispose {
                eventListUpdater.unsubscribe()
            }
        }
    }
}