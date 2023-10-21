package io.github.tatakinov.treegrove

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.acinq.secp256k1.Hex
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import java.text.SimpleDateFormat
import java.util.Date


@Composable
fun EventView(post : Event, onGetEventMap: () -> Map<String, Set<Event>>,
              onGetChannelMetaData : () -> Map<String, MetaData>,
              onGetUserMetaData : () -> Map<String, MetaData>, onClickImageURL : (String) -> Unit,
              onUserNotFound : (String) -> Unit, onEventNotFound : (Filter) -> Unit,
              onReply: (Event) -> Unit, onHide : (Event) -> Unit, onMute : (Event) -> Unit,
              onMoveChannel : (String) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var name = NIP19.encode("npub", Hex.decode(post.pubkey)).take(16) + "..."
    var url = ""
    var image : ImageBitmap? = null
    var identify = false
    val postProfileData = onGetUserMetaData()
    var doHideMessage by remember {
        mutableStateOf(false)
    }
    var doMuteUser by remember {
        mutableStateOf(false)
    }
    var noteReference by remember {
        mutableStateOf("")
    }
    if (postProfileData.contains(post.pubkey)) {
        val data    = postProfileData[post.pubkey]!!
        if (data.name.isNotEmpty()) {
            name    = data.name
            if (name.length > 16) {
                name    = name.take(16) + "..."
            }
        }
        if (data.pictureUrl.isNotEmpty()) {
            url = data.pictureUrl
        }
        if (data.image.status == DataStatus.Valid) {
            image   = data.image.data
        }
        if (data.nip05.status == DataStatus.Valid) {
            identify = data.nip05.data!!
        }
    }
    // seconds -> milliseconds
    val date = Date(post.createdAt * 1000)
    val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
    val d   = format.format(date)
    var expanded by remember { mutableStateOf(false) }
    val content = post.content
    val annotated =
        buildAnnotatedString {
            var index = 0
            val func = mapOf<Regex, (String) -> Boolean>(
                "^nostr:npub1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex() to label@{ value ->
                    val bech32str = value.substring(6)
                    val (hrp, data) = NIP19.decode(bech32str)
                    val pubkey = Hex.encode(data)
                    if (hrp != "npub") {
                        Log.d("EventView", "invalid bech32.")
                        return@label false
                    } else {
                        if (postProfileData.contains(pubkey) && postProfileData[pubkey]!!.name.isNotEmpty()) {
                            val profile = postProfileData[pubkey]!!
                            withStyle(style = SpanStyle(color = Color.Cyan)) {
                                append(profile.name)
                            }
                        } else if (postProfileData.contains(pubkey)) {
                            withStyle(style = SpanStyle(color = Color.Cyan)) {
                                append(bech32str.take(10) + "...")
                            }
                        } else {
                            onUserNotFound(pubkey)// TODO   username
                        }
                        return@label true
                    }
                },
                "^nostr:note1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex() to label@{ value ->
                    val bech32str = value.substring(6)
                    val (hrp, data) = NIP19.decode(bech32str)
                    val note = String(data)
                    if (hrp != "note") {
                        Log.d("EventView", "invalid bech32.")
                        return@label false
                    } else {
                        pushStringAnnotation(tag = "note", annotation = value)
                        withStyle(style = SpanStyle(color = Color.Green)) {
                            append(value)
                        }
                        pop()
                        return@label true
                    }
                },
                "^nostr:nevent1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex() to label@{ value ->
                    val bech32str = value.substring(6)
                    val (hrp, data) = NIP19.decode(bech32str)
                    try {
                        val tlv = NIP19.parseTLV(data)
                        if (hrp != "nevent") {
                            Log.d("EventView", "invalid bech32.")
                            return@label false
                        }
                        if (tlv[0] == null || tlv[0]!!.isEmpty()) {
                            throw Exception("missing TLV 0 for nevent")
                        }
                        if (tlv[0]!![0].size != 32) {
                            throw Exception("TLV 0 should be 32 bytes")
                        }
                        if (tlv[2] != null && tlv[2]!!.isNotEmpty() && tlv[2]!![0].size != 32) {
                            throw Exception("TLV 2 should be 32 bytes")
                        }
                        if (tlv[3] != null && tlv[3]!!.isNotEmpty() && tlv[3]!![0].size != 4) {
                            throw Exception("TLV 3 should be 4 bytes")
                        }
                        pushStringAnnotation(tag = "note", annotation = value)
                        withStyle(style = SpanStyle(color = Color.Green)) {
                            append(value)
                        }
                        pop()
                        return@label true
                    }
                    catch (e : Exception) {
                        e.printStackTrace()
                        withStyle(style = SpanStyle(color = Color.Green)) {
                            append(e.message)
                        }
                    }
                    return@label true
                },
                // \wは日本語とかにもマッチしてしまうので使えない
                "^https?://[0-9A-Za-z_!?/+\\-_~;.,*&@#$%()'\\[\\]]+\\.(jpg|jpeg|png|webp)(\\?[0-9A-Za-z_!?/+\\-=_~;.,*&@#$%()'\\[\\]]+)?".toRegex() to label@{ value ->
                    val domain = "^https?://[0-9A-Za-z_!?+\\-_~;.,*&@#\\\\$%()'\\[\\]]+/".toRegex()
                        .find(value)?.value
                    "^https?://[0-9A-Za-z_!?/+\\-_~;.,*&@#$%()'\\[\\]]+\\.(jpg|jpeg|png|bmp|webp)".toRegex()
                        .find(value)?.let {
                            pushStringAnnotation(tag = "image", annotation = value)
                            withStyle(style = SpanStyle(color = Color.Cyan)) {
                                append(domain + "..." + it.value.takeLast(6))
                            }
                            pop()
                        }
                    return@label true
                },
                "^https?://[0-9A-Za-z_!?/+\\-=_~;.,*&@#$%()'\\[\\]]+\\.(jpg|jpeg|png|webp)".toRegex() to label@{ value ->
                    val domain = "^https?://[0-9A-Za-z_!?+\\-_~;.,*&@#\\\\$%()'\\[\\]]+/".toRegex()
                        .find(value)?.value
                    pushStringAnnotation(tag = "image", annotation = value)
                    withStyle(style = SpanStyle(color = Color.Cyan)) {
                        append(domain + "..." + value.takeLast(6))
                    }
                    pop()
                    return@label true
                },
                "^https?://[0-9A-Za-z_!?/+\\-=_~;.,*&@#$%()'\\[\\]]+".toRegex() to label@{ value ->
                    pushStringAnnotation(tag = "url", annotation = value)
                    withStyle(style = SpanStyle(color = Color.Cyan)) {
                        append(value)
                    }
                    pop()
                    return@label true
                })
            while (index < content.length) {
                val sub = content.substring(index)
                var isReplaced = false
                for ((k, v) in func) {
                    val match = k.find(sub)
                    if (match != null && v(match.value)) {
                        isReplaced = true
                        index += match.value.length
                        break
                    }
                }
                if (!isReplaced) {
                    append(content[index])
                    index++
                }
            }
        }
    Box {
        Row(modifier = Modifier
            .padding(top = 10.dp, bottom = 10.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        expanded = true
                    }
                )
            }) {
            if (Config.config.displayProfilePicture) {
                if (image == null) {
                    Image(
                        painterResource(id = R.drawable.no_image),
                        "no_image",
                        modifier = Modifier
                            .width(Const.ICON_SIZE)
                            .height(Const.ICON_SIZE)                    )
                } else {
                    Image(image, url,  modifier = Modifier
                        .width(Const.ICON_SIZE)
                        .height(Const.ICON_SIZE))
                }
            }
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        fontSize = 12.sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2
                    )
                    if (identify) {
                        Image(painterResource(id = R.drawable.verify), contentDescription = context.getString(R.string.verify), modifier = Modifier.height(12.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(d, fontSize = 12.sp)
                }
                ClickableText(text = annotated, onClick = { offset ->
                    var tapped = false
                    annotated.getStringAnnotations(tag = "image", start = offset, end = offset)
                        .firstOrNull()?.let {
                        onClickImageURL(it.item)
                        tapped = true
                    }
                    annotated.getStringAnnotations(tag = "url", start = offset, end = offset)
                        .firstOrNull()?.let {
                        uriHandler.openUri(it.item)
                        tapped = true
                    }
                    annotated.getStringAnnotations(tag = "note", start = offset, end = offset).firstOrNull()?.let {
                        noteReference = it.item
                        tapped = true
                    }
                    if (!tapped) {
                        expanded = true
                    }
                }, style = TextStyle(color = contentColorFor(MaterialTheme.colorScheme.background)))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = {
            expanded = false
        }) {
            DropdownMenuItem(text = {
                Text(context.getString(R.string.reply))
            }, onClick = {
                expanded = false
                if (Config.config.privateKey.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.error_set_private_key), Toast.LENGTH_SHORT).show()
                }
                else {
                    onReply(post)
                }
            })
            /* TODO 受信部分が未実装
            DropdownMenuItem(text = {
                Text(context.getString(R.string.hide))
            }, onClick = {
                expanded = false
                if (Config.config.privateKey.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.error_set_private_key), Toast.LENGTH_SHORT).show()
                }
                else {
                    doHideMessage = true
                }
            })
            DropdownMenuItem(text = {
                Text(context.getString(R.string.mute))
            }, onClick = {
                expanded = false
                if (Config.config.privateKey.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.error_set_private_key), Toast.LENGTH_SHORT).show()
                }
                else {
                    doMuteUser = true
                }
            })
             */
        }
        if (doHideMessage) {
            val dismiss : () -> Unit = {
                doHideMessage = false
            }
            AlertDialog(onDismissRequest = {
                dismiss()
            }, confirmButton = {
                onHide(post)
            }, dismissButton = {
                dismiss()
            }, title = {
                Text(context.getString(R.string.hide_title))
            }, text = {
                Text(post.content)
            }
            )
        }
        if (doMuteUser) {
            val dismiss : () -> Unit = {
                doMuteUser = false
            }
            AlertDialog(onDismissRequest = {
                dismiss()
            }, confirmButton = {
                onMute(post)
            }, dismissButton = {
                dismiss()
            }, title = {
                Text(context.getString(R.string.mute_title))
            }, text = {
                Text(name)
            }
            )
        }
        if (noteReference.isNotEmpty()) {
            val onDismiss : () -> Unit = {
                noteReference = ""
            }
            var event : Event? = null
            if (noteReference.startsWith("nostr:note1")) {
                val bech32str = noteReference.substring(6)
                val (hrp, data) = NIP19.decode(bech32str)
                val id = Hex.encode(data)
                event = onGetEventMap()[id]?.firstOrNull()
                if (event == null) {
                    val filter = Filter(
                        ids = listOf(id),
                        kinds = listOf(Kind.Text.num),
                        until = System.currentTimeMillis() / 1000
                    )
                    onEventNotFound(filter)
                }
            }
            else if (noteReference.startsWith("nostr:nevent1")) {
                val bech32str = noteReference.substring(6)
                val (hrp, data) = NIP19.decode(bech32str)
                val tlv = NIP19.parseTLV(data)
                val id = Hex.encode(tlv[0]!![0])
                val relays = tlv[1]?.map { String(it) } ?: mutableListOf<String>()
                val author = tlv[2]?.get(0)?.let {
                    Hex.encode(it)
                } ?: ""
                val kind = tlv[3]?.get(0)?.let {
                    Hex.encode(it).toIntOrNull() ?: 16
                } ?: -1
                var list = onGetEventMap()[id] ?: mutableListOf()
                if (author.isNotEmpty()) {
                    list = list.filter { it.pubkey == author }
                }
                if (kind != -1) {
                    list = list.filter { it.kind == kind }
                }
                event = list.firstOrNull()
                if (event == null) {
                    val filter = Filter(
                        ids = listOf(id),
                        authors = if (author.isEmpty()) { listOf() } else { listOf(author) },
                        kinds = if (kind == -1) { listOf() } else { listOf(kind) },
                        until = System.currentTimeMillis() / 1000
                    )
                    onEventNotFound(filter)
                }
            }
            if (event == null) {
                Dialog(onDismissRequest = onDismiss) {
                    Card {
                        Text(context.getString(R.string.loading), textAlign = TextAlign.Center)
                    }
                }
            }
            else if (event.kind == Kind.ChannelCreation.num) {
                // MetaDataはeventが存在しているならあるはずなので!!を使っていい
                AlertDialog(onDismissRequest = { onDismiss() }, dismissButton = {
                    TextButton(onClick = {
                        onDismiss()
                    }) {
                        Text(context.getString(R.string.cancel))
                    }
                }, confirmButton = {
                    TextButton(onClick = {
                        onMoveChannel(event.id)
                    }) {
                        Text(context.getString(R.string.ok))
                    }
                }, title = {
                    Text(context.getString(R.string.move_channel_title))
                }, text = {
                    val n = onGetChannelMetaData()[event.id]!!.name
                    Text(context.getString(R.string.description_move_channel).format(n))
                })
            }
            else {
                Dialog(onDismissRequest = onDismiss) {
                    Card {
                        EventView(
                            post = event,
                            onGetEventMap = onGetEventMap,
                            onGetChannelMetaData = onGetChannelMetaData,
                            onGetUserMetaData = onGetUserMetaData,
                            onClickImageURL = onClickImageURL,
                            onUserNotFound = onUserNotFound,
                            onEventNotFound = onEventNotFound,
                            onReply = {},
                            onHide = {},
                            onMute = {},
                            onMoveChannel = onMoveChannel
                        )
                    }
                }
            }
        }
    }
}
