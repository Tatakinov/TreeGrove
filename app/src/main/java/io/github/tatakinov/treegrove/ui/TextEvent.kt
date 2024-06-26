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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
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
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP05
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun TextEvent(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?, event: Event,
              onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
              onSubscribeOneShotEvent: (Filter) -> StateFlow<List<Event>>,
              onRepost: ((Event) -> Unit)?, onPost: ((Int, String, List<List<String>>) -> Unit)?,
              onNavigate: ((Event) -> Unit)?, onAddScreen: ((Screen) -> Unit)?,
              onNavigateImage: ((String) -> Unit)?, isFocused: Boolean) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val userMetaDataMap = remember { mutableStateMapOf<String, State<LoadingData<ReplaceableEvent>>>() }
    val pubkeyMap = remember { mutableStateMapOf<String ,String>() }
    val channelMap = remember { mutableStateMapOf<String, String>() }
    val kindMap = remember { mutableStateMapOf<String, Int>() }
    val uriHandler = LocalUriHandler.current
    val d = Date(event.createdAt * 1000)
    val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
    val date   = format.format(d)
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
                userMetaDataMap[pub.id] = onSubscribeReplaceableEvent(
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
            val ev = NIP19.parse(noteMatch.value.substring(6))
            if (ev is NIP19.Data.Note) {
                val f = Filter(ids = listOf(ev.id))
                val eventList by onSubscribeOneShotEvent(f).collectAsState()
                if (eventList.isNotEmpty()) {
                    val e = eventList.first()
                    when (e.kind) {
                        Kind.Text.num -> {
                            pubkeyMap[ev.id] = e.pubkey
                            kindMap[ev.id] = e.kind
                        }
                        Kind.ChannelCreation.num -> {
                            pubkeyMap[ev.id] = e.pubkey
                            kindMap[ev.id] = e.kind
                        }
                        Kind.ChannelMessage.num -> {
                            val ids = if (event.tags.any { it.size >= 4 && it[0] == "e" && it[3] == "root" }) {
                                listOf(event.tags.filter { it.size >= 4 && it[0] == "e" && it[3] == "root" }.map { it[1] }.first())
                            }
                            else {
                                event.tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
                            }
                            if (ids.isNotEmpty()) {
                                val channelFilter =
                                    Filter(ids = ids, kinds = listOf(Kind.ChannelCreation.num))
                                val channelEvent by onSubscribeOneShotEvent(channelFilter)
                                    .collectAsState()
                                if (channelEvent.isNotEmpty()) {
                                    pubkeyMap[ev.id] = e.pubkey
                                    channelMap[ev.id] = channelEvent.first().id
                                    kindMap[ev.id] = e.kind
                                }
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
            val ev = NIP19.parse(neventMatch.value.substring(6))
            if (ev is NIP19.Data.Event) {
                val ids = listOf(ev.id)
                val kinds = if (ev.kind != null) {
                    listOf(ev.kind)
                }
                else {
                    listOf()
                }
                val authors = if (ev.author != null) {
                    listOf(ev.author)
                }
                else {
                    listOf()
                }
                val f = Filter(ids = ids, kinds = kinds, authors = authors)
                val eventList by onSubscribeOneShotEvent(f).collectAsState()
                if (eventList.isNotEmpty()) {
                    val e = eventList.first()
                    when (e.kind) {
                        Kind.Text.num -> {
                            pubkeyMap[ev.id] = e.pubkey
                            kindMap[ev.id] = e.kind
                        }
                        Kind.ChannelCreation.num -> {
                            pubkeyMap[ev.id] = e.pubkey
                            kindMap[ev.id] = e.kind
                        }
                        Kind.ChannelMessage.num -> {
                            val ids = if (event.tags.any { it.size >= 4 && it[0] == "e" && it[3] == "root" }) {
                                listOf(event.tags.filter { it.size >= 4 && it[0] == "e" && it[3] == "root" }.map { it[1] }.first())
                            }
                            else {
                                event.tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
                            }
                            if (ids.isNotEmpty()) {
                                val channelFilter =
                                    Filter(ids = ids, kinds = listOf(Kind.ChannelCreation.num))
                                val channelEvent by onSubscribeOneShotEvent(channelFilter)
                                    .collectAsState()
                                if (channelEvent.isNotEmpty()) {
                                    pubkeyMap[ev.id] = e.pubkey
                                    channelMap[ev.id] = channelEvent.first().id
                                    kindMap[ev.id] = e.kind
                                }
                            }
                        }
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
                        pushStringAnnotation(tag = "nevent", annotation = value)
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
                        pop()
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
                        val metaData by onSubscribeReplaceableEvent(filter).collectAsState()
                        val m = metaData
                        /*
                Rtlのままだと
                00:00:00 2000/01/01
                となるので一時的にLtrに戻す
                 */
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(date, fontSize = 12.sp, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData &&
                            m.data.nip05.identify is LoadingData.Valid && m.data.nip05.identify.data
                        ) {
                            NIP05.ADDRESS_REGEX.find(m.data.nip05.domain)?.let {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        it.groups[2]!!.value,
                                        fontSize = 12.sp,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.CheckCircle,
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
                        val metaData by onSubscribeReplaceableEvent(
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
                        annotated.getStringAnnotations(tag = "image", start = offset, end = offset)
                            .firstOrNull()?.let {
                                if (onNavigateImage != null) {
                                    onNavigateImage(it.item)
                                }
                            }
                        annotated.getStringAnnotations(tag = "url", start = offset, end = offset)
                            .firstOrNull()?.let {
                                uriHandler.openUri(it.item)
                            }
                        annotated.getStringAnnotations(tag = "nevent", start = offset, end = offset)
                            .firstOrNull()?.let {
                                val data = NIP19.parse(it.item.substring(6))
                                var screen: Screen? = null
                                when (data) {
                                    is NIP19.Data.Note -> {
                                        when (kindMap[data.id]) {
                                            Kind.Text.num -> {
                                                screen = Screen.EventDetail(id = data.id, pubkey = pubkeyMap[data.id]!!)
                                            }
                                            Kind.ChannelCreation.num -> {
                                                screen = Screen.Channel(id = data.id, pubkey = pubkeyMap[data.id]!!)
                                            }
                                            Kind.ChannelMessage.num -> {
                                                screen = Screen.ChannelEventDetail(id = data.id, pubkey = pubkeyMap[data.id]!!, channelID = channelMap[data.id]!!)
                                            }
                                        }
                                    }

                                    is NIP19.Data.Event -> {
                                        when (kindMap[data.id]) {
                                            Kind.Text.num -> {
                                                screen = Screen.EventDetail(id = data.id, pubkey = pubkeyMap[data.id]!!)
                                            }
                                            Kind.ChannelCreation.num -> {
                                                screen = Screen.Channel(id = data.id, pubkey = pubkeyMap[data.id]!!)
                                            }
                                            Kind.ChannelMessage.num -> {
                                                screen = Screen.ChannelEventDetail(id = data.id, pubkey = pubkeyMap[data.id]!!, channelID = channelMap[data.id]!!)
                                            }
                                        }
                                    }

                                    is NIP19.Data.Profile -> {
                                        screen = Screen.Timeline(id = data.id)
                                    }

                                    is NIP19.Data.Pub -> {
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
            if (onNavigate != null) {
                DropdownMenuItem(onClick = {
                    expanded = false
                    onNavigate(event)
                }, text = {
                    Text(stringResource(id = R.string.reply))
                }, leadingIcon = {
                    Icon(Icons.AutoMirrored.Default.Reply, "reply")
                })
            }
            if (priv is NIP19.Data.Sec && pub is NIP19.Data.Pub) {
                var expandedRepostDialog by remember { mutableStateOf(false) }
                DropdownMenuItem(text = {
                    Text(stringResource(id = R.string.repost))
                }, leadingIcon = {
                    Icon(Icons.Default.Repeat, "repost")
                }, onClick = {
                    expandedRepostDialog = true
                })
                if (expandedRepostDialog) {
                    AlertDialog(
                        title = {
                            Text(stringResource(id = R.string.confirm_title))
                        },
                        text = {
                            Text(stringResource(R.string.confirm_repost))
                        },
                        onDismissRequest = {
                            expanded = false
                            expandedRepostDialog = false
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                expanded = false
                                expandedRepostDialog = false
                            }) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                expanded = false
                                expandedRepostDialog = false
                                if (onRepost != null) {
                                    onRepost(event)
                                }
                            }) {
                                Text(stringResource(id = R.string.ok))
                            }
                        })
                }
                if (event.pubkey == pub.id) {
                    var expandedDeleteDialog by remember { mutableStateOf(false) }
                    DropdownMenuItem(leadingIcon = {
                        Icon(Icons.Default.Delete, "delete")
                    }, text = {
                        Text(stringResource(id = R.string.delete))
                    }, onClick = {
                        expandedDeleteDialog = true
                    })
                    if (expandedDeleteDialog) {
                        AlertDialog(title = {
                            Text(stringResource(id = R.string.confirm_title))
                        }, text = {
                            Text(stringResource(id = R.string.description_delete, event.content))
                        }, onDismissRequest = {
                            expandedDeleteDialog = false
                        }, dismissButton = {
                            TextButton(onClick = {
                                expandedDeleteDialog = false
                            }) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        }, confirmButton = {
                            TextButton(onClick = {
                                expandedDeleteDialog = false
                                val kind = Kind.EventDeletion.num
                                val content = ""
                                val tags = listOf(listOf("e", event.id))
                                if (onPost != null) {
                                    onPost(kind, content, tags)
                                }
                            }) {
                                Text(stringResource(id = R.string.ok))
                            }
                        })
                    }
                }
            }
            if (onAddScreen != null) {
                DropdownMenuItem(onClick = {
                    expanded = false
                    onAddScreen(Screen.Timeline(id = event.pubkey))
                }, text = {
                    Text(stringResource(id = R.string.view_profile))
                }, leadingIcon = {
                    Icon(Icons.Default.Person, "profile")
                })
                DropdownMenuItem(onClick = {
                    onAddScreen(Screen.EventDetail(id = event.id, pubkey = event.pubkey))
                }, text = {
                    Text(stringResource(id = R.string.detail))
                }, leadingIcon = {
                    Icon(Icons.Default.Info, "detail")
                })
            }
            DropdownMenuItem(text = {
                Text(stringResource(id = R.string.copy_note))
            }, leadingIcon = {
                Icon(Icons.Default.ContentCopy, "copy note")
            }, onClick = {
                expanded = false
                val prefix = "nostr:"
                val note = NIP19.toNote(event)
                if (note.isNotEmpty()) {
                    clipboard.setText(
                        annotatedString = AnnotatedString(
                            prefix + note
                        )
                    )
                    coroutineScope.launch {
                        Toast.makeText(
                            context,
                            context.getString(R.string.copied, prefix + note),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
            DropdownMenuItem(onClick = {
                expanded = false
                val prefix = "nostr:"
                val nevent = NIP19.toNevent(event)
                if (nevent.isNotEmpty()) {
                    clipboard.setText(
                        annotatedString = AnnotatedString(
                            prefix + nevent
                        )
                    )
                    coroutineScope.launch {
                        Toast.makeText(
                            context,
                            context.getString(R.string.copied, prefix + nevent),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }, text = {
                Text(stringResource(id = R.string.copy_nevent))
            }, leadingIcon = {
                Icon(Icons.Default.ContentCopy, "copy nevent")
            })
            DropdownMenuItem(onClick = {
                expanded = false
                clipboard.setText(
                    annotatedString = AnnotatedString(
                        event.content
                    )
                )
                coroutineScope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.copied, event.content),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }, text = {
                Text(stringResource(id = R.string.copy_content))
            }, leadingIcon = {
                Icon(Icons.Default.ContentCopy, "copy content")
            })
        }
    }
}
