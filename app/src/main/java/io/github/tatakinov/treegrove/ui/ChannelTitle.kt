package io.github.tatakinov.treegrove.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ChannelTitle(viewModel: TreeGroveViewModel, id: String, name: String, about: String?) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val privateKey by viewModel.privateKeyFlow.collectAsState()
    val priv = NIP19.parse(privateKey)
    val publicKey by viewModel.publicKeyFlow.collectAsState()
    val pub = NIP19.parse(publicKey)
    var expandAbout by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                expandAbout = !expandAbout
            }, modifier = Modifier.weight(1f)) {
                Text(name)
            }
            if (priv is NIP19.Data.Sec && pub is NIP19.Data.Pub) {
                val pinListFilter = Filter(kinds = listOf(Kind.ChannelList.num), authors = listOf(pub.id))
                val pinList by viewModel.subscribeReplaceableEvent(pinListFilter).collectAsState()
                val p = pinList
                if (p is LoadingData.Valid && p.data is ReplaceableEvent.ChannelList) {
                    if (p.data.list.contains(id)) {
                        Button(onClick = {
                            val tags = mutableListOf<List<String>>().apply {
                                addAll(p.data.list.filter { it != id }.map { listOf("e", it) })
                            }
                            val pinEvent = Event(
                                kind = Kind.ChannelList.num,
                                content = "",
                                createdAt = System.currentTimeMillis() / 1000,
                                pubkey = pub.id,
                                tags = tags
                            )
                            pinEvent.id = Event.generateHash(pinEvent, false)
                            pinEvent.sig = Event.sign(pinEvent, priv.id)
                            viewModel.post(pinEvent, onSuccess = {}, onFailure = { url, reason ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.error_failed_to_post),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            })
                        }) {
                            Icon(Icons.Default.Remove, "unpin")
                        }
                    }
                    else {
                        Button(onClick = {
                            val tags = mutableListOf<List<String>>().apply {
                                addAll(p.data.list.map { listOf("e", it) })
                                add(listOf("e", id))
                            }
                            val pinEvent = Event(
                                kind = Kind.ChannelList.num,
                                content = "",
                                createdAt = System.currentTimeMillis() / 1000,
                                pubkey = pub.id,
                                tags = tags
                            )
                            pinEvent.id = Event.generateHash(pinEvent, false)
                            pinEvent.sig = Event.sign(pinEvent, priv.id)
                            viewModel.post(pinEvent, onSuccess = {}, onFailure = { url, reason ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.error_failed_to_post),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            })
                        }) {
                            Icon(Icons.Default.Add, "pin")
                        }
                    }
                }
                else {
                    Button(onClick = {
                        val tags = mutableListOf<List<String>>()
                        tags.add(listOf("e", id))
                        val pinEvent = Event(
                            kind = Kind.ChannelList.num,
                            content = "",
                            createdAt = System.currentTimeMillis() / 1000,
                            pubkey = pub.id,
                            tags = tags
                        )
                        pinEvent.id = Event.generateHash(pinEvent, false)
                        pinEvent.sig = Event.sign(pinEvent, priv.id)
                        viewModel.post(pinEvent, onSuccess = {}, onFailure = { url, reason ->
                            coroutineScope.launch(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error_failed_to_post),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                    }) {
                        Icon(Icons.Default.Add, "pin")
                    }
                }
            }
        }
        if (!about.isNullOrEmpty() && expandAbout) {
            HorizontalDivider()
            Text(about)
        }
    }
}
