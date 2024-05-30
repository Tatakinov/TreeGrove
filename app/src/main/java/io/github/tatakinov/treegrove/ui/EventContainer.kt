package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Kind

@Composable
fun EventContainer(viewModel: TreeGroveViewModel, event: Event, onNavigate: ((Event) -> Unit)?, onAddScreen: ((Screen) -> Unit)?,
           onNavigateImage: ((String) -> Unit)?, isFocused: Boolean, suppressDetail: Boolean = false) {
    var expand by remember { mutableStateOf(event.tags.none { it.isNotEmpty() && it[0] == "content-warning" }) }
    if (!expand) {
        val reason = event.tags.firstOrNull { it.size >= 2 && it[0] == "content-warning" }?.get(1) ?: ""
        Text(stringResource(id = R.string.show_content_warning, reason), modifier = Modifier.clickable {
            expand = true
        }.padding(10.dp))
    }
    else {
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
                ChannelMessageEvent(
                    viewModel = viewModel,
                    event = event,
                    onNavigate = onNavigate,
                    onAddScreen = onAddScreen,
                    onNavigateImage = onNavigateImage,
                    isFocused = isFocused,
                    suppressDetail = suppressDetail
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
}
