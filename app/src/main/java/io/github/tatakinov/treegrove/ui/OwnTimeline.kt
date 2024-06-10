package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
fun OwnTimeline(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?, id: String,
                onSubscribeStreamEvent: (Filter) -> StreamUpdater<List<Event>>,
                onSubscribeOneShotEvent: (Filter) -> StateFlow<List<Event>>,
                onSubscribeStreamReplaceableEvent: (Filter) -> StreamUpdater<LoadingData<ReplaceableEvent>>,
                onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
                onRepost: (Event) -> Unit, onPost: (Int, String, List<List<String>>) -> Unit,
                onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    var expandFolloweeList by remember { mutableStateOf(false) }
    val followeeFilter = Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(id))
    val followeeEventListUpdater = remember { onSubscribeStreamReplaceableEvent(followeeFilter) }
    val followeeEvent by followeeEventListUpdater.flow.collectAsState()
    val fe = followeeEvent
    val followeeList = if (fe is LoadingData.Valid && fe.data is ReplaceableEvent.Contacts) {
        fe.data.list
    }
    else {
        listOf()
    }
    var expandFollowerList by remember { mutableStateOf(false) }
    val followerFilter = Filter(kinds = listOf(Kind.Contacts.num), tags = mapOf("p" to listOf(id)))
    val followerListUpdater = remember { onSubscribeStreamEvent(followerFilter) }
    val followerListEvent by followerListUpdater.flow.collectAsState()
    if (followeeList.isNotEmpty()) {
        val eventFilter = Filter(kinds = listOf(Kind.Text.num, Kind.Repost.num, Kind.GenericRepost.num, Kind.ChannelMessage.num), authors = followeeList.map { it.key })
        val eventListUpdater = remember { onSubscribeStreamEvent(eventFilter) }
        val eventList by eventListUpdater.flow.collectAsState()
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
            item {
                TextButton(onClick = { expandFolloweeList = !expandFolloweeList }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.followee))
                }
            }
            if (expandFolloweeList) {
                items(items = followeeList, key = { "followee@${it.key}" }) {
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
                    LoadMoreEventsButton(fetch = followeeEventListUpdater.fetch)
                }
            }
            itemsIndexed(items = eventList, key = { _, event ->
                event.toJSONObject().toString()
            }) { index, event ->
                HorizontalDivider()
                EventContainer(priv = priv, pub = pub, event = event,
                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                    onRepost = onRepost, onPost = onPost,
                    onNavigate = onNavigate, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage, isFocused = false)
                LaunchedEffect(Unit) {
                    eventListUpdater.fetch(event.createdAt)
                }
            }
            item {
                HorizontalDivider()
            }
            item {
                LoadMoreEventsButton(fetch = eventListUpdater.fetch)
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
    else {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(id = R.string.follow_someone))
        }
    }
    DisposableEffect(id) {
        onDispose {
            followerListUpdater.unsubscribe()
        }
    }
}
