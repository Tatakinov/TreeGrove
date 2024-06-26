package io.github.tatakinov.treegrove.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.Misc
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.StreamUpdater
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun Profile(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?,
            onSubscribeStreamEvent: (Filter) -> StreamUpdater<List<Event>>,
            onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
            onPost: (Int, String, List<List<String>>) -> Unit,
            onNavigate: () -> Unit) {
    if (pub is NIP19.Data.Pub) {
        val metaDataFilter = Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(pub.id))
        val metaData by onSubscribeReplaceableEvent(metaDataFilter).collectAsState()
        var expandFolloweeList by remember { mutableStateOf(false) }
        val followeeFilter = Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(pub.id))
        val followeeListEvent by onSubscribeReplaceableEvent(followeeFilter)
            .collectAsState()
        var expandFollowerList by remember { mutableStateOf(false) }
        val followerFilter =
            Filter(kinds = listOf(Kind.Contacts.num), tags = mapOf("p" to listOf(pub.id)))
        val followerUpdater = onSubscribeStreamEvent(followerFilter)
        val followerListEvent by followerUpdater.flow.collectAsState()
        var name by remember { mutableStateOf("") }
        var about by remember { mutableStateOf("") }
        var picture by remember { mutableStateOf("") }
        var nip05 by remember { mutableStateOf("") }
        val followeeList = remember { mutableStateListOf<ReplaceableEvent.Contacts.Data>() }
        val listState = rememberLazyListState()

        Column(modifier = Modifier.fillMaxHeight()) {
            LazyColumn(
                state = listState, modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    TextField(
                        label = {
                            Text(stringResource(id = R.string.name))
                        },
                        value = name,
                        onValueChange = {
                            name = it.replace("\n", "")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                item {
                    TextField(label = {
                        Text(stringResource(id = R.string.about))
                    }, value = about, onValueChange = {
                        about = it
                    })
                }
                item {
                    TextField(
                        label = {
                            Text(stringResource(id = R.string.picture_url))
                        },
                        value = picture,
                        onValueChange = {
                            picture = it.replace("\n", "")
                        },
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                item {
                    TextField(
                        label = {
                            Text(stringResource(id = R.string.nip05_address))
                        },
                        value = nip05,
                        onValueChange = {
                            nip05 = it.replace("\n", "")
                        },
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
                item {
                    TextButton(
                        onClick = { expandFolloweeList = !expandFolloweeList },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.followee))
                    }
                }
                if (expandFolloweeList) {
                    item {
                        HorizontalDivider()
                    }
                    if (followeeList.isEmpty()) {
                        item {
                            Button(onClick = {
                                if (followeeList.isEmpty()) {
                                    followeeList.add(
                                        ReplaceableEvent.Contacts.Data(key = pub.id, relay = "")
                                    )
                                }
                            }) {
                                Text(stringResource(id = R.string.follow_self))
                            }
                        }
                    } else {
                        items(items = followeeList.distinctBy { it.key }, key = { item ->
                            "followee@${item.key}"
                        }) { item ->
                            val pubKey = item.key
                            val filter =
                                Filter(
                                    kinds = listOf(Kind.Metadata.num),
                                    authors = listOf(pubKey)
                                )
                            val meta by onSubscribeReplaceableEvent(filter)
                                .collectAsState()
                            val m = meta
                            val n =
                                if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
                                    m.data.name
                                } else {
                                    pubKey
                                }
                            HorizontalDivider()
                            Row {
                                Text(modifier = Modifier.weight(1f), text = n)
                                Button(onClick = {
                                    followeeList.remove(item)
                                }) {
                                    Icon(Icons.Default.PersonRemove, "unfollow")
                                }
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                }
                item {
                    TextButton(
                        onClick = { expandFollowerList = !expandFollowerList },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.follower))
                    }
                }
                if (expandFollowerList) {
                    items(items = followerListEvent.distinctBy { it.pubkey }, key = { item ->
                        "follower@${item.pubkey}"
                    }) { item ->
                        val filter =
                            Filter(
                                kinds = listOf(Kind.Metadata.num),
                                authors = listOf(item.pubkey)
                            )
                        val meta by onSubscribeReplaceableEvent(filter)
                            .collectAsState()
                        val m = meta
                        val n =
                            if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
                                m.data.name
                            } else {
                                item.pubkey
                            }
                        HorizontalDivider()
                        Row {
                            Text(modifier = Modifier.weight(1f), text = n)
                            if (followeeList.isEmpty() || followeeList.none { it.key == item.pubkey }) {
                                Button(onClick = {
                                    followeeList.add(ReplaceableEvent.Contacts.Data(key = item.pubkey))
                                }) {
                                    Icon(Icons.Default.PersonAdd, "follow")
                                }
                            }
                        }
                    }
                    item {
                        HorizontalDivider()
                    }
                    item {
                        LoadMoreEventsButton(fetch = followerUpdater.fetch)
                    }
                }
            }
            if (priv is NIP19.Data.Sec) {
                Button(onClick = {
                    val json = JSONObject()
                    json.put("name", name)
                    json.put("about", about)
                    json.put("picture", picture)
                    json.put("nip05", nip05)
                    onPost(Kind.Metadata.num, json.toString(), listOf())
                    val tags = mutableListOf<List<String>>()
                    for (tag in followeeList) {
                        tags.add(listOf("p", tag.key, tag.relay, tag.petname))
                    }
                    onPost(Kind.Contacts.num, "", tags)
                    onNavigate()
                }) {
                    Text(stringResource(id = R.string.save))
                }
            }
        }

        LaunchedEffect(metaData) {
            val m = metaData
            if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
                val data = m.data
                name = data.name
                about = data.about
                picture = data.picture
                nip05 = data.nip05.domain
            }
        }
        LaunchedEffect(followeeListEvent) {
            val c = followeeListEvent
            if (c is LoadingData.Valid && c.data is ReplaceableEvent.Contacts) {
                val data = c.data
                followeeList.clear()
                followeeList.addAll(data.list.distinctBy { it.key })
            }
        }
        DisposableEffect(pub.id) {
            onDispose {
                followerUpdater.unsubscribe()
            }
        }
    }
}
