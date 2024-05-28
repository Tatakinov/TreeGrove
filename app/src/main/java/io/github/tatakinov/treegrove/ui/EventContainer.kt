package io.github.tatakinov.treegrove.ui

import androidx.compose.runtime.Composable
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Kind

@Composable
fun EventContainer(viewModel: TreeGroveViewModel, event: Event, onNavigate: ((Event) -> Unit)?, onAddScreen: ((Screen) -> Unit)?,
           onNavigateImage: ((String) -> Unit)?, isFocused: Boolean) {
    when (event.kind) {
        Kind.Text.num -> {
            TextEvent(
                viewModel = viewModel,
                event = event,
                onNavigate = onNavigate,
                onAddScreen = onAddScreen,
                onNavigateImage = onNavigateImage,
                isFocused = isFocused
            )
        }
        Kind.ChannelMessage.num -> {
            TextEvent(
                viewModel = viewModel,
                event = event,
                onNavigate = onNavigate,
                onAddScreen = onAddScreen,
                onNavigateImage = onNavigateImage,
                isFocused = isFocused
            )
        }
        Kind.Repost.num -> {
            RepostEvent(viewModel, event, onNavigate, onAddScreen, onNavigateImage, isFocused)
        }
        Kind.GenericRepost.num -> {
            RepostEvent(viewModel, event, onNavigate, onAddScreen, onNavigateImage, isFocused)
        }
    }
}
