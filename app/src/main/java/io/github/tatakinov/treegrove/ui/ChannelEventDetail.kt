package io.github.tatakinov.treegrove.ui

import androidx.compose.runtime.Composable
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event

@Composable
fun ChannelEventDetail(viewModel: TreeGroveViewModel, id: String, pubkey: String, channelID: String,
                       onNavigate: (Event) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    EventDetail(viewModel, id, pubkey, onNavigate, onAddScreen, onNavigateImage)
}
