package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.flow.StateFlow

@Composable
fun EventContainer(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?, event: Event,
                   onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
                   onSubscribeOneShotEvent: (Filter) -> StateFlow<List<Event>>,
                   onRepost: ((Event) -> Unit)?, onPost: ((Int, String, List<List<String>>) -> Unit)?,
                   onNavigate: ((Event) -> Unit)?, onAddScreen: ((Screen) -> Unit)?,
                   onNavigateImage: ((String) -> Unit)?, isFocused: Boolean, suppressDetail: Boolean = false) {
    val deleteFilter = Filter(kinds = listOf(Kind.EventDeletion.num), tags = mapOf("e" to listOf(event.id)))
    val deleteEventList by onSubscribeOneShotEvent(deleteFilter).collectAsState()
    if (deleteEventList.any { it.pubkey == event.pubkey }) {
        val e = deleteEventList.first { it.pubkey == event.pubkey }
        Text(stringResource(id = R.string.deleted, e.content), modifier = Modifier.padding(10.dp))
    }
    else {
        var expand by remember { mutableStateOf(event.tags.none { it.isNotEmpty() && it[0] == "content-warning" }) }
        if (!expand) {
            val reason =
                event.tags.firstOrNull { it.size >= 2 && it[0] == "content-warning" }?.get(1) ?: ""
            Text(stringResource(id = R.string.show_content_warning, reason), modifier = Modifier
                .clickable {
                    expand = true
                }
                .padding(10.dp))
        } else {
            when (event.kind) {
                Kind.Text.num -> {
                    TextEvent(
                        priv = priv, pub = pub,
                        event = event,
                        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                        onRepost = onRepost, onPost = onPost,
                        onNavigate = onNavigate,
                        onAddScreen = onAddScreen,
                        onNavigateImage = onNavigateImage,
                        isFocused = isFocused
                    )
                }

                Kind.ChannelMessage.num -> {
                    ChannelMessageEvent(
                        priv = priv, pub = pub,
                        event = event,
                        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                        onRepost = onRepost, onPost = onPost,
                        onNavigate = onNavigate,
                        onAddScreen = onAddScreen,
                        onNavigateImage = onNavigateImage,
                        isFocused = isFocused,
                        suppressDetail = suppressDetail
                    )
                }

                Kind.Repost.num -> {
                    RepostEvent(
                        priv = priv, pub = pub,
                        event = event,
                        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                        onRepost = onRepost, onPost = onPost,
                        onNavigate = onNavigate,
                        onAddScreen = onAddScreen,
                        onNavigateImage = onNavigateImage,
                        isFocused = isFocused
                    )
                }

                Kind.GenericRepost.num -> {
                    RepostEvent(
                        priv = priv, pub = pub,
                        event = event,
                        onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                        onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                        onRepost = onRepost, onPost = onPost,
                        onNavigate = onNavigate,
                        onAddScreen = onAddScreen,
                        onNavigateImage = onNavigateImage,
                        isFocused = isFocused
                    )
                }
            }
        }
    }
}
