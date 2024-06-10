package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.StreamUpdater
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.flow.StateFlow

@Composable
fun Follow(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?, pubkey: String,
           onSubscribeStreamReplaceableEvent: (Filter) -> StreamUpdater<LoadingData<ReplaceableEvent>>,
           onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
           onPost: (Int, String, List<List<String>>) -> Unit,
           onAddScreen: (Screen) -> Unit) {
    val meta by onSubscribeReplaceableEvent(
        Filter(
            kinds = listOf(Kind.Metadata.num),
            authors = listOf(pubkey)
        )
    ).collectAsState()
    val m = meta
    val name = if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
        m.data.name
    }
    else {
        pubkey
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            name,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 10.dp)
                .clickable {
                    onAddScreen(Screen.Timeline(id = pubkey))
                }
        )
        if (priv is NIP19.Data.Sec && pub is NIP19.Data.Pub) {
            val contactsFilter = Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(pub.id))
            val contactsEventUpdater = remember { onSubscribeStreamReplaceableEvent(contactsFilter) }
            val contactsEventList by contactsEventUpdater.flow.collectAsState()
            val e = contactsEventList
            val c = if (e is LoadingData.Valid) {
                e.data
            }
            else {
                null
            }
            if (c is ReplaceableEvent.Contacts) {
                if (c.list.any { it.key == pubkey }) {
                    var expandUnfollowDialog by remember { mutableStateOf(false) }
                    Button(onClick = {
                        expandUnfollowDialog = true
                    }) {
                        Icon(Icons.Default.PersonRemove, "unfollow")
                    }
                    if (expandUnfollowDialog) {
                        AlertDialog(
                            title = {
                                Text(stringResource(id = R.string.unfollow))
                            },
                            text = {
                                Text(name)
                            },
                            onDismissRequest = { expandUnfollowDialog = false },
                            dismissButton = {
                                TextButton(onClick = { expandUnfollowDialog = false }) {
                                    Text(stringResource(id = R.string.cancel))
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    expandUnfollowDialog = false
                                    val list = c.list.filter { it.key != pubkey }
                                    val tags = mutableListOf<List<String>>()
                                    for (tag in list) {
                                        tags.add(listOf("p", tag.key, tag.relay, tag.petname))
                                    }
                                    onPost(Kind.Contacts.num, "", tags)
                                }) {
                                    Text(stringResource(id = R.string.ok))
                                }
                            }
                        )
                    }
                }
                else {
                    var expandFollowDialog by remember { mutableStateOf(false) }
                    Button(onClick = {
                        expandFollowDialog = true
                    }) {
                        Icon(Icons.Default.PersonAdd, "follow")
                    }
                    if (expandFollowDialog) {
                        AlertDialog(
                            title = {
                                Text(stringResource(id = R.string.follow))
                            },
                            text = {
                                Text(name)
                            },
                            onDismissRequest = { expandFollowDialog = false },
                            dismissButton = {
                                TextButton(onClick = { expandFollowDialog = false }) {
                                    Text(stringResource(id = R.string.cancel))
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    expandFollowDialog = false
                                    val list =
                                        mutableListOf<ReplaceableEvent.Contacts.Data>().apply {
                                            addAll(c.list)
                                            add(ReplaceableEvent.Contacts.Data(key = pubkey))
                                        }
                                    val tags = mutableListOf<List<String>>()
                                    for (tag in list) {
                                        tags.add(listOf("p", tag.key, tag.relay, tag.petname))
                                    }
                                    onPost(Kind.Contacts.num, "", tags)
                                }) {
                                    Text(stringResource(id = R.string.ok))
                                }
                            })
                    }
                }
            }
        }
    }
}
