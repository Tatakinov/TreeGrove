package io.github.tatakinov.treegrove.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.StreamUpdater
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ChannelEventDetail(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?, id: String, pubkey: String, channelID: String,
                       onSubscribeStreamEvent: (Filter) -> StreamUpdater<List<Event>>,
                       onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
                       onSubscribeOneShotEvent: (Filter) -> StateFlow<List<Event>>,
                       onRepost: (Event) -> Unit, onPost: (Int, String, List<List<String>>) -> Unit,
                       onNavigate: (Event) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    EventDetail(priv, pub, id, pubkey,
        onSubscribeStreamEvent = onSubscribeStreamEvent,
        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
        onRepost = onRepost, onPost = onPost,
        onNavigate = onNavigate, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
}
