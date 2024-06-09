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
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent

@Composable
fun Timeline(viewModel: TreeGroveViewModel, id: String, onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    val listState = rememberLazyListState()
    val eventFilter = Filter(kinds = listOf(Kind.Text.num, Kind.Repost.num, Kind.GenericRepost.num, Kind.ChannelMessage.num), authors = listOf(id))
    val eventListFlow = remember { viewModel.subscribeStreamEvent(eventFilter) }
    val eventList by eventListFlow.collectAsState()
    val metaDataFilter = Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(id))
    val metaData by viewModel.subscribeReplaceableEvent(metaDataFilter).collectAsState()
    var expandFolloweeList by remember { mutableStateOf(false) }
    val followeeFilter = Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(id))
    val followeeEvent by viewModel.subscribeReplaceableEvent(followeeFilter).collectAsState()
    val f = followeeEvent
    val followeeListEvent = if (f is LoadingData.Valid && f.data is ReplaceableEvent.Contacts) {
        f.data.list
    }
    else {
        listOf()
    }
    var expandFollowerList by remember { mutableStateOf(false) }
    val followerFilter = Filter(kinds = listOf(Kind.Contacts.num), tags = mapOf("p" to listOf(id)))
    val followerListFlow = viewModel.subscribeStreamEvent(followerFilter)
    val followerListEvent by followerListFlow.collectAsState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
        val m = metaData
        item {
            Follow(viewModel = viewModel, pubkey = id, onAddScreen = onAddScreen)
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
                Follow(viewModel, it.key, onAddScreen = onAddScreen)
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
                Follow(viewModel = viewModel, it.pubkey, onAddScreen = onAddScreen)
            }
            item {
                HorizontalDivider()
            }
            item {
                LoadMoreEventsButton(viewModel = viewModel, filter = followerFilter)
            }
        }
        itemsIndexed(items = eventList, key = { index, event ->
            event.toJSONObject().toString()
        }) { index, event ->
            HorizontalDivider()
            EventContainer(viewModel, event, onNavigate = onNavigate, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage, false)
            LaunchedEffect(Unit) {
                viewModel.fetchStreamPastPost(eventFilter, index)
            }
        }
        item {
            HorizontalDivider()
            LoadMoreEventsButton(viewModel = viewModel, filter = eventFilter)
        }
    }
    DisposableEffect(id) {
        if (eventList.isEmpty()) {
            viewModel.fetchStreamPastPost(eventFilter, -1)
        }
        onDispose {
            viewModel.unsubscribeStreamEvent(eventFilter)
            viewModel.unsubscribeStreamEvent(followerFilter)
        }
    }
}
