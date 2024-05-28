package io.github.tatakinov.treegrove.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.secp256k1.Hex
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
import java.text.SimpleDateFormat
import java.util.Date


@Composable
fun TextEvent(viewModel: TreeGroveViewModel, event: Event, onNavigate: ((Event) -> Unit)?, onAddScreen: ((Screen) -> Unit)?,
              onNavigateImage: ((String) -> Unit)?, isFocused: Boolean) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val userMetaDataMap = remember { mutableStateMapOf<String, State<LoadingData<ReplaceableEvent>>>() }
    val pubkeyMap = remember { mutableStateMapOf<String ,String>() }
    val channelMap = remember { mutableStateMapOf<String, String>() }
    val uriHandler = LocalUriHandler.current
    val date = Date(event.createdAt * 1000)
    val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
    val d   = format.format(date)
    var expanded by remember { mutableStateOf(false) }
    var i = 0
    while (i < event.content.length) {
        val pos = event.content.indexOf("nostr:", i)
        if (pos == -1) {
            break
        }
        val npubMatch = "^nostr:npub1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex().find(event.content.substring(pos))
        val noteMatch = "^nostr:note1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex().find(event.content.substring(pos))
        val neventMatch = "^nostr:nevent1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex().find(event.content.substring(pos))
        if (npubMatch != null) {
            val pub = NIP19.parse(npubMatch.value.substring(6))
            if (pub is NIP19.Data.Pub && !userMetaDataMap.containsKey(pub.id)) {
                userMetaDataMap[pub.id] = viewModel.subscribeReplaceableEvent(
                    Filter(
                        kinds = listOf(Kind.Metadata.num),
                        authors = listOf(pub.id)
                    )
                ).collectAsState()
                i = pos + npubMatch.value.length
            }
            else {
                i = pos + 1
            }
        }
        else if (noteMatch != null) {
            val e = NIP19.parse(noteMatch.value.substring(6))
            if (e is NIP19.Data.Note) {
                val f = Filter(ids = listOf(e.id))
                val eventList by viewModel.subscribeOneShotEvent(f).collectAsState()
                if (eventList.isNotEmpty()) {
                    val ev = eventList.first()
                    if (ev.kind == Kind.Text.num) {
                        pubkeyMap[e.id] = ev.pubkey
                    }
                    else if (ev.kind == Kind.ChannelMessage.num) {
                        val eTagList = ev.tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
                        if (eTagList.isNotEmpty()) {
                            val channelFilter =
                                Filter(ids = eTagList, kinds = listOf(Kind.ChannelCreation.num))
                            val channelEvent by viewModel.subscribeOneShotEvent(channelFilter)
                                .collectAsState()
                            if (channelEvent.isNotEmpty()) {
                                pubkeyMap[e.id] = ev.pubkey
                                channelMap[e.id] = channelEvent.first().id
                            }
                        }
                    }
                }
                i = pos + noteMatch.value.length
            }
            else {
                i = pos + 1
            }
        }
        else if (neventMatch != null) {
            val e = NIP19.parse(neventMatch.value.substring(6))
            if (e is NIP19.Data.Event) {
                val ids = listOf(e.id)
                val kinds = if (e.kind != null) {
                    listOf(e.kind)
                }
                else {
                    listOf()
                }
                val authors = if (e.author != null) {
                    listOf(e.author)
                }
                else {
                    listOf()
                }
                val f = Filter(ids = ids, kinds = kinds, authors = authors)
                val eventList by viewModel.subscribeOneShotEvent(f).collectAsState()
                if (eventList.isNotEmpty()) {
                    val ev = eventList.first()
                    if (ev.kind == Kind.ChannelMessage.num) {
                        val eTagList = ev.tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
                        if (eTagList.isNotEmpty()) {
                            val channelFilter =
                                Filter(ids = eTagList, kinds = listOf(Kind.ChannelCreation.num))
                            val channelEvent by viewModel.subscribeOneShotEvent(channelFilter)
                                .collectAsState()
                            if (channelEvent.isNotEmpty()) {
                                pubkeyMap[e.id] = ev.pubkey
                                channelMap[e.id] = channelEvent.first().id
                            }
                        }
                    }
                    else {
                        pubkeyMap[e.id] = ev.pubkey
                    }
                }
                i = pos + neventMatch.value.length
            }
            else {
                i = pos + 1
            }
        }
        else {
            i = pos + 1
        }
    }
    val annotated =
        buildAnnotatedString {
            val func = mapOf<Regex, (String) -> Boolean>(
                "^nostr:npub1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex() to label@{ value ->
                    val bech32 = value.substring(6)
                    val pub = NIP19.parse(bech32)
                    if (pub is NIP19.Data.Pub && userMetaDataMap.containsKey(pub.id)) {
                        val e = userMetaDataMap[pub.id]?.value
                        if (e != null && e is LoadingData.Valid && e.data is ReplaceableEvent.MetaData) {
                            withStyle(style = SpanStyle(color = Color.Cyan)) {
                                append(e.data.name)
                            }
                        }
                        else {
                            withStyle(style = SpanStyle(color = Color.Cyan)) {
                                append(bech32)
                            }
                        }
                        return@label true
                    }
                    return@label false
                },
                "^nostr:note1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex() to label@{ value ->
                    val note = NIP19.parse(value.substring(6))
                    if (note is NIP19.Data.Note) {
                        if (pubkeyMap.containsKey(note.id)) {
                            pushStringAnnotation(tag = "nevent", annotation = value)
                            withStyle(style = SpanStyle(color = Color.Green)) {
                                append(value)
                            }
                            pop()
                            return@label true
                        }
                    }
                    return@label false
                },
                "^nostr:nevent1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex() to label@{ value ->
                    val nevent = NIP19.parse(value.substring(6))
                    if (nevent is NIP19.Data.Event) {
                        if (pubkeyMap.containsKey(nevent.id)) {
                            pushStringAnnotation(tag = "nevent", annotation = value)
                            withStyle(style = SpanStyle(color = Color.Green)) {
                                append(value)
                            }
                            pop()
                            return@label true
                        }
                        else {
                            // TODO invalid nevent?
                        }
                    }
                    return@label true
                },
                "^nostr:nprofile1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex() to label@{ value ->
                    val nevent = NIP19.parse(value.substring(6))
                    if (nevent is NIP19.Data.Profile) {
                        pushStringAnnotation(tag = "nevent", annotation = value)
                        withStyle(style = SpanStyle(color = Color.Green)) {
                            append(value)
                        }
                        pop()
                        return@label true
                    }
                    return@label true
                },
                // \wは日本語とかにもマッチしてしまうので使えない
                "^https?://[0-9A-Za-z_!?/+\\-~;.,*&@#$%()'\\[\\]]+\\.(jpg|jpeg|png|webp)(\\?[0-9A-Za-z_!?/+\\-=~;.,*&@#$%()'\\[\\]]+)?".toRegex() to label@{ value ->
                    val domain = "^https?://[0-9A-Za-z_!?+\\-~;.,*&@#\\\\$%()'\\[\\]]+/".toRegex()
                        .find(value)?.value
                    "^https?://[0-9A-Za-z_!?/+\\-~;.,*&@#$%()'\\[\\]]+\\.(jpg|jpeg|png|bmp|webp)".toRegex()
                        .find(value)?.let {
                            pushStringAnnotation(tag = "image", annotation = value)
                            withStyle(style = SpanStyle(color = Color.Cyan)) {
                                append(domain + "..." + it.value.takeLast(6))
                            }
                            pop()
                        }
                    return@label true
                },
                "^https?://[0-9A-Za-z_!?/+\\-=~;.,*&@#$%()'\\[\\]]+\\.(jpg|jpeg|png|webp)".toRegex() to label@{ value ->
                    val domain = "^https?://[0-9A-Za-z_!?+\\-~;.,*&@#\\\\$%()'\\[\\]]+/".toRegex()
                        .find(value)?.value
                    pushStringAnnotation(tag = "image", annotation = value)
                    withStyle(style = SpanStyle(color = Color.Cyan)) {
                        append(domain + "..." + value.takeLast(6))
                    }
                    pop()
                    return@label true
                },
                "^https?://[0-9A-Za-z_!?/+\\-=~;.,*&@#$%()'\\[\\]]+".toRegex() to label@{ value ->
                    pushStringAnnotation(tag = "url", annotation = value)
                    withStyle(style = SpanStyle(color = Color.Cyan)) {
                        append(value)
                    }
                    pop()
                    return@label true
                })
            val content = event.content
            var index = 0
            val prefixList = listOf("nostr:", "http://", "https://")
            while (index < content.length) {
                val min = prefixList.filter { content.indexOf(it, index) != -1 }
                    .minOfOrNull { content.indexOf(it, index) }
                if (min != null) {
                    if (min > index) {
                        append(content.substring(index, min))
                    }
                    val sub = content.substring(min)
                    var isReplaced = false
                    for ((k, v) in func) {
                        val match = k.find(sub)
                        if (match != null && v(match.value)) {
                            isReplaced = true
                            index = min + match.value.length
                            break
                        }
                    }
                    if (!isReplaced) {
                        append(content[min])
                        index = min + 1
                    }
                }
                else {
                    append(content.substring(index))
                    break
                }
            }
        }
    Box(modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)) {
        Card(colors = CardDefaults.cardColors(
            containerColor = if (isFocused) {
                MaterialTheme.colorScheme.primaryContainer
            }
            else {
                MaterialTheme.colorScheme.surface
            }
        )) {
            Column(modifier = Modifier
                .padding(start = 10.dp, end = 10.dp)
                .clickable { expanded = true }) {
                /*
        名前が短いときは
        hoge             2000/01/01 00:00:00
        となり
        長いときは
        very_very_lon... 2000/01/01 00:00:00
        となるようにしたかったが左からsizeが決まるっぽく
        very_very_long_name 2000/01/01 00...
        となってしまうので仕方無しに右から決まるようにした。
        絶対なんかもっとスマートな解決方法があるはず…
         */
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val filter = Filter(
                            kinds = listOf(Kind.Metadata.num),
                            authors = listOf(event.pubkey)
                        )
                        val metaData by viewModel.subscribeReplaceableEvent(filter).collectAsState()
                        val m = metaData
                        /*
                Rtlのままだと
                00:00:00 2000/01/01
                となるので一時的にLtrに戻す
                 */
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(d, fontSize = 12.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData &&
                            m.data.nip05.identify is LoadingData.Valid && m.data.nip05.identify.data
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                "verified",
                                modifier = Modifier.height(12.dp)
                            )
                        }
                        /*
                Rtlのままだと左端が切れるので一時的にLtrに戻す
                 */
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            val name =
                                if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
                                    m.data.name
                                } else {
                                    val data = Hex.decode(event.pubkey)
                                    NIP19.encode(NIP19.NPUB, data)
                                }
                            Text(
                                name,
                                fontSize = 12.sp,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                        }
                    }
                }
                var text = ""
                for (tag in event.tags) {
                    if (tag.size >= 2 && tag[0] == "p") {
                        val metaData by viewModel.subscribeReplaceableEvent(
                            Filter(
                                kinds = listOf(Kind.Metadata.num),
                                authors = listOf(tag[1])
                            )
                        ).collectAsState()
                        val m = metaData
                        if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
                            text += "@${m.data.name} "
                        }
                    }
                }
                if (text.isNotEmpty()) {
                    Text(
                        text.substring(0, text.length - 1),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 0.dp)
                    )
                }
                ClickableText(
                    text = annotated,
                    onClick = { offset ->
                        var tapped = false
                        annotated.getStringAnnotations(tag = "image", start = offset, end = offset)
                            .firstOrNull()?.let {
                                if (onNavigateImage != null) {
                                    onNavigateImage(it.item)
                                }
                            }
                        annotated.getStringAnnotations(tag = "url", start = offset, end = offset)
                            .firstOrNull()?.let {
                                uriHandler.openUri(it.item)
                                tapped = true
                            }
                        annotated.getStringAnnotations(tag = "nevent", start = offset, end = offset)
                            .firstOrNull()?.let {
                                val data = NIP19.parse(it.item.substring(6))
                                var screen: Screen? = null
                                when (data) {
                                    is NIP19.Data.Note -> {
                                        screen =
                                            Screen.EventDetail(
                                                data.id,
                                                pubkey = pubkeyMap[data.id]!!
                                            )
                                    }

                                    is NIP19.Data.Event -> {
                                        when (data.kind) {
                                            Kind.Text.num -> {
                                                screen = Screen.EventDetail(
                                                    data.id,
                                                    pubkey = pubkeyMap[data.id]!!
                                                )
                                            }

                                            Kind.ChannelCreation.num -> {
                                                if (data.author != null) {
                                                    screen = Screen.Channel(
                                                        id = data.id,
                                                        pubkey = data.author
                                                    )
                                                }
                                            }

                                            Kind.ChannelMessage.num -> {
                                                screen = Screen.ChannelEventDetail(
                                                    id = data.id, pubkey = pubkeyMap[data.id]!!,
                                                    channelID = channelMap[data.id]!!
                                                )
                                            }

                                            else -> {}
                                        }
                                    }

                                    is NIP19.Data.Profile -> {
                                        screen = Screen.Timeline(id = data.id)
                                    }

                                    else -> {}
                                }
                                if (screen != null && onAddScreen != null) {
                                    onAddScreen(screen)
                                }
                            }
                    }, style = TextStyle(color = MaterialTheme.colorScheme.onSurface)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val privateKey by viewModel.privateKeyFlow.collectAsState()
            val publicKey by viewModel.publicKeyFlow.collectAsState()
            val priv = NIP19.parse(privateKey)
            val pub = NIP19.parse(publicKey)
            if (onNavigate != null) {
                DropdownMenuItem(onClick = {
                    expanded = false
                    onNavigate(event)
                }, text = {
                    Text(stringResource(id = R.string.reply))
                })
            }
            if (priv is NIP19.Data.Sec && pub is NIP19.Data.Pub) {
                var expandedConfirmDialog by remember { mutableStateOf(false) }
                DropdownMenuItem(text = {
                    Text(stringResource(id = R.string.repost))
                }, onClick = {
                    expandedConfirmDialog = true
                })
                if (expandedConfirmDialog) {
                    AlertDialog(
                        title = {
                            Text(stringResource(id = R.string.confirm_title))
                        },
                        text = {
                            Text(stringResource(R.string.confirm_repost))
                        },
                        onDismissRequest = {
                            expanded = false
                            expandedConfirmDialog = false
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                expanded = false
                                expandedConfirmDialog = false
                            }) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                expanded = false
                                expandedConfirmDialog = false
                                coroutineScope.launch {
                                    Misc.repost(
                                        viewModel = viewModel,
                                        event = event,
                                        priv = priv,
                                        pub = pub,
                                        onSuccess = {},
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
                                }
                            }) {
                                Text(stringResource(id = R.string.ok))
                            }
                        })
                }
            }
            if (onAddScreen != null) {
                DropdownMenuItem(onClick = {
                    expanded = false
                    onAddScreen(Screen.Timeline(id = event.pubkey))
                }, text = {
                    Text(stringResource(id = R.string.view_profile))
                })
                DropdownMenuItem(onClick = {
                    onAddScreen(Screen.EventDetail(id = event.id, pubkey = event.pubkey))
                }, text = {
                    Text(stringResource(id = R.string.detail))
                })
            }
            DropdownMenuItem(onClick = {
                expanded = false
                val prefix = "nostr:"
                val nip19 = NIP19.toString(event)
                if (nip19.isNotEmpty()) {
                    clipboard.setText(
                        annotatedString = AnnotatedString(
                            prefix + NIP19.toString(
                                event
                            )
                        )
                    )
                    coroutineScope.launch {
                        Toast.makeText(
                            context,
                            context.getString(R.string.copied_nevent, prefix + nip19),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }, text = {
                Text(stringResource(id = R.string.copy_nevent))
            })
        }
    }
}
