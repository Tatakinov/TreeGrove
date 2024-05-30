package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19

@Composable
fun Notification(viewModel: TreeGroveViewModel, onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage:(String) -> Unit) {
    val publicKey by viewModel.publicKeyFlow.collectAsState()
    val pub = NIP19.parse(publicKey)
    if (pub is NIP19.Data.Pub) {
        // TODO support contacts
        val filter = Filter(kinds = listOf(Kind.Text.num, Kind.Repost.num, Kind.GenericRepost.num, Kind.ChannelMessage.num), tags = mapOf("e" to listOf(pub.id)))
        val eventListFlow = remember { viewModel.subscribeStreamEvent(filter) }
        val eventList by eventListFlow.collectAsState()
        val listState = rememberLazyListState()
        LazyColumn(state = listState) {
            items (items = eventList, key = { it.toJSONObject().toString() }) { event ->
                EventContainer(
                    viewModel = viewModel,
                    event = event,
                    onNavigate = onNavigate,
                    onAddScreen = onAddScreen,
                    onNavigateImage = onNavigateImage,
                    isFocused = false
                )
            }
            item {
                LoadMoreEventsButton(viewModel = viewModel, filter = filter)
            }
        }
        DisposableEffect(pub.id) {
            if (eventList.isEmpty()) {
                viewModel.fetchPastPost(filter)
            }
            onDispose {
                viewModel.unsubscribeStreamEvent(filter)
            }
        }
    }
}