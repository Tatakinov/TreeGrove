package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun Timeline(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?, id: String,
             onSubscribeStreamEvent: (Filter) -> StreamUpdater<List<Event>>,
             onSubscribeOneShotEvent: (Filter) -> StateFlow<List<Event>>,
             onSubscribeStreamReplaceableEvent: (Filter) -> StreamUpdater<LoadingData<ReplaceableEvent>>,
             onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
             onRepost: (Event) -> Unit, onPost: (Int, String, List<List<String>>) -> Unit,
             onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    val listState = rememberLazyListState()
    val eventFilter = Filter(kinds = listOf(Kind.Text.num, Kind.Repost.num, Kind.GenericRepost.num, Kind.ChannelMessage.num), authors = listOf(id))
    val eventListUpdater = remember { onSubscribeStreamEvent(eventFilter) }
    val eventList by eventListUpdater.flow.collectAsState()
    val metaDataFilter = Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(id))
    val metaData by onSubscribeReplaceableEvent(metaDataFilter).collectAsState()
    var expandFolloweeList by remember { mutableStateOf(false) }
    val followeeFilter = Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(id))
    val followeeEvent by onSubscribeReplaceableEvent(followeeFilter).collectAsState()
    val f = followeeEvent
    val followeeListEvent = if (f is LoadingData.Valid && f.data is ReplaceableEvent.Contacts) {
        f.data.list
    }
    else {
        listOf()
    }
    var expandFollowerList by remember { mutableStateOf(false) }
    val followerFilter = Filter(kinds = listOf(Kind.Contacts.num), tags = mapOf("p" to listOf(id)))
    val followerListUpdater = onSubscribeStreamEvent(followerFilter)
    val followerListEvent by followerListUpdater.flow.collectAsState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
        val m = metaData
        item {
            Follow(priv = priv, pub = pub, pubkey = id,
                onSubscribeStreamReplaceableEvent = onSubscribeStreamReplaceableEvent,
                onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                onPost = onPost,
                onAddScreen = onAddScreen)
        }
        if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
            item {
                Text(m.data.about, modifier = Modifier.padding(start = 10.dp, end = 10.dp))
            }
        }
        item {
            HorizontalDivider()
        }
        item {
            TextButton(onClick = { expandFolloweeList = !expandFolloweeList }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(id = R.string.followee))
            }
        }
        if (expandFolloweeList) {
            items(items = followeeListEvent, key = { "followee@${it.key}" }) {
                HorizontalDivider()
                Follow(priv = priv, pub = pub, it.key,
                    onSubscribeStreamReplaceableEvent = onSubscribeStreamReplaceableEvent,
                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                    onPost = onPost,
                    onAddScreen = onAddScreen)
            }
        }
        item {
            HorizontalDivider()
        }
        item {
            TextButton(onClick = { expandFollowerList = !expandFollowerList }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(id = R.string.follower))
            }
        }
        if (expandFollowerList) {
            items(items = followerListEvent, key = { "follower@${it.pubkey}" }) {
                HorizontalDivider()
                Follow(priv = priv, pub = pub, it.pubkey,
                    onSubscribeStreamReplaceableEvent = onSubscribeStreamReplaceableEvent,
                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                    onPost = onPost,
                    onAddScreen = onAddScreen)
            }
            item {
                HorizontalDivider()
            }
            item {
                LoadMoreEventsButton(fetch = followerListUpdater.fetch)
            }
        }
        itemsIndexed(items = eventList, key = { index, event ->
            event.toJSONObject().toString()
        }) { index, event ->
            HorizontalDivider()
            EventContainer(priv = priv, pub = pub, event = event,
                onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                onRepost = onRepost, onPost = onPost,
                onNavigate = onNavigate, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage, false)
            LaunchedEffect(Unit) {
                eventListUpdater.fetch(event.createdAt)
            }
        }
        item {
            HorizontalDivider()
            LoadMoreEventsButton(fetch = eventListUpdater.fetch)
        }
    }
    DisposableEffect(id) {
        if (eventList.isEmpty()) {
            eventListUpdater.fetch(0)
        }
        onDispose {
            eventListUpdater.unsubscribe()
            followerListUpdater.unsubscribe()
        }
    }
}
