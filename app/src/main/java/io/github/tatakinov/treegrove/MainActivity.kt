package io.github.tatakinov.treegrove

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.acinq.secp256k1.Hex
import fr.acinq.secp256k1.Secp256k1
import io.github.tatakinov.treegrove.connection.RelayConfig
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Keys
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import io.github.tatakinov.treegrove.ui.theme.TreeGroveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = TreeGroveViewModel(UserPreferencesRepository(dataStore))
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModel.connectRelay()
            }
        })
        setContent {
            TreeGroveTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Main(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun Main(viewModel: TreeGroveViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            Home(viewModel, onNavigateSetting = {
                navController.navigate("setting")
            }, onNavigatePost = { s, event ->
                val e = URLEncoder.encode(event?.toJSONObject().toString() ?: "", "UTF-8")
                when (s) {
                    is Screen.OwnTimeline -> {
                        navController.navigate("post?class=OwnTimeline&id=${s.id}&event=${e}")
                    }
                    is Screen.Timeline -> {
                        navController.navigate("post?class=Timeline&id=${s.id}&event=${e}")
                    }
                    is Screen.Channel -> {
                        navController.navigate("post?class=Channel&id=${s.id}&pubKey=${s.pubKey}&event=${e}")
                    }
                }
            }, onNavigateProfile = {
                navController.navigate("profile")
            }, onNavigateImage = { url ->
                navController.navigate("image?url=${URLEncoder.encode(url, "UTF-8")}")
            })
        }
        composable("setting") {
            Setting(viewModel, onNavigate = {
                navController.popBackStack()
            })
        }
        composable("profile") {
            Profile(viewModel, onNavigate = {
                navController.popBackStack()
            })
        }
        composable("image?url={url}") {
            val url = URLDecoder.decode(it.arguments?.getString("url") ?: "", "UTF-8")
            if (url.isNotEmpty()) {
                ImageViewer(viewModel, url)
            }
            else {
                Toast.makeText(context, stringResource(id = R.string.error_no_valid_URL), Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }
        composable("post?class={class}&id={id}&pubKey={pubKey}&event={event}") {
            val c = it.arguments?.getString("class") ?: ""
            val id = it.arguments?.getString("id") ?: ""
            val pubKey = it.arguments?.getString("pubKey") ?: ""
            val e = URLDecoder.decode(it.arguments?.getString("event") ?: "", "UTF-8")
            val event = try {
                val json = JSONObject(e)
                Event.parse(json)
            }
            catch (e: JSONException) {
                null
            }
            val screen = when (c) {
                "OwnTimeline" -> { Screen.OwnTimeline(id) }
                "Timeline" -> { Screen.Timeline(id) }
                "Channel" -> {
                    if (pubKey.isNotEmpty()) {
                        Screen.Channel(id, pubKey)
                    }
                    else {
                        null
                    }
                }
                else -> { null }
            }
            if (screen != null) {
                Post(viewModel, screen, event, onNavigate = {
                    navController.popBackStack()
                })
            }
            else {
                Toast.makeText(context, stringResource(id = R.string.error_failed_to_open_post_screen), Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }
    }
}

@Composable
fun ImageViewer(viewModel: TreeGroveViewModel, url: String) {
    val data by viewModel.fetchImage(url).collectAsState()
    when (val d = data) {
        is LoadingData.Valid -> {
            val bitmap = BitmapFactory.decodeByteArray(d.data, 0, d.data.size)
            if (bitmap != null) {
                val image = bitmap.asImageBitmap()
                Image(bitmap = image, contentDescription = "image")
            }
            else {
                Text(stringResource(id = R.string.error_invalid_image), modifier = Modifier.fillMaxSize())
            }
        }
        is LoadingData.Invalid -> {
            Text(stringResource(id = R.string.error_invalid_data, d.reason))
        }
        is LoadingData.Loading -> {
            Text(stringResource(id = R.string.loading))
        }
        is LoadingData.NotLoading -> {
            Text(stringResource(id = R.string.not_loading))
        }
    }
}

sealed class Screen(val icon : ImageVector) {
    abstract val id: String

    data class OwnTimeline(override val id: String): Screen(icon = Icons.Filled.Done)
    data class Timeline(override val id: String): Screen(icon = Icons.Filled.Done)
    data class Channel(override val id: String, val pubKey: String): Screen(icon = Icons.Filled.Add)
}

@Composable
fun ChannelMenuItem(viewModel: TreeGroveViewModel, channel: Event, onAddTab: () -> Unit) {
    val metaDataEvent by viewModel.subscribeReplaceableEvent(
        Filter(
            kinds = listOf(
                Kind.ChannelMetadata.num
            ), authors = listOf(channel.pubkey),
            tags = mapOf("e" to listOf(channel.id))
        )
    ).collectAsState()
    val metaData = if (metaDataEvent is LoadingData.Valid) {
        metaDataEvent
    }
    else {
        val r = ReplaceableEvent.parse(channel)
        if (r != null) {
            LoadingData.Valid(r)
        }
        else {
            LoadingData.Invalid(LoadingData.Reason.ParseError)
        }
    }
    if (metaData is LoadingData.Valid && metaData.data is ReplaceableEvent.ChannelMetaData) {
        NavigationDrawerItem(label = {
            Text(metaData.data.name)
        },
            selected = false,
            onClick = onAddTab)
    }
}

@Composable
fun LoadMoreEventsButton(viewModel: TreeGroveViewModel, filter: Filter) {
    val coroutineScope = rememberCoroutineScope()
    TextButton(onClick = {
        viewModel.fetchPastPost(filter)
    }) {
        Text(stringResource(id = R.string.load_more), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Home(viewModel: TreeGroveViewModel, onNavigateSetting: () -> Unit, onNavigatePost: (Screen, Event?) -> Unit, onNavigateProfile: () -> Unit, onNavigateImage: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val publicKey by viewModel.publicKeyFlow.collectAsState()
    val relayConfigList by viewModel.relayConfigListFlow.collectAsState()
    val tabList = remember { viewModel.tabList }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabList.size })
    val channelFilter = Filter(kinds = listOf(Kind.ChannelCreation.num))
    val channelList by viewModel.subscribeStreamEvent(StreamFilter(id = "channel", filter = channelFilter)).collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    BackHandler(tabList.isNotEmpty()) {
        val filter = when (val screen = tabList.removeAt(pagerState.currentPage)) {
            is Screen.OwnTimeline -> {
                StreamFilter(id = "own@${screen.id}", filter = Filter())
            }
            is Screen.Timeline -> {
                StreamFilter(id = "timeline@${screen.id}", Filter(kinds = listOf(Kind.Text.num), authors = listOf(screen.id)))
            }
            is Screen.Channel -> {
                StreamFilter(id = "channel@${screen.id}", Filter(kinds = listOf(Kind.ChannelMessage.num), tags = mapOf("e" to listOf(screen.id))))
            }
        }
        viewModel.unsubscribeStreamEvent(filter)
    }
    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        val relayInfoList by viewModel.relayInfoListFlow.collectAsState()
        ModalDrawerSheet(modifier = Modifier.padding(end = 100.dp)) {
            val listState = rememberLazyListState()
            var expandRelayList by remember { mutableStateOf(false) }
            var expandPinnedChannelList by remember { mutableStateOf(false) }
            var expandChannelList by remember { mutableStateOf(false) }
            val pub = NIP19.parse(publicKey)
            val pinnedChannelFilter = if (pub is NIP19.Data.Pub) {
                StreamFilter(id = "pinned_channel", filter = Filter(kinds = listOf(Kind.ChannelList.num), authors = listOf(pub.id)))
            }
            else {
                StreamFilter(id = "invalid", filter = Filter())
            }
            val pinnedChannelList by viewModel.subscribeStreamEvent(pinnedChannelFilter).collectAsState()
            LazyColumn(state = listState) {
                item {
                    Row {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.close()
                                }
                                onNavigateSetting()
                            }
                        ) {
                            Icon(Icons.Filled.Settings, "setting")
                        }
                        if (pub is NIP19.Data.Pub) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        drawerState.close()
                                    }
                                    onNavigateProfile()
                                }
                            ) {
                                Icon(Icons.Filled.Person, "profile")
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                }
                item {
                    val transmittedDataSize by viewModel.transmittedSizeFlow.collectAsState()
                    var dataSize = transmittedDataSize
                    for (relayInfo in relayInfoList) {
                        dataSize += relayInfo.transmittedSize
                    }
                    lateinit var unit: String
                    var s: Double = dataSize.toDouble()
                    if (s < 1024) {
                        unit = "B"
                    } else if (s < 1024 * 1024) {
                        unit = "kB"
                        s /= 1024
                    } else if (s < 1024 * 1024 * 1024) {
                        unit = "MB"
                        s /= 1024 * 1024
                    } else {
                        unit = "GB"
                        s /= 1024 * 1024 * 1024
                    }
                    val text: String = if (unit == "B") {
                        "%.0f%s".format(s, unit)
                    } else {
                        "%.1f%s".format(s, unit)
                    }
                    Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                item {
                    HorizontalDivider()
                }
                item {
                    Row {
                        var searchWord by remember { mutableStateOf("") }
                        TextField(value = searchWord, onValueChange = {
                            searchWord = it.replace("\n", "")
                        }, modifier = Modifier.weight(1f))
                        Button(onClick = {
                        }) {
                            Icon(Icons.Filled.Search, "search")
                        }
                    }
                }
                item {
                    HorizontalDivider()
                }
                item {
                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(id = R.string.relay_connection_status))
                        },
                        selected = false,
                        onClick = {
                            expandRelayList = !expandRelayList
                        })
                }
                if (expandRelayList) {
                    item {
                        HorizontalDivider()
                    }
                    items(items = relayInfoList, key = { it.url }) { info ->
                        NavigationDrawerItem(
                            label = {
                                Row {
                                    Text(info.url, modifier = Modifier.weight(1f))
                                    if (info.isConnected) {
                                        Icon(Icons.Filled.Check, "is_connected")
                                    } else {
                                        Icon(Icons.Filled.Clear, "not_connected")
                                    }
                                }
                            },
                            selected = false,
                            onClick = {
                                viewModel.connectRelay()
                            })
                    }
                }
                if (publicKey.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                    }
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Home, "home") },
                            label = { Text(stringResource(id = R.string.home)) },
                            selected = false,
                            onClick = {
                                if (tabList.isEmpty() || tabList.none { it is Screen.OwnTimeline }) {
                                    val pub = NIP19.parse(publicKey)
                                    if (pub is NIP19.Data.Pub) {
                                        tabList.add(Screen.OwnTimeline(pub.id))
                                    }
                                }
                                var index = -1
                                for (i in tabList.indices) {
                                    if (tabList[i] is Screen.OwnTimeline) {
                                        index = i
                                        break
                                    }
                                }
                                coroutineScope.launch {
                                    drawerState.close()
                                }
                                if (index >= 0) {
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(index)
                                    }
                                }
                            })
                    }
                }
                item {
                    HorizontalDivider()
                }
                item {
                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(id = R.string.pinned_channel))
                        },
                        selected = false,
                        onClick = { expandPinnedChannelList = !expandPinnedChannelList })
                }
                if (expandPinnedChannelList) {
                    items(items = pinnedChannelList, key = { it.toJSONObject().toString() }) { channel ->
                        ChannelMenuItem(viewModel = viewModel, channel = channel, onAddTab = {
                            if (tabList.isEmpty() || tabList.none { it is Screen.Channel && it.id == channel.id }) {
                                tabList.add(Screen.Channel(channel.id, channel.pubkey))
                            }
                            var index = -1
                            for (i in tabList.indices) {
                                if (tabList[i] is Screen.Channel && tabList[i].id == channel.id) {
                                    index = i
                                    break
                                }
                            }
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            if (index >= 0) {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(index)
                                }
                            }
                        })
                    }
                }
                item {
                    HorizontalDivider()
                }
                item {
                    NavigationDrawerItem(
                        label = {
                            Text(stringResource(id = R.string.list_of_channel))
                        },
                        selected = false,
                        onClick = { expandChannelList = !expandChannelList })
                }
                if (expandChannelList) {
                    item {
                        HorizontalDivider()
                    }
                    items(items = channelList, key = { it.toJSONObject().toString() }) { channel ->
                        ChannelMenuItem(viewModel = viewModel, channel = channel, onAddTab = {
                            if (tabList.isEmpty() || tabList.none { it is Screen.Channel && it.id == channel.id }) {
                                tabList.add(Screen.Channel(channel.id, channel.pubkey))
                            }
                            var index = -1
                            for (i in tabList.indices) {
                                if (tabList[i] is Screen.Channel && tabList[i].id == channel.id) {
                                    index = i
                                    break
                                }
                            }
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            if (index >= 0) {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(index)
                                }
                            }
                        })
                    }
                    item {
                        LoadMoreEventsButton(viewModel = viewModel, filter = channelFilter)
                    }
                }
            }
        }
    }) {
        Scaffold(floatingActionButton = {
            if (tabList.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    onNavigatePost(tabList[pagerState.currentPage], null)
                }) {
                    Icon(Icons.Filled.Create, "post")
                }
            }
        }, bottomBar = {
            val selectedTabIndex by remember { derivedStateOf { pagerState.currentPage } }
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabList.forEachIndexed { index, screen ->
                    Tab(selected = selectedTabIndex == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.scrollToPage(index)
                            }
                        },
                        icon = {
                            Icon(screen.icon, "Tab Icon")
                        })
                }
            }
        }) { padding ->
            Column {
                HorizontalPager(
                    state = pagerState, modifier = Modifier
                        .padding(padding)
                        .weight(1f), userScrollEnabled = false
                ) { index ->
                    val onAddScreen: (Screen) -> Unit = { screen ->
                        val pos = tabList.indexOf(screen)
                        if (pos == -1) {
                            tabList.add(screen)
                            coroutineScope.launch {
                                pagerState.scrollToPage(tabList.size - 1)
                            }
                        } else {
                            coroutineScope.launch {
                                pagerState.scrollToPage(pos)
                            }
                        }
                    }
                    when (val screen = tabList[index]) {
                        is Screen.OwnTimeline -> {
                            OwnTimeline(viewModel, screen.id, onNavigate = {
                                onNavigatePost(screen, it)
                            }, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
                        }

                        is Screen.Timeline -> {
                            Timeline(viewModel, screen.id, onNavigate = {
                                onNavigatePost(screen, it)
                            }, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
                        }

                        is Screen.Channel -> {
                            Channel(viewModel, screen.id, screen.pubKey, onNavigate = {
                                onNavigatePost(screen, it)
                            }, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(relayConfigList) {
        viewModel.setRelayConfigList(relayConfigList)
        if (relayConfigList.isNotEmpty() && channelList.isEmpty()) {
            viewModel.fetchPastPost(channelFilter)
        }
    }
    LaunchedEffect(publicKey) {
        val pub = NIP19.parse(publicKey)
        if (pub is NIP19.Data.Pub) {
            for (i in tabList.indices) {
                if (tabList[i] is Screen.OwnTimeline) {
                    tabList[i] = Screen.OwnTimeline(pub.id)
                    break
                }
            }
        }
        else {
            for (i in tabList.indices) {
                if (tabList[i] is Screen.OwnTimeline) {
                    tabList.removeAt(i)
                    if (pagerState.currentPage >= i) {
                        pagerState.scrollToPage(pagerState.currentPage - 1)
                    }
                    break
                }
            }
        }
    }
}

@Composable
fun Event1(viewModel: TreeGroveViewModel, event: Event,  onNavigate: ((Event) -> Unit)?, onAddScreen: ((Screen) -> Unit)?, onNavigateImage: ((String) -> Unit)?) {
    val userMetaDataMap = remember { mutableStateMapOf<String, State<LoadingData<ReplaceableEvent>>>() }
    val uriHandler = LocalUriHandler.current
    val date = Date(event.createdAt * 1000)
    val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
    val d   = format.format(date)
    var expanded by remember { mutableStateOf(false) }
    var i = 0
    while (i < event.content.length) {
        val pos = event.content.indexOf("nostr:npub", i)
        if (pos == -1) {
            break
        }
        Log.d("debug", event.content.substring(pos))
        val match = "^nostr:npub1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex().find(event.content.substring(pos))
        if (match != null) {
            val pub = NIP19.parse(match.value.substring(6))
            if (pub is NIP19.Data.Pub && !userMetaDataMap.containsKey(pub.id)) {
                userMetaDataMap[pub.id] = viewModel.subscribeReplaceableEvent(
                    Filter(
                        kinds = listOf(Kind.Metadata.num),
                        authors = listOf(pub.id)
                    )
                ).collectAsState()
            }
            i = pos + match.value.length
        } else {
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
                        pushStringAnnotation(tag = "nevent", annotation = value)
                        withStyle(style = SpanStyle(color = Color.Green)) {
                            append(value)
                        }
                        pop()
                        return@label true
                    }
                    return@label false
                },
                "^nostr:nevent1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+".toRegex() to label@{ value ->
                    val nevent = NIP19.parse(value.substring(6))
                    if (nevent is NIP19.Data.Event) {
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
                        append(content[index])
                        index++
                    }
                }
                else {
                    append(content.substring(index))
                    break
                }
            }
        }
    Box(modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)) {
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
                    val filter = Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(event.pubkey))
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
                Text(text.substring(0, text.length - 1), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 0.dp))
            }
            ClickableText(text = annotated, onClick = { offset ->
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
                        val data = NIP19.parse(it.item)
                        var screen: Screen? = null
                        when (data) {
                            is NIP19.Data.Note -> {
                                // TODO single event
                            }
                            is NIP19.Data.Event -> {
                                when (data.kind) {
                                    Kind.Text.num -> {
                                        // TODO single event
                                    }
                                    Kind.ChannelCreation.num -> {
                                        if (data.author != null) {
                                            screen =
                                                Screen.Channel(id = data.id, pubKey = data.author)
                                        }
                                    }
                                    Kind.ChannelMessage.num -> {
                                        // TODO single event
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
            }, style = TextStyle(color = contentColorFor(MaterialTheme.colorScheme.background)))
        }
        if (onNavigate != null || onAddScreen != null) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (onNavigate != null) {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        onNavigate(event)
                    }) {
                        Text(stringResource(id = R.string.reply))
                    }
                }
                if (onAddScreen != null) {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        onAddScreen(Screen.Timeline(id = event.pubkey))
                    }) {
                        Text(stringResource(id = R.string.view_profile))
                    }
                }
            }
        }
    }
}

@Composable
fun OwnTimeline(viewModel: TreeGroveViewModel, id: String, onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    var expandFolloweeList by remember { mutableStateOf(false) }
    val followeeFilter = Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(id))
    val followeeEvent by viewModel.subscribeReplaceableEvent(followeeFilter).collectAsState()
    val f = followeeEvent
    val followeeList = if (f is LoadingData.Valid && f.data is ReplaceableEvent.Contacts) {
        f.data.list
    }
    else {
        listOf()
    }
    var expandFollowerList by remember { mutableStateOf(false) }
    val followerFilter = Filter(kinds = listOf(Kind.Contacts.num), tags = mapOf("p" to listOf(id)))
    val followerListEvent by viewModel.subscribeStreamEvent(StreamFilter(id = "follower@${id}", filter = followerFilter)).collectAsState()
    if (followeeList.isNotEmpty()) {
        val eventFilter = Filter(kinds = listOf(Kind.Text.num), authors = followeeList.map { it.key })
        val eventList by viewModel.subscribeStreamEvent(StreamFilter(id = "own@${id}", filter = eventFilter)).collectAsState()
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
            item {
                TextButton(onClick = { expandFolloweeList = !expandFolloweeList }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.followee))
                }
            }
            if (expandFolloweeList) {
                items(items = followeeList, key = { "followee@${it.key}" }) {
                    HorizontalDivider()
                    Follow(viewModel, it.key, onAddScreen = onAddScreen)
                }
            }
            item {
                HorizontalDivider()
            }
            item {
                TextButton(onClick = { expandFollowerList = !expandFollowerList }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.follower))
                }
            }
            if (expandFollowerList) {
                items(items = followerListEvent, key = { "follower@${it.pubkey}" }) {
                    HorizontalDivider()
                    Follow(viewModel = viewModel, it.pubkey, onAddScreen = onAddScreen)
                }
            }
            items(items = eventList, key = { event ->
                event.toJSONObject().toString()
            }) { event ->
                HorizontalDivider()
                Event1(viewModel, event = event, onNavigate = onNavigate, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
            }
            item{
                HorizontalDivider()
                LoadMoreEventsButton(viewModel = viewModel, filter = eventFilter)
            }
        }
        LaunchedEffect(id) {
            if (eventList.isEmpty()) {
                viewModel.fetchPastPost(eventFilter)
            }
        }
    }
    else {
        Text(stringResource(id = R.string.follow_someone))
    }
}

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

@Composable
fun Timeline(viewModel: TreeGroveViewModel, id: String, onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    val eventFilter = Filter(kinds = listOf(Kind.Text.num), authors = listOf(id))
    val eventList by viewModel.subscribeStreamEvent(StreamFilter(id = "timeline@${id}", filter = eventFilter)).collectAsState()
    val metaDataFilter = Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(id))
    val metaData by viewModel.subscribeReplaceableEvent(metaDataFilter).collectAsState()
    val listState = rememberLazyListState()
    var expandFolloweeList by remember { mutableStateOf(false) }
    val followeeFilter = Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(id))
    val followeeEvent by viewModel.subscribeReplaceableEvent(followeeFilter).collectAsState()
    val f = followeeEvent
    val followeeListEvent = if (f is LoadingData.Valid && f.data is ReplaceableEvent.Contacts) {
        f.data.list
    }
    else {
        listOf()
    }
    var expandFollowerList by remember { mutableStateOf(false) }
    val followerFilter = Filter(kinds = listOf(Kind.Contacts.num), tags = mapOf("p" to listOf(id)))
    val followerListEvent by viewModel.subscribeStreamEvent(StreamFilter(id = "follower@${id}", filter = followerFilter)).collectAsState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
        val m = metaData
        item {
            Follow(viewModel = viewModel, pubkey = id, onAddScreen = onAddScreen)
        }
        if (m is LoadingData.Valid && m.data is ReplaceableEvent.MetaData) {
            item {
                Text(m.data.about, modifier = Modifier.padding(start = 10.dp, end = 10.dp))
            }
        }
        item {
            HorizontalDivider()
        }
        item {
            TextButton(onClick = { expandFolloweeList = !expandFolloweeList }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(id = R.string.followee))
            }
        }
        if (expandFolloweeList) {
            items(items = followeeListEvent, key = { "followee@${it.key}" }) {
                Follow(viewModel, it.key, onAddScreen = onAddScreen)
            }
        }
        item {
            TextButton(onClick = { expandFollowerList = !expandFollowerList }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(id = R.string.follower))
            }
        }
        if (expandFollowerList) {
            items(items = followerListEvent, key = { "follower@${it.pubkey}" }) {
                Follow(viewModel = viewModel, it.pubkey, onAddScreen = onAddScreen)
            }
        }
        itemsIndexed(items = eventList, key = { index, event ->
            event.toJSONObject().toString()
        }) { index, event ->
            HorizontalDivider()
            Event1(viewModel, event, onNavigate = onNavigate, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
        }
        item {
            HorizontalDivider()
            LoadMoreEventsButton(viewModel = viewModel, filter = eventFilter)
        }
    }
    LaunchedEffect(id) {
        if (eventList.isEmpty()) {
            viewModel.fetchPastPost(eventFilter)
        }
    }
}

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
                            Icon(Icons.Filled.Clear, "unpin")
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
                            Icon(Icons.Filled.Add, "pin")
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
                        Icon(Icons.Filled.Add, "pin")
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

@Composable
fun Channel(viewModel: TreeGroveViewModel, id: String, pubKey: String, onNavigate: (Event?) -> Unit, onAddScreen: (Screen) -> Unit, onNavigateImage: (String) -> Unit) {
    val listState = rememberLazyListState()
    val metaDataFilter = Filter(kinds = listOf(Kind.ChannelMetadata.num), authors = listOf(pubKey), tags = mapOf("e" to listOf(id)))
    val metaData by viewModel.subscribeReplaceableEvent(metaDataFilter).collectAsState()
    val eventFilter = Filter(kinds = listOf(Kind.ChannelMessage.num), tags = mapOf("e" to listOf(id)))
    val eventList by viewModel.subscribeStreamEvent(StreamFilter(id = "channel@${id}", filter = eventFilter)).collectAsState()
    Column {
        val m = metaData
        if (m is LoadingData.Valid && m.data is ReplaceableEvent.ChannelMetaData) {
            ChannelTitle(viewModel = viewModel, id = id, name = m.data.name, about = m.data.about)
        } else {
            ChannelTitle(viewModel = viewModel, id = id, name = id, about = null)
        }
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.fillMaxHeight()) {
            itemsIndexed(items = eventList, key = { index, event ->
                event.toJSONObject().toString()
            }) { index, event ->
                if (index > 0) {
                    HorizontalDivider()
                }
                Event1(
                    viewModel = viewModel,
                    event = event,
                    onNavigate = onNavigate,
                    onAddScreen = onAddScreen,
                    onNavigateImage = onNavigateImage
                )
            }
            item {
                HorizontalDivider()
                LoadMoreEventsButton(viewModel = viewModel, filter = eventFilter)
            }
        }
    }
    LaunchedEffect(id) {
        if (eventList.isEmpty()) {
            viewModel.fetchPastPost(eventFilter)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Post(viewModel: TreeGroveViewModel, screen: Screen, event: Event?, onNavigate: () -> Unit) {
    val context = LocalContext.current
    val privateKey by viewModel.privateKeyFlow.collectAsState()
    val publicKey by viewModel.publicKeyFlow.collectAsState()
    var text by remember { mutableStateOf("") }
    var openConfirmDialog by remember { mutableStateOf(false) }
    val filter = when (screen) {
        is Screen.OwnTimeline -> { Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(screen.id)) }
        is Screen.Timeline -> { Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(screen.id)) }
        is Screen.Channel -> { Filter(kinds = listOf(Kind.ChannelMetadata.num), authors = listOf(screen.pubKey), tags = mapOf("e" to listOf(screen.id))) }
    }
    val metaData by viewModel.subscribeReplaceableEvent(filter).collectAsState()
    Column {
        if (event != null) {
            Event1(viewModel, event, onNavigate = null, onAddScreen = null, onNavigateImage = null)
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
                    val ev = when (screen) {
                        is Screen.OwnTimeline -> {
                            val tags = mutableListOf<List<String>>()
                            if (event != null) {
                                for (tag in event.tags) {
                                    if (tag.size >= 2 && tag[0] == "p") {
                                        tags.add(tag)
                                    }
                                }
                                if (tags.isEmpty() || tags.none { it.size >= 2 && it[0] == "p" && it[1] == event.pubkey }) {
                                    tags.add(listOf("p", event.pubkey))
                                }
                            }
                            val e = Event(kind = Kind.Text.num, content = text, tags = tags,
                                createdAt = System.currentTimeMillis() / 1000, pubkey = pub.id)
                            e.id = Event.generateHash(e, false)
                            e.sig = Event.sign(e, priv.id)
                            e
                        }
                        is Screen.Timeline -> {
                            val tags = mutableListOf<List<String>>()
                            if (event != null) {
                                for (tag in event.tags) {
                                    if (tag.size >= 2 && tag[0] == "p") {
                                        tags.add(tag)
                                    }
                                }
                                if (tags.isEmpty() || tags.none { it.size >= 2 && it[0] == "p" && it[1] == event.pubkey }) {
                                    tags.add(listOf("p", event.pubkey))
                                }
                            }
                            val e = Event(kind = Kind.Text.num, content = text,
                                createdAt = System.currentTimeMillis() / 1000, pubkey = pub.id, tags = listOf(listOf("p", screen.id)))
                            e.id = Event.generateHash(e, false)
                            e.sig = Event.sign(e, priv.id)
                            e
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
                                    if (tag.size >= 2 && tag[0] == "p") {
                                        tags.add(tag)
                                    }
                                }
                                if (tags.isEmpty() || tags.none { it.size >= 2 && it[0] == "p" && it[1] == event.pubkey }) {
                                    tags.add(listOf("p", event.pubkey))
                                }
                                tags.add(listOf("e", event.id, recommendRelay ?: "", "reply"))
                            }
                            tags.add(listOf("e", screen.id, recommendRelay ?: "", "root"))
                            val e = Event(kind = Kind.ChannelMessage.num, content = text,
                                createdAt = System.currentTimeMillis() / 1000, pubkey = pub.id, tags = tags)
                            e.id = Event.generateHash(e, false)
                            e.sig = Event.sign(e, priv.id)
                            e
                        }
                    }
                    viewModel.post(ev, onSuccess = {
                        // TODO
                    }, onFailure = { url, reason ->
                        Toast.makeText(context, context.getString(R.string.error_failed_to_post), Toast.LENGTH_SHORT).show()
                    })
                    onNavigate()
                }
            }) {
                Text(stringResource(id = R.string.ok))
            }
        })
    }
}

@Composable
fun Profile(viewModel: TreeGroveViewModel, onNavigate: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val privateKey by viewModel.privateKeyFlow.collectAsState()
    val publicKey by viewModel.publicKeyFlow.collectAsState()

    if (publicKey.isNotEmpty()) {
        val pub = NIP19.parse(publicKey)
        if (pub is NIP19.Data.Pub) {
            val metaDataFilter = Filter(kinds = listOf(Kind.Metadata.num), authors = listOf(pub.id))
            val metaData by viewModel.subscribeReplaceableEvent(metaDataFilter).collectAsState()
            var expandFolloweeList by remember { mutableStateOf(false) }
            val followeeFilter = Filter(kinds = listOf(Kind.Contacts.num), authors = listOf(pub.id))
            val followeeListEvent by viewModel.subscribeReplaceableEvent(followeeFilter).collectAsState()
            var expandFollowerList by remember { mutableStateOf(false) }
            val followerFilter = Filter(kinds = listOf(Kind.Contacts.num), tags = mapOf("p" to listOf(pub.id)))
            val followerListEvent by viewModel.subscribeStreamEvent(StreamFilter(id = "follower@${pub.id}", filter = followerFilter)).collectAsState()
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
                        TextField(label = {
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
                        TextButton(onClick = { expandFolloweeList = !expandFolloweeList }, modifier = Modifier.fillMaxWidth()) {
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
                        }
                        else {
                            items(items = followeeList.distinctBy { it.key }, key = { item ->
                                "followee@${item.key}"
                            }) { item ->
                                val pubKey = item.key
                                val filter =
                                    Filter(
                                        kinds = listOf(Kind.Metadata.num),
                                        authors = listOf(pubKey)
                                    )
                                val meta by viewModel.subscribeReplaceableEvent(filter)
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
                                        Icon(Icons.Filled.Delete, "delete")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        HorizontalDivider()
                    }
                    item {
                        TextButton(onClick = { expandFollowerList = !expandFollowerList }, modifier = Modifier.fillMaxWidth()) {
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
                            val meta by viewModel.subscribeReplaceableEvent(filter)
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
                                if (followeeList.isEmpty() || followeeList.none {it.key == item.pubkey}) {
                                    Button(onClick = {
                                        followeeList.add(ReplaceableEvent.Contacts.Data(key = item.pubkey))
                                    }) {
                                        Icon(Icons.Filled.Add, "add")
                                    }
                                }
                            }
                        }
                        item {
                            HorizontalDivider()
                        }
                        item {
                            TextButton(onClick = {
                                viewModel.fetchPastPost(followerFilter)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(id = R.string.load_more))
                            }
                        }
                    }
                }
                if (privateKey.isNotEmpty()) {
                    Button(onClick = {
                        val priv = NIP19.parse(privateKey)
                        if (priv is NIP19.Data.Sec) {
                            val json = JSONObject()
                            json.put("name", name)
                            json.put("about", about)
                            json.put("picture", picture)
                            json.put("nip05", nip05)
                            val metaDataEvent = Event(
                                kind = Kind.Metadata.num,
                                content = json.toString(),
                                createdAt = System.currentTimeMillis() / 1000,
                                pubkey = pub.id
                            )
                            metaDataEvent.id = Event.generateHash(metaDataEvent, false)
                            metaDataEvent.sig = Event.sign(metaDataEvent, priv.id)
                            viewModel.post(
                                metaDataEvent,
                                onSuccess = {},
                                onFailure = { url, reason ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.error_failed_to_post),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                })
                            Misc.postContacts(viewModel, followeeList, priv, pub, onSuccess = {},
                                onFailure = { url, reason ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.error_failed_to_post),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                })
                        }
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
        }
    }
}

@Composable
fun Setting(viewModel: TreeGroveViewModel, onNavigate: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope  = rememberCoroutineScope()
    val state   = rememberLazyListState()
    val privateKey by viewModel.privateKeyFlow.collectAsState()
    var inputPrivateKey by remember { mutableStateOf(UserPreferencesRepository.Default.privateKey) }
    val relayList by viewModel.relayConfigListFlow.collectAsState()
    val inputRelayList = remember {
        mutableStateListOf<RelayConfig>().apply {
            addAll(UserPreferencesRepository.Default.relayList)
        }
    }
    val fetchSize by viewModel.fetchSizeFlow.collectAsState()
    var inputFetchSize by remember {
        mutableStateOf(UserPreferencesRepository.Default.fetchSize.toString())
    }
    LaunchedEffect(privateKey) {
        inputPrivateKey = privateKey
    }
    LaunchedEffect(relayList) {
        inputRelayList.clear()
        inputRelayList.addAll(relayList)
    }
    LaunchedEffect(fetchSize) {
        inputFetchSize = fetchSize.toString()
    }
    Column {
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            item {
                TextField(label = {
                    Text(stringResource(R.string.private_key))
                }, value = inputPrivateKey, onValueChange = {
                    inputPrivateKey = it.replace("\n", "")
                }, maxLines = 1, placeholder = {
                    Text(stringResource(R.string.nsec))
                }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
            }
            if (inputPrivateKey.isEmpty()) {
                item {
                    Button(onClick = {
                        inputPrivateKey = NIP19.encode(NIP19.NSEC, Keys.generatePrivateKey())
                    }, content = {
                        Text(stringResource(R.string.generate_private_key), textAlign = TextAlign.Center)
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                TextField(label = {
                    Text(stringResource(R.string.fetch_size_description))
                }, value = inputFetchSize, onValueChange = {
                    inputFetchSize = it
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
            }
            items(count = inputRelayList.size) { index ->
                Column {
                    Row {
                        TextField(label = {
                            Text(stringResource(R.string.relay))
                        }, value = inputRelayList[index].url, onValueChange = {
                            inputRelayList[index] = inputRelayList[index].copy(url = it.replace("\n", ""))
                        }, placeholder = {
                            Text(stringResource(R.string.relay_url))
                        }, modifier = Modifier.weight(1f), maxLines = 2,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                        Button(onClick = {
                            inputRelayList.removeAt(index)
                        }, content = {
                            Image(
                                painterResource(id = R.drawable.close), contentDescription = "Cancel", modifier = Modifier
                                    .width(Const.ACTION_ICON_SIZE)
                                    .height(Const.ACTION_ICON_SIZE))
                        }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var read by remember { mutableStateOf(inputRelayList[index].read) }
                        var write by remember { mutableStateOf(inputRelayList[index].write) }
                        Checkbox(checked = read, onCheckedChange = {
                            // なぜか知らんがrelayList[index]  = relayList[index].copy(read = it)だと更新できない。
                            read    = it
                            inputRelayList[index] = inputRelayList[index].copy(read = read)
                        })
                        Text(stringResource(R.string.read))
                        Checkbox(checked = write, onCheckedChange = {
                            write   = it
                            inputRelayList[index] = inputRelayList[index].copy(write = write)
                        })
                        Text(stringResource(R.string.write))
                    }
                }
            }
            item {
                Button(onClick = {
                    inputRelayList.add(RelayConfig(url = "", read = true, write = true))
                }, content = {
                    Text(stringResource(R.string.add_relay_server))
                }, modifier = Modifier.fillMaxWidth())
            }
        }
        Button(onClick = {
            var valid = true
            var privKey = ""
            var pubKey = ""
            if (inputPrivateKey.isNotEmpty()) {
                if (inputPrivateKey.startsWith(NIP19.NSEC)) {
                    val (hrp, priv) = NIP19.decode(inputPrivateKey)
                    if (hrp.isEmpty() || priv.size != 32 || !Secp256k1.secKeyVerify(priv)) {
                        coroutineScope.launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.error_invalid_secret_key),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        valid = false
                    }
                    privKey = inputPrivateKey
                    pubKey = NIP19.encode(NIP19.NPUB, Keys.getPublicKey(priv))
                }
                else if (inputPrivateKey.startsWith(NIP19.NPUB)) {
                    val pub = NIP19.parse(inputPrivateKey)
                    if (pub !is NIP19.Data.Pub) {
                        valid = false
                    }
                    pubKey = inputPrivateKey
                }
                else {
                    valid = false
                }
            }
            val list    = inputRelayList.filter { it.url.isNotEmpty() && (it.url.startsWith("ws://") || it.url.startsWith("wss://")) }
            if (list.isEmpty()) {
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.error_no_valid_URL), Toast.LENGTH_SHORT).show()
                }
                valid = false
            }
            val size = inputFetchSize.toLongOrNull()
            if (size != null) {
                if (size < 10 || size > 200) {
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.error_invalid_range_of_fetch_size),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    valid = false
                }
            }
            else {
                valid = false
            }

            if (valid) {
                coroutineScope.launch(Dispatchers.IO) {
                    viewModel.updatePreferences(UserPreferences(privateKey = privKey, publicKey = pubKey,
                        relayList = list, fetchSize = inputFetchSize.toLong()))
                }
                Toast.makeText(context, context.getString(R.string.save_config), Toast.LENGTH_SHORT).show()
                onNavigate()
            }
        }, content = {
            Text(stringResource(R.string.save))
        })
    }
}