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

@Composable
fun EventDetail(viewModel: TreeGroveViewModel, id: String, pubkey: String, onNavigate: (Event) -> Unit,
                onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    val filter = Filter(ids = listOf(id), authors = listOf(pubkey))
    val eventList by viewModel.subscribeOneShotEvent(filter).collectAsState()
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
        val childEventFlow = remember { viewModel.subscribeStreamEvent(childFilter) }
        val childEventList by childEventFlow.collectAsState()
        if (parentIDList.isNotEmpty()) {
            val parentFilter =
                Filter(ids = parentIDList, kinds = listOf(Kind.Text.num, Kind.ChannelMessage.num))
            val parentEventList by viewModel.subscribeOneShotEvent(parentFilter).collectAsState()
            LazyColumn(state = listState) {
                items(items = parentEventList.sortedBy { it.createdAt },
                    key = { it.toJSONObject().toString() }) {
                    EventContainer(viewModel, it, onNavigate, onAddScreen = onAddScreen, onNavigateImage, false)
                }
                item {
                    EventContainer(
                        viewModel,
                        event,
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
                    EventContainer(viewModel, it, onNavigate, onAddScreen, onNavigateImage, false)
                }
            }
        }
        else {
            LazyColumn(state = listState) {
                item {
                    EventContainer(
                        viewModel,
                        event,
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
                    EventContainer(viewModel, it, onNavigate, onAddScreen, onNavigateImage, false)
                }
            }
        }
        DisposableEffect(event.id) {
            if (childEventList.isEmpty()) {
                viewModel.fetchStreamPastPost(childFilter, -1)
            }
            onDispose {
                viewModel.unsubscribeStreamEvent(childFilter)
            }
        }
    }
}