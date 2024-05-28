package io.github.tatakinov.treegrove.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.Misc
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.launch

@Composable
fun Follow(viewModel: TreeGroveViewModel, pubkey: String, onAddScreen: (Screen) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val privateKey by viewModel.privateKeyFlow.collectAsState()
    val publicKey by viewModel.publicKeyFlow.collectAsState()
    val priv = NIP19.parse(privateKey)
    val pub = NIP19.parse(publicKey)
    val meta by viewModel.subscribeReplaceableEvent(
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
            val contacts by viewModel.subscribeReplaceableEvent(Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(pub.id))).collectAsState()
            val c = contacts
            if (c is LoadingData.Valid && c.data is ReplaceableEvent.Contacts) {
                if (c.data.list.any { it.key == pubkey }) {
                    var expandUnfollowDialog by remember { mutableStateOf(false) }
                    Button(onClick = {
                        expandUnfollowDialog = true
                    }) {
                        Icon(Icons.Filled.Clear, "unfollow")
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
                                    val list = c.data.list.filter { it.key != pubkey }
                                    Misc.postContacts(viewModel, list, priv, pub, onSuccess = {}, onFailure = { url, reason ->
                                        coroutineScope.launch {
                                            Toast.makeText(context, context.getString(R.string.error_failed_to_post, reason), Toast.LENGTH_SHORT).show()
                                        }
                                    })
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
                        Icon(Icons.Filled.Add, "follow")
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
                                    val list =
                                        mutableListOf<ReplaceableEvent.Contacts.Data>().apply {
                                            addAll(c.data.list)
                                            add(ReplaceableEvent.Contacts.Data(key = pubkey))
                                        }
                                    Misc.postContacts(viewModel, list, priv, pub, onSuccess = {},
                                        onFailure = { url, reason ->
                                            coroutineScope.launch {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(
                                                        R.string.error_failed_to_post,
                                                        reason
                                                    ),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        })
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
