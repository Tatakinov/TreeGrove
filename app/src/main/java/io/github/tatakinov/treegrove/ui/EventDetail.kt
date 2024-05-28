package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        if (parentIDList.isNotEmpty()) {
            val parentFilter =
                Filter(ids = parentIDList, kinds = listOf(Kind.Text.num, Kind.ChannelMessage.num))
            val parentEventList by viewModel.subscribeOneShotEvent(parentFilter).collectAsState()
            val childFilter = Filter(
                kinds = listOf(Kind.Text.num, Kind.ChannelMessage.num),
                tags = mapOf("e" to listOf(event.id))
            )
            val childEventList by viewModel.subscribeOneShotEvent(childFilter).collectAsState()
            LazyColumn(state = listState) {
                items(items = parentEventList.sortedBy { it.createdAt },
                    key = { it.toJSONObject().toString() }) {
                    TextEvent(viewModel, it, onNavigate, onAddScreen = onAddScreen, onNavigateImage, false)
                }
                item {
                    TextEvent(
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
                    TextEvent(viewModel, it, onNavigate, onAddScreen, onNavigateImage, false)
                }
            }
        }
        else {
            val childFilter = Filter(
                kinds = listOf(Kind.Text.num, Kind.ChannelMessage.num),
                tags = mapOf("e" to listOf(event.id))
            )
            val childEventList by viewModel.subscribeOneShotEvent(childFilter).collectAsState()
            LazyColumn(state = listState) {
                item {
                    TextEvent(
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
                    TextEvent(viewModel, it, onNavigate, onAddScreen, onNavigateImage, false)
                }
            }
        }
    }
}