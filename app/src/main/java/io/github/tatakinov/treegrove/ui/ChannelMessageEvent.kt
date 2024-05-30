package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent

@Composable
fun ChannelMessageEvent(viewModel: TreeGroveViewModel, event: Event, onNavigate: ((Event) -> Unit)?, onAddScreen: ((Screen) -> Unit)?,
                        onNavigateImage: ((String) -> Unit)?, isFocused: Boolean, suppressDetail: Boolean) {
    val ids = if (event.tags.any { it.size >= 4 && it[0] == "e" && it[3] == "root" }) {
        listOf(event.tags.filter { it.size >= 4 && it[0] == "e" && it[3] == "root" }.map { it[1] }.first())
    }
    else {
        event.tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
    }
    if (ids.isNotEmpty()) {
        val filter = Filter(kinds = listOf(Kind.ChannelMetadata.num), tags = mapOf("e" to ids))
        viewModel.subscribeOneShotEvent(Filter(ids = ids, kinds = listOf(Kind.ChannelCreation.num)))
        val metaData by viewModel.subscribeReplaceableEvent(filter).collectAsState()
        val m = metaData
        val name = if (m is LoadingData.Valid && m.data is ReplaceableEvent.ChannelMetaData) {
            m.data.name
        }
        else {
            event.tags.firstOrNull { it.size == 4 && it[0] == "e" && it[3] == "root" }?.get(1) ?: ""
        }
        Column {
            if (!suppressDetail) {
                Text(
                    stringResource(id = R.string.at_channel, name),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp)
                )
            }
            TextEvent(
                viewModel = viewModel,
                event = event,
                onNavigate = onNavigate,
                onAddScreen = onAddScreen,
                onNavigateImage = onNavigateImage,
                isFocused = isFocused
            )
        }
    }
}