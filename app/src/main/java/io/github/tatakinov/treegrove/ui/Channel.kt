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
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent

@Composable
fun Channel(viewModel: TreeGroveViewModel, id: String, pubKey: String, onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    val listState = rememberLazyListState()
    val metaDataFilter = Filter(kinds = listOf(Kind.ChannelMetadata.num), authors = listOf(pubKey), tags = mapOf("e" to listOf(id)))
    val metaData by viewModel.subscribeReplaceableEvent(metaDataFilter).collectAsState()
    val eventFilter = Filter(kinds = listOf(Kind.ChannelMessage.num), tags = mapOf("e" to listOf(id)))
    val eventListFlow = remember { viewModel.subscribeStreamEvent(eventFilter) }
    val eventList by eventListFlow.collectAsState()
    Column {
        val m = metaData
        if (m is LoadingData.Valid && m.data is ReplaceableEvent.ChannelMetaData) {
            ChannelTitle(viewModel = viewModel, id = id, name = m.data.name, about = m.data.about)
        } else {
            ChannelTitle(viewModel = viewModel, id = id, name = id, about = null)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
            itemsIndexed(items = eventList, key = { _, event ->
                event.toJSONObject().toString()
            }) { index, event ->
                EventContainer(
                    viewModel = viewModel,
                    event = event,
                    onNavigate = onNavigate,
                    onAddScreen = onAddScreen,
                    onNavigateImage = onNavigateImage,
                    isFocused = false,
                    suppressDetail = true
                )
                LaunchedEffect(Unit) {
                    viewModel.fetchStreamPastPost(eventFilter, index)
                }
            }
            item {
                HorizontalDivider()
                LoadMoreEventsButton(viewModel = viewModel, filter = eventFilter)
            }
        }
    }
    DisposableEffect(id) {
        if (eventList.isEmpty()) {
            viewModel.fetchStreamPastPost(eventFilter, -1)
        }
        onDispose {
            viewModel.unsubscribeStreamEvent(eventFilter)
        }
    }
}
