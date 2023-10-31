package io.github.tatakinov.treegrove

import android.widget.Toast
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import fr.acinq.secp256k1.Hex
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun EventListView(onGetPostDataList : () -> List<EventData>,
                  onGetEventMap : () -> Map<String, Set<Event>>,
                  onGetLazyListState : () -> LazyListState,
                  onGetChannelMetaData: () -> Map<String, MetaData>,
                  onGetUserMetaData: () -> Map<String, MetaData>,
                  onGetChannelId : () -> String,
                  onClickImageURL : (String) -> Unit, modifier: Modifier,
                  onUserNotFound: (String) -> Unit, onEventNotFound: (Filter) -> Unit,
                  onPost : (Event) -> Unit,
                  onHide: (Event) -> Unit, onMute: (Event) -> Unit,
                  onMoveChannel: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var doPost by remember {
        mutableStateOf(false)
    }
    var doConfirmPost by remember {
        mutableStateOf(false)
    }
    var postMessage by remember { mutableStateOf("") }
    var replyEvent : Event? by remember {
        mutableStateOf(null)
    }
    val isBottom by remember {
        derivedStateOf {
            val info = onGetLazyListState().layoutInfo
            info.visibleItemsInfo.isEmpty() || info.visibleItemsInfo.last().index == info.totalItemsCount - 1
        }
    }
    var height by remember { mutableStateOf(0) }
    LazyColumn(state = onGetLazyListState(), modifier = modifier.onGloballyPositioned {
        if (height != it.size.height) {
            val diffHeight = height - it.size.height
            height = it.size.height
            if (diffHeight > 0 || !isBottom) {
                scope.launch(Dispatchers.Main) {
                    onGetLazyListState().scrollBy(diffHeight.toFloat())
                }
            }
        }
    }) {
        items(items = onGetPostDataList(), key = { it.event.toJSONObject().toString() }) { event ->
            EventView(event.event, onGetEventMap = onGetEventMap,
                onGetChannelMetaData = onGetChannelMetaData,
                onGetUserMetaData = onGetUserMetaData,
                onClickImageURL = onClickImageURL,
                onUserNotFound = onUserNotFound,
                onEventNotFound = onEventNotFound,
                onReply = {e ->
                    replyEvent = e
                    doPost = true
                }, onHide = onHide, onMute = onMute,
                onMoveChannel = onMoveChannel
            )
        }
    }
    if (doPost) {
        PostView(
            onGetReplyEvent = {
                if (replyEvent == null) {
                    return@PostView null
                }
                var name = NIP19.encode("npub", Hex.decode(replyEvent!!.pubkey)).take(16) + "..."
                val postProfileData = onGetUserMetaData()
                if (postProfileData.contains(replyEvent!!.pubkey)) {
                    val data    = postProfileData[replyEvent!!.pubkey]!!
                    if (data.name.isNotEmpty()) {
                        name    = data.name
                        if (name.length > 16) {
                            name    = name.take(16) + "..."
                        }
                    }
                }
                return@PostView name
            },
            onSubmit = { message ->
                postMessage = message
                doPost = false
            },
            onCancel = {
                doPost = false
            }
        )
    } else {
        ActionView(
            modifier = Modifier.height(Const.ICON_SIZE),
            onClickGoToTop = {
                scope.launch(Dispatchers.Main) {
                    onGetLazyListState().scrollToItem(0)
                }
            },
            onClickGoToBottom = {
                scope.launch(Dispatchers.Main) {
                    onGetLazyListState().scrollToItem(onGetLazyListState().layoutInfo.totalItemsCount)
                }
            },
            onClickOpenPostView = {
                if (Config.config.privateKey.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.error_set_private_key),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    doPost = true
                }
            })
    }
    if (postMessage.isNotEmpty()) {
        AlertDialog(onDismissRequest = {
            replyEvent  = null
            postMessage = ""
            doConfirmPost = false
        }, confirmButton = {
            TextButton(onClick = {
                val tags =
                    mutableListOf(
                        listOf(
                            "e",
                            onGetChannelId(),
                            "",
                            "root"
                        )
                    )
                if (replyEvent != null) {
                    tags.add(listOf("e", replyEvent!!.id, "", "reply"))
                    tags.addAll(replyEvent!!.tags.filter {
                        if (it.size >= 2 && it[0] == "p") {
                            return@filter true
                        }
                        return@filter false
                    })
                    if (!tags.any {
                            it.size >= 2 && it[0] == "p" && it[1] == replyEvent!!.pubkey
                        }) {
                        tags.add(listOf("p", replyEvent!!.pubkey))
                    }
                }
                val event = Event(
                    kind = Kind.ChannelMessage.num,
                    content = postMessage,
                    createdAt = System.currentTimeMillis() / 1000,
                    pubkey = Config.config.getPublicKey(),
                    tags = tags
                )
                event.id = Event.generateHash(event, true)
                event.sig = Event.sign(event, Config.config.privateKey)
                onPost(event)
                postMessage = ""
                replyEvent  = null
                doConfirmPost = false
            }) {
                Text(stringResource(R.string.ok))
            }
        }, dismissButton = {
            TextButton(onClick = {
                replyEvent  = null
                postMessage = ""
                doConfirmPost = false
            }) {
                Text(stringResource(R.string.cancel))
            }
        }, title = {
            Text(stringResource(R.string.confirm_title))
        }, text = {
            Text(stringResource(R.string.confirm_text).format(postMessage))
        })
    }
    LaunchedEffect(onGetChannelId()) {
        replyEvent  = null
        doPost = false
    }
}
