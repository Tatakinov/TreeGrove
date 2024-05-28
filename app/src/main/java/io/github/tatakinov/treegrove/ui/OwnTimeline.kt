package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.StreamFilter
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent

@Composable
fun OwnTimeline(viewModel: TreeGroveViewModel, id: String, onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    var expandFolloweeList by remember { mutableStateOf(false) }
    val followeeFilter = Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(id))
    val followeeEvent by viewModel.subscribeReplaceableEvent(followeeFilter).collectAsState()
    val f = followeeEvent
    val followeeList = if (f is LoadingData.Valid && f.data is ReplaceableEvent.Contacts) {
        f.data.list
    }
    else {
        listOf()
    }
    var expandFollowerList by remember { mutableStateOf(false) }
    val followerFilter = Filter(kinds = listOf(Kind.Contacts.num), tags = mapOf("p" to listOf(id)))
    val followerListEvent by viewModel.subscribeStreamEvent(StreamFilter(id = "follower@${id}", filter = followerFilter)).collectAsState()
    if (followeeList.isNotEmpty()) {
        val eventFilter = Filter(kinds = listOf(Kind.Text.num, Kind.Repost.num, Kind.GenericRepost.num), authors = followeeList.map { it.key })
        val eventList by viewModel.subscribeStreamEvent(StreamFilter(id = "own@${id}", filter = eventFilter)).collectAsState()
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
            }
            items(items = eventList, key = { event ->
                event.toJSONObject().toString()
            }) { event ->
                HorizontalDivider()
                EventContainer(viewModel, event = event, onNavigate = onNavigate, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage, isFocused = false)
            }
            item{
                HorizontalDivider()
                LoadMoreEventsButton(viewModel = viewModel, filter = eventFilter)
            }
        }
        LaunchedEffect(id) {
            if (eventList.isEmpty()) {
                viewModel.fetchPastPost(eventFilter)
            }
        }
    }
    else {
        Text(stringResource(id = R.string.follow_someone))
    }
}
