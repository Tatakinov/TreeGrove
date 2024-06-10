package io.github.tatakinov.treegrove.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.StreamUpdater
import io.github.tatakinov.treegrove.connection.RelayInfo
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Home(priv: NIP19.Data.Sec?, pub: NIP19.Data.Pub?, tabList: SnapshotStateList<Screen>,
         relayInfoList: State<List<RelayInfo>>, transmittedSize: State<Int>,
         onConnectRelay: () -> Unit,
         onSubscribeStreamEvent: (Filter) -> StreamUpdater<List<Event>>,
         onSubscribeOneShotEvent: (Filter) -> StateFlow<List<Event>>,
         onSubscribeStreamReplaceableEvent: (Filter) -> StreamUpdater<LoadingData<ReplaceableEvent>>,
         onSubscribeReplaceableEvent: (Filter) -> StateFlow<LoadingData<ReplaceableEvent>>,
         onRepost: (Event) -> Unit, onPost: (Int, String, List<List<String>>) -> Unit,
         onNavigateSetting: () -> Unit, onNavigatePost: (Screen, Event?) -> Unit, onNavigateProfile: () -> Unit, onNavigateImage: (String) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabList.size })
    val channelFilter = Filter(kinds = listOf(Kind.ChannelCreation.num))
    val channelUpdater = remember { onSubscribeStreamEvent(channelFilter) }
    val channelList by channelUpdater.flow.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet(modifier = Modifier.padding(end = 100.dp)) {
            val listState = rememberLazyListState()
            var expandRelayList by remember { mutableStateOf(false) }
            var expandPinnedChannelList by remember { mutableStateOf(false) }
            var expandChannelList by remember { mutableStateOf(false) }
            if (pub is NIP19.Data.Pub) {
                val pinnedChannelFilter = Filter(kinds = listOf(Kind.ChannelList.num), authors = listOf(pub.id))
                val pinnedChannelUpdater = remember { onSubscribeStreamEvent(pinnedChannelFilter) }
                val pinnedChannelList by pinnedChannelUpdater.flow.collectAsState()
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
                                Icon(Icons.Default.Settings, "setting")
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        drawerState.close()
                                    }
                                    onNavigateProfile()
                                }
                            ) {
                                Icon(Icons.Default.Person, "profile")
                            }
                        }
                    }
                    item {
                        HorizontalDivider()
                    }
                    item {
                        val transmittedDataSize by transmittedSize
                        var dataSize = transmittedDataSize
                        for (relayInfo in relayInfoList.value) {
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
                            }, maxLines = 1, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                            Button(onClick = {
                            }) {
                                Icon(Icons.Default.Search, "search")
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
                        items(items = relayInfoList.value, key = { it.url }) { info ->
                            NavigationDrawerItem(
                                label = {
                                    Row {
                                        Text(info.url, modifier = Modifier.weight(1f))
                                        if (info.isConnected) {
                                            Icon(Icons.Default.Check, "is_connected")
                                        } else {
                                            Icon(Icons.Default.Clear, "not_connected")
                                        }
                                    }
                                },
                                selected = false,
                                onClick = {
                                    onConnectRelay()
                                })
                        }
                    }
                    item {
                        HorizontalDivider()
                    }
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Home, "home") },
                            label = { Text(stringResource(id = R.string.home)) },
                            selected = false,
                            onClick = {
                                if (tabList.isEmpty() || tabList.none { it is Screen.OwnTimeline }) {
                                    tabList.add(Screen.OwnTimeline(pub.id))
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
                    item {
                        HorizontalDivider()
                    }
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Notifications, "notification") },
                            label = { Text(stringResource(id = R.string.notification)) },
                            selected = false,
                            onClick = {
                                if (tabList.isEmpty() || tabList.none { it is Screen.Notification }) {
                                    tabList.add(Screen.Notification(pub.id))
                                }
                                var index = -1
                                for (i in tabList.indices) {
                                    if (tabList[i] is Screen.Notification) {
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
                            ChannelMenuItem(channel = channel, onSubscribeReplaceableEvent = onSubscribeReplaceableEvent, onAddTab = {
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
                            LoadMoreEventsButton(fetch = pinnedChannelUpdater.fetch)
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
                            ChannelMenuItem(channel = channel, onSubscribeReplaceableEvent = onSubscribeReplaceableEvent, onAddTab = {
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
                            LoadMoreEventsButton(fetch = channelUpdater.fetch)
                        }
                    }
                }
                DisposableEffect(pub.id) {
                    onDispose {
                        pinnedChannelUpdater.unsubscribe()
                    }
                }
            }
            else {
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
                                Icon(Icons.Default.Settings, "setting")
                            }
                        }
                    }
                    item {
                        HorizontalDivider()
                    }
                    item {
                        val transmittedDataSize by transmittedSize
                        var dataSize = transmittedDataSize
                        for (relayInfo in relayInfoList.value) {
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
                            }, maxLines = 1, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                            Button(onClick = {
                            }) {
                                Icon(Icons.Default.Search, "search")
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
                        items(items = relayInfoList.value, key = { it.url }) { info ->
                            NavigationDrawerItem(
                                label = {
                                    Row {
                                        Text(info.url, modifier = Modifier.weight(1f))
                                        if (info.isConnected) {
                                            Icon(Icons.Default.Check, "is_connected")
                                        } else {
                                            Icon(Icons.Default.Clear, "not_connected")
                                        }
                                    }
                                },
                                selected = false,
                                onClick = {
                                    onConnectRelay()
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
                            ChannelMenuItem(channel = channel, onSubscribeReplaceableEvent = onSubscribeReplaceableEvent, onAddTab = {
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
                            LoadMoreEventsButton(fetch = channelUpdater.fetch)
                        }
                    }
                }
            }
        }
    }) {
        if (tabList.isNotEmpty()) {
            BackHandler(true) {
                tabList.removeAt(pagerState.currentPage)
            }
            Scaffold(floatingActionButton = {
                if (tabList.isNotEmpty()) {
                    FloatingActionButton(onClick = {
                        onNavigatePost(tabList[pagerState.currentPage], null)
                    }) {
                        Icon(Icons.Default.Create, "post")
                    }
                }
            }, bottomBar = {
                if (tabList.isNotEmpty()) {
                    val scrollState = rememberScrollState()
                    val selectedTabIndex by remember { derivedStateOf { pagerState.currentPage } }
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        scrollState = scrollState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                                OwnTimeline(priv = priv, pub = pub, screen.id,
                                    onSubscribeStreamEvent = onSubscribeStreamEvent,
                                    onSubscribeStreamReplaceableEvent = onSubscribeStreamReplaceableEvent,
                                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                                    onRepost = onRepost, onPost = onPost,
                                    onNavigate = {
                                    onNavigatePost(screen, it)
                                }, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
                            }

                            is Screen.Timeline -> {
                                Timeline(priv = priv, pub = pub, screen.id,
                                    onSubscribeStreamEvent = onSubscribeStreamEvent,
                                    onSubscribeStreamReplaceableEvent = onSubscribeStreamReplaceableEvent,
                                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                                    onRepost = onRepost, onPost = onPost,
                                    onNavigate = {
                                        onNavigatePost(screen, it)
                                    }, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
                            }

                            is Screen.Channel -> {
                                Channel(priv = priv, pub = pub, id = screen.id, pubkey = screen.pubkey,
                                    onSubscribeStreamEvent = onSubscribeStreamEvent,
                                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                                    onRepost = onRepost, onPost = onPost,
                                    onNavigate = {
                                        onNavigatePost(screen, it)
                                    }, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
                            }

                            is Screen.EventDetail -> {
                                EventDetail(
                                    priv = priv, pub = pub,
                                    id = screen.id,
                                    pubkey = screen.pubkey,
                                    onSubscribeStreamEvent = onSubscribeStreamEvent,
                                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                                    onRepost = onRepost, onPost = onPost,
                                    onNavigate = {
                                        onNavigatePost(screen, it)
                                    },
                                    onAddScreen = onAddScreen,
                                    onNavigateImage = onNavigateImage
                                )
                            }

                            is Screen.ChannelEventDetail -> {
                                ChannelEventDetail(
                                    priv = priv,
                                    pub = pub,
                                    id = screen.id,
                                    pubkey = screen.pubkey,
                                    channelID = screen.channelID,
                                    onSubscribeStreamEvent = onSubscribeStreamEvent,
                                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                                    onRepost = onRepost, onPost = onPost,
                                    onNavigate = {
                                        onNavigatePost(screen, it)
                                    },
                                    onAddScreen = onAddScreen,
                                    onNavigateImage = onNavigateImage
                                )
                            }

                            is Screen.Notification -> {
                                Notification(priv = priv, pub = pub,
                                    onSubscribeStreamEvent = onSubscribeStreamEvent,
                                    onSubscribeReplaceableEvent = onSubscribeReplaceableEvent,
                                    onSubscribeOneShotEvent = onSubscribeOneShotEvent,
                                    onRepost = onRepost, onPost = onPost,
                                    onNavigate = {
                                    onNavigatePost(screen, it)
                                }, onAddScreen = onAddScreen, onNavigateImage = onNavigateImage)
                            }
                        }
                    }
                }
            }
        }
        else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SwipeRight, "swipe to open menu")
                Text(text = stringResource(id = R.string.description_swipe_to_open_menu))
            }
        }
    }
    LaunchedEffect(pub) {
        if (pub is NIP19.Data.Pub) {
            for (i in tabList.indices) {
                if (tabList[i] is Screen.OwnTimeline) {
                    tabList[i] = Screen.OwnTimeline(pub.id)
                }
                else if (tabList[i] is Screen.Notification) {
                    tabList[i] = Screen.Notification(pub.id)
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
            for (i in tabList.indices) {
                if (tabList[i] is Screen.Notification) {
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
