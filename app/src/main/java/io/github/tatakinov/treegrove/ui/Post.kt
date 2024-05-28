package io.github.tatakinov.treegrove.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.Misc
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Post(viewModel: TreeGroveViewModel, screen: Screen, event: Event?, onNavigate: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val privateKey by viewModel.privateKeyFlow.collectAsState()
    val publicKey by viewModel.publicKeyFlow.collectAsState()
    var text by remember { mutableStateOf("") }
    var openConfirmDialog by remember { mutableStateOf(false) }
    val filter = when (screen) {
        is Screen.OwnTimeline -> { Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(screen.id)) }
        is Screen.Timeline -> { Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(screen.id)) }
        is Screen.Channel -> { Filter(kinds = listOf(Kind.ChannelMetadata.num), authors = listOf(screen.pubkey), tags = mapOf("e" to listOf(screen.id))) }
        is Screen.EventDetail -> { Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(screen.pubkey)) }
        is Screen.ChannelEventDetail -> { Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(screen.pubkey)) }
    }
    val metaData by viewModel.subscribeReplaceableEvent(filter).collectAsState()
    Column {
        if (event != null) {
            TextEvent(viewModel, event, onNavigate = null, onAddScreen = null, onNavigateImage = null, isFocused = false)
            HorizontalDivider()
        }
        TextField(value = text, onValueChange = {
            text = it
        }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            openConfirmDialog = true
        }) {
            Icon(Icons.AutoMirrored.Filled.Send, "send")
        }
    }
    if (openConfirmDialog) {
        AlertDialog(title = {
            Text(stringResource(id = R.string.confirm_title))
        }, text = {
            Column {
                val m = metaData
                when (screen) {
                    is Screen.OwnTimeline -> {
                        if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
                            Text(stringResource(id = R.string.post_to_own, m.data.name))
                        }
                    }
                    is Screen.Timeline -> {
                        if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
                            Text(stringResource(id = R.string.post_to_other, m.data.name))
                        }
                    }
                    is Screen.Channel -> {
                        if (m is LoadingData.Valid && m.data is ReplaceableEvent.ChannelMetaData) {
                            Text(stringResource(id = R.string.post_to_channel, m.data.name))
                        }
                    }
                    is Screen.EventDetail -> {
                        if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
                            Text(stringResource(id = R.string.post_to_other, m.data.name))
                        }
                    }
                    is Screen.ChannelEventDetail -> {
                        if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
                            Text(stringResource(id = R.string.post_to_other, m.data.name))
                        }
                    }
                }
                HorizontalDivider()
                Text(text = text, modifier = Modifier.fillMaxWidth())
            }
        },onDismissRequest = {
            openConfirmDialog = false
        }, dismissButton = {
            TextButton(
                onClick = {
                    openConfirmDialog = false
                }
            ) {
                Text(stringResource(id = R.string.cancel))
            }
        }, confirmButton = {
            TextButton(onClick = {
                val priv = NIP19.parse(privateKey)
                val pub = NIP19.parse(publicKey)
                if (text.isNotEmpty() && priv is NIP19.Data.Sec && pub is NIP19.Data.Pub) {
                    val kind = when (screen) {
                        is Screen.OwnTimeline -> {
                            Kind.Text
                        }
                        is Screen.Timeline -> {
                            Kind.Text
                        }
                        is Screen.Channel -> {
                            Kind.ChannelMessage
                        }
                        is Screen.EventDetail -> {
                            Kind.Text
                        }
                        is Screen.ChannelEventDetail -> {
                            Kind.ChannelMessage
                        }
                    }
                    val generate = { e: Event? ->
                        val tags = mutableListOf<List<String>>()
                        if (e != null) {
                            for (tag in e.tags) {
                                if (tag.size >= 2 && (tag[0] == "p" || tag[0] == "e")) {
                                    tags.add(tag)
                                }
                            }
                            if (tags.none { it[0] == "e" }) {
                                tags.add(listOf("e", e.id, "", "root"))
                            }
                            else {
                                tags.add(listOf("e", e.id, "", "reply"))
                            }
                            if (tags.isEmpty() || tags.none { it.size >= 2 && it[0] == "p" && it[1] == e.pubkey }) {
                                tags.add(listOf("p", e.pubkey))
                            }
                        }
                        tags
                    }
                    val t = when (screen) {
                        is Screen.OwnTimeline -> {
                            generate(event)
                        }
                        is Screen.Timeline -> {
                            val tags = generate(event)
                            if (tags.isEmpty() || tags.none { it.size >= 2 && it[0] == "p" && it[1] == screen.id}) {
                                tags.add(listOf("p", screen.id))
                            }
                            tags
                        }
                        is Screen.Channel -> {
                            val m = metaData
                            var recommendRelay: String? = null
                            if (m is LoadingData.Valid && m.data is ReplaceableEvent.ChannelMetaData) {
                                recommendRelay = m.data.recommendRelay
                            }
                            val tags = mutableListOf<List<String>>()
                            if (event != null) {
                                for (tag in event.tags) {
                                    if (tag.size >= 2 && (tag[0] == "p" || tag[0] == "e")) {
                                        tags.add(tag)
                                    }
                                }
                                if (tags.isEmpty() || tags.none { it.size >= 2 && it[0] == "p" && it[1] == event.pubkey }) {
                                    tags.add(listOf("p", event.pubkey))
                                }
                                tags.add(listOf("e", event.id, recommendRelay ?: "", "reply"))
                            }
                            else {
                                tags.add(listOf("e", screen.id, recommendRelay ?: "", "root"))
                            }
                            tags
                        }
                        is Screen.EventDetail -> {
                            val tags = generate(event)
                            if (screen.pubkey != pub.id && (tags.isEmpty() || tags.none { it.size >= 2 && it[0] == "p" && it[1] == screen.pubkey})) {
                                tags.add(listOf("p", screen.pubkey))
                            }
                            tags
                        }
                        is Screen.ChannelEventDetail -> {
                            val tags = mutableListOf<List<String>>()
                            if (event != null) {
                                for (tag in event.tags) {
                                    if (tag.size >= 2 && (tag[0] == "p" || tag[0] == "e")) {
                                        tags.add(tag)
                                    }
                                }
                                if (tags.isEmpty() || tags.none { it.size >= 2 && it[0] == "p" && it[1] == event.pubkey }) {
                                    tags.add(listOf("p", event.pubkey))
                                }
                                // TODO reply? root?
                                tags.add(listOf("e", event.id, "", "reply"))
                            }
                            else {
                                // TODO recommended relay
                                tags.add(listOf("e", screen.channelID, "", "root"))
                            }
                            tags
                        }
                    }
                    Misc.post(viewModel, kind, text, t, priv, pub, onSuccess = {}, onFailure = { url, reason ->
                        coroutineScope.launch {
                            Toast.makeText(context, context.getString(R.string.error_failed_to_post, reason), Toast.LENGTH_SHORT).show()
                        }
                    })
                    onNavigate()
                }
            }) {
                Text(stringResource(id = R.string.ok))
            }
        })
    }
}
