package io.github.tatakinov.treegrove.ui

import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ChannelMenuItem(channel: Event,
                    onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
                    onAddTab: () -> Unit) {
    val metaDataEvent by onSubscribeReplaceableEvent(
        Filter(
            kinds = listOf(
                Kind.ChannelMetadata.num
            ), authors = listOf(channel.pubkey),
            tags = mapOf("e" to listOf(channel.id))
        )
    ).collectAsState()
    val metaData = if (metaDataEvent is LoadingData.Valid) {
        metaDataEvent
    }
    else {
        val r = ReplaceableEvent.parse(channel)
        if (r != null) {
            LoadingData.Valid(r)
        }
        else {
            LoadingData.Invalid(LoadingData.Reason.ParseError)
        }
    }
    if (metaData is LoadingData.Valid && metaData.data is ReplaceableEvent.ChannelMetaData) {
        NavigationDrawerItem(label = {
            Text(metaData.data.name)
        },
            selected = false,
            onClick = onAddTab)
    }
}
