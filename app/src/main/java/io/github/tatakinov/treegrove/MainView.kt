package io.github.tatakinov.treegrove

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(onNavigate : () -> Unit, networkViewModel: NetworkViewModel = viewModel()) {
    val scope   = rememberCoroutineScope()
    val channelId = networkViewModel.channelId.observeAsState()
    val channelListState = rememberLazyListState()
    val channelDataList = networkViewModel.channelList.observeAsState()
    val postListState = rememberLazyListState()
    val postDataList = networkViewModel.postDataList.observeAsState()
    val eventMap = networkViewModel.eventMap.observeAsState()
    val channelMetaData = networkViewModel.channelMetaData.observeAsState()
    val userMetaData = networkViewModel.userMetaData.observeAsState()
    val context = LocalContext.current
    val transmittedDataSize = networkViewModel.transmittedDataSize.observeAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var imageURL by remember { mutableStateOf("") }
    val image = networkViewModel.image.observeAsState()
    var expandedChannelAbout by remember {
        mutableStateOf(false)
    }
    var searchChannel by remember { mutableStateOf("") }
    val manager = LocalFocusManager.current
    var showRelayConnectionStatus by remember { mutableStateOf(false) }
    val relayConnectionStatus = networkViewModel.relayConnectionStatus.observeAsState()
    val pinnedChannelList = networkViewModel.pinnedChannelList.observeAsState()
    val postFirstVisibleIndex = remember { mutableMapOf<String, Int>() }
    var isNewPostReceived by remember { mutableStateOf(false) }
    var isNewPostsReceived by remember { mutableStateOf(false) }
    var isChannelSwitched by remember { mutableStateOf(false) }
    val isLatestBefore by remember {
        derivedStateOf {
            val info = postListState.layoutInfo
            info.visibleItemsInfo.isEmpty() || info.visibleItemsInfo.last().index == info.totalItemsCount - 2
        }
    }
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = {
        val filter = Filter(
            kinds = listOf(Kind.ChannelMessage.num),
            limit = Config.config.fetchSize,
            tags = mapOf("e" to listOf(channelId.value!!))
        )
        scope.launch(Dispatchers.Default) {
            networkViewModel.send(filter, until = true)
        }
    })
    val changeChannel : (String) -> Unit = { id ->
        if (channelId.value!! != id) {
            scope.launch(Dispatchers.Main) {
                if (channelId.value!!.isNotEmpty()) {
                    val info = postListState.layoutInfo.visibleItemsInfo
                    postFirstVisibleIndex[channelId.value!!] = if (info.isEmpty()) {
                        0
                    }
                    else {
                        info.first().index
                    }
                }
                networkViewModel.setChannel(id)
                isChannelSwitched = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                var doCreateChannel by remember { mutableStateOf(false) }
                var doChangeProfile by remember { mutableStateOf(false) }
                ModalDrawerSheet(modifier = Modifier.padding(end = 100.dp)) {
                    Row {
                        Button(onClick = {
                            scope.launch(Dispatchers.Main) {
                                manager.clearFocus()
                                drawerState.close()
                            }
                            onNavigate()
                        }, content = {
                            Image(painterResource(id = R.drawable.setting), contentDescription = "setting", modifier = Modifier
                                .width(Const.ACTION_ICON_SIZE)
                                .height(Const.ACTION_ICON_SIZE))
                        }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background), modifier = Modifier
                            .weight(1f))
                        Button(onClick = {
                            if (Config.config.privateKey.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error_set_private_key),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            else {
                                manager.clearFocus()
                                doCreateChannel = true
                            }
                        }, content = {
                            Image(painterResource(id = R.drawable.edit), contentDescription = context.getString(R.string.description_create_channel), modifier = Modifier
                                .width(Const.ACTION_ICON_SIZE)
                                .height(Const.ACTION_ICON_SIZE))
                        }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background), modifier = Modifier
                            .weight(1f))
                        Button(onClick = {
                            if (Config.config.privateKey.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error_set_private_key),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            else {
                                manager.clearFocus()
                                doChangeProfile = true
                            }
                        }, content = {
                            Image(painterResource(id = R.drawable.person), contentDescription = context.getString(R.string.description_change_profile), modifier = Modifier
                                .width(Const.ACTION_ICON_SIZE)
                                .height(Const.ACTION_ICON_SIZE))
                        }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background), modifier = Modifier
                            .weight(1f))
                    }
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(id = R.drawable.search), contentDescription = context.getString(R.string.search), modifier = Modifier
                            .height(Const.ACTION_ICON_SIZE)
                            .width(Const.ACTION_ICON_SIZE))
                        TextField(value = searchChannel, onValueChange = {
                            searchChannel = it.replace("\n", "")
                        }, maxLines = 1, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                    }
                    Divider()
                    val list = if (searchChannel.isEmpty()) {
                        channelDataList.value!!
                    }
                    else {
                        channelDataList.value!!.filter {
                            val profileData = channelMetaData.value!![it.event.id]!!
                            profileData.name.contains(searchChannel.toRegex())
                        }
                    }
                    LazyColumn(state = channelListState) {
                        item {
                            Text(context.getString(R.string.relay_connection_status), textAlign = TextAlign.Center, modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showRelayConnectionStatus = !showRelayConnectionStatus
                                }
                                .padding(top = 10.dp, bottom = 10.dp)
                            )
                        }
                        item {
                            Divider()
                        }
                        if (showRelayConnectionStatus) {
                            items(items = relayConnectionStatus.value!!.keys.toList(), key = { it }) {
                                Row(modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        networkViewModel.reconnect(it)
                                    }, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = it, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), maxLines = 1)
                                    if (relayConnectionStatus.value!![it]!!) {
                                        Image(painterResource(id = R.drawable.ok), contentDescription = context.getString(R.string.ok), modifier = Modifier.height(Const.ICON_SIZE))
                                    }
                                    else {
                                        Image(painterResource(id = R.drawable.close), contentDescription = context.getString(R.string.ng), modifier = Modifier.height(Const.ICON_SIZE))
                                    }
                                }
                            }
                            item {
                                Divider()
                            }
                        }
                        item {
                            Text(context.getString(R.string.pinned_channel), textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 10.dp))
                        }
                        item {
                            Divider()
                        }
                        items(items = pinnedChannelList.value!!, key = { it }) {
                            var expanded by remember { mutableStateOf(false) }
                            val metaData = if (channelMetaData.value!!.contains(it)) {
                                channelMetaData.value!![it]!!
                            }
                            else {
                                MetaData(0, it)
                            }
                            Box {
                                DrawerMenuItem(metaData = metaData, onClick = {
                                    scope.launch(Dispatchers.Main) {
                                        manager.clearFocus()
                                        drawerState.close()
                                        changeChannel(it)
                                    }
                                }, onLongPress = {
                                    expanded = true
                                })
                                DropdownMenu(expanded = expanded, onDismissRequest = {
                                    expanded = false
                                }) {
                                    DropdownMenuItem(text = {
                                        Text(context.getString(R.string.unpinned))
                                    }, onClick = {
                                        if (Config.config.privateKey.isEmpty()) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.error_set_private_key),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            scope.launch(Dispatchers.Default) {
                                                expanded = false
                                                networkViewModel.unpinChannel(it)
                                            }
                                        }
                                    })
                                }
                            }
                        }
                        item {
                            Divider()
                        }
                        item {
                            Text(context.getString(R.string.list_of_channel), textAlign = TextAlign.Center, modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 10.dp))
                        }
                        item {
                            Divider()
                        }
                        items(items = list, key = { it.event.toJSONObject().toString() }) {
                            if (channelMetaData.value!!.contains(it.event.id)) {
                                var expanded by remember { mutableStateOf(false) }
                                val metaData = channelMetaData.value!![it.event.id]!!
                                Box {
                                    DrawerMenuItem(metaData = metaData, onClick = {
                                        scope.launch(Dispatchers.Main) {
                                            manager.clearFocus()
                                            drawerState.close()
                                            changeChannel(it.event.id)
                                        }
                                    }, onLongPress = {
                                        expanded = true
                                    })
                                    DropdownMenu(expanded = expanded, onDismissRequest = {
                                        expanded = false
                                    }) {
                                        DropdownMenuItem(text = {
                                            Text(context.getString(R.string.pinned))
                                        }, onClick = {
                                            if (Config.config.privateKey.isEmpty()) {
                                                Toast.makeText(context, context.getString(R.string.error_set_private_key), Toast.LENGTH_SHORT).show()
                                            }
                                            else {
                                                scope.launch(Dispatchers.Default) {
                                                    expanded = false
                                                    networkViewModel.pinChannel(it.event.id)
                                                }
                                            }
                                        })
                                    }
                                }
                            }
                        }
                        item {
                            Divider()
                        }
                        item {
                            NavigationDrawerItem(
                                label = {
                                    Text(context.getString(R.string.load_more), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                },
                                selected = false,
                                onClick = {
                                    val filter = Filter(
                                        kinds = listOf(Kind.ChannelCreation.num),
                                        limit = Config.config.fetchSize,
                                    )
                                    scope.launch(Dispatchers.Default) {
                                        networkViewModel.send(filter, until = channelDataList.value!!.isNotEmpty())
                                    }
                                }
                            )
                        }
                    }
                }
                if (doCreateChannel) {
                    ChannelMetaDataView(title = context.getString(R.string.description_create_channel),
                        name = "", about = "", picture = "", modifier = Modifier.align(Alignment.TopCenter),
                        onSubmit = { name, about, picture ->
                            scope.launch(Dispatchers.Default) {
                                val json = JSONObject()
                                json.put(MetaData.NAME, name)
                                json.put(MetaData.ABOUT, about)
                                json.put(MetaData.PICTURE, picture)
                                val event = Event(
                                    Kind.ChannelCreation.num,
                                    json.toString(),
                                    Misc.now(),
                                    Config.config.getPublicKey()
                                )
                                event.id    = Event.generateHash(event, true)
                                event.sig   = Event.sign(event, Config.config.privateKey)
                                networkViewModel.send(event)
                            }
                            doCreateChannel = false
                        }, onCancel = {
                            doCreateChannel = false
                        }
                    )
                }
                if (doChangeProfile) {
                    var name = ""
                    var about = ""
                    var picture = ""
                    var nip05 = ""
                    if (userMetaData.value!!.contains(Config.config.getPublicKey())) {
                        val data = userMetaData.value!![Config.config.getPublicKey()]!!
                        name = data.name
                        about = data.about
                        picture = data.pictureUrl
                        nip05 = data.nip05Address
                    }
                    ProfileMetaDataView(title = context.getString(R.string.description_change_profile),
                        name = name, about = about, picture = picture, nip05 = nip05,
                        modifier = Modifier.align(Alignment.TopCenter),
                        onSubmit = { name, about, picture, nip05 ->
                            scope.launch(Dispatchers.Default) {
                                val json = JSONObject()
                                json.put(MetaData.NAME, name)
                                json.put(MetaData.ABOUT, about)
                                json.put(MetaData.PICTURE, picture)
                                json.put(MetaData.NIP05, nip05)
                                val event = Event(
                                    Kind.Metadata.num,
                                    json.toString(),
                                    Misc.now(),
                                    Config.config.getPublicKey()
                                )
                                event.id    = Event.generateHash(event, true)
                                event.sig   = Event.sign(event, Config.config.privateKey)
                                networkViewModel.send(event)
                            }
                            doChangeProfile = false
                        }, onCancel = {
                            doChangeProfile = false
                        }
                    )
                }
            }, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)) {
                Column {
                    var channelAbout = ""
                    Row(modifier = Modifier.clickable {
                        expandedChannelAbout = !expandedChannelAbout
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.menu),
                            context.getString(R.string.menu),
                            modifier = Modifier
                                .width(Const.ICON_SIZE)
                                .height(Const.ICON_SIZE)
                                .clickable {
                                    scope.launch(Dispatchers.Main) {
                                        drawerState.open()
                                    }
                                })
                        var channelImage: ImageBitmap? = null
                        var channelName = ""
                        if (channelMetaData.value!!.contains(channelId.value!!)) {
                            val data = channelMetaData.value!![channelId.value!!]!!
                            if (data.name.isNotEmpty()) {
                                channelName = data.name
                                if (channelName.length > 16) {
                                    channelName = channelName.take(16) + "..."
                                }
                            }
                            if (data.about.isNotEmpty()) {
                                channelAbout = data.about
                            }
                            if (data.image.status == DataStatus.Valid) {
                                channelImage = data.image.data
                            }
                        }
                        if (Config.config.displayProfilePicture) {
                            if (channelImage == null) {
                                Image(
                                    painterResource(id = R.drawable.no_image),
                                    "no_image",
                                    modifier = Modifier
                                        .width(Const.ICON_SIZE)
                                        .height(Const.ICON_SIZE)
                                )
                            } else {
                                Image(
                                    channelImage,
                                    channelId.value!!,
                                    modifier = Modifier
                                        .width(Const.ICON_SIZE)
                                        .height(Const.ICON_SIZE)
                                )
                            }
                        }
                        Text(
                            text = channelName,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2
                        )
                    }
                    if (expandedChannelAbout) {
                        Row {
                            Text(
                                text = channelAbout, modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        expandedChannelAbout = false
                                    }, textAlign = TextAlign.Center
                            )
                        }
                    }
                    Divider()
                    if (channelDataList.value!!.isEmpty()) {
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    else if (channelId.value!!.isEmpty()) {
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()) {
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painterResource(id = R.drawable.swipe_right),
                                    contentDescription = context.getString(R.string.description_swipe_to_open_menu),
                                    modifier = Modifier
                                        .height(Const.ACTION_ICON_SIZE * 2)
                                        .fillMaxWidth()
                                )
                                Text(text = context.getString(R.string.description_swipe_to_open_menu), textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        EventListView(
                            onGetPostDataList = { postDataList.value!! },
                            onGetEventMap = { eventMap.value!! },
                            onGetLazyListState = { postListState },
                            onGetChannelMetaData = { channelMetaData.value!! },
                            onGetUserMetaData = { userMetaData.value!! },
                            onGetChannelId = { channelId.value!! },
                            onClickImageURL = { url ->
                                scope.launch(Dispatchers.Default) {
                                    networkViewModel.fetchImage(url)
                                    withContext(Dispatchers.Main) {
                                        imageURL = url
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f),
                            onUserNotFound = { pubkey ->
                                scope.launch(Dispatchers.Default) {
                                    networkViewModel.fetchUserProfile(listOf(pubkey))
                                }
                            }, onEventNotFound = { filter ->
                                scope.launch(Dispatchers.Default) {
                                    networkViewModel.send(filter)
                                }
                            }, onPost = { event ->
                                scope.launch(Dispatchers.Default) {
                                    networkViewModel.send(event)
                                }
                            }, onHide = { event ->
                                val e = Event(
                                    kind = Kind.ChannelHideMessage.num,
                                    content = "",
                                    createdAt = Misc.now(),
                                    pubkey = Config.config.getPublicKey(),
                                    tags = listOf(listOf("e", event.id))
                                )
                                scope.launch(Dispatchers.Default) {
                                    networkViewModel.send(e)
                                }
                            }, onMute = { event ->
                                val e = Event(
                                    kind = Kind.ChannelMuteUser.num,
                                    content = "",
                                    createdAt = Misc.now(),
                                    pubkey = Config.config.getPublicKey(),
                                    tags = listOf(listOf("p", event.pubkey))
                                )
                                scope.launch(Dispatchers.Default) {
                                    networkViewModel.send(e)
                                }
                            }, onMoveChannel = { id ->
                                scope.launch(Dispatchers.Default) {
                                    changeChannel(id)
                                }
                            }
                        )
                    }
                }
                PullRefreshIndicator(refreshing = refreshing, state = refreshState,
                    modifier = Modifier.align(Alignment.TopCenter))
                TransmittedDataView(modifier = Modifier
                    .align(Alignment.BottomCenter),
                    onGetTransmittedDataSize = {
                        transmittedDataSize.value!!
                    }
                )
            }
        }
        if (imageURL.isNotEmpty()) {
            val onDismissRequest : () -> Unit = {
                imageURL = ""
            }
            Dialog(onDismissRequest = {
                onDismissRequest()
            }) {
                Card {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (image.value == null || image.value!!.status == DataStatus.Loading) {
                            Text(text = context.getString(R.string.loading), modifier = Modifier.align(
                                Alignment.Center))
                        } else if (image.value!!.status == DataStatus.Invalid) {
                            Text(text = context.getString(R.string.invalid_image), modifier = Modifier.align(
                                Alignment.Center))
                        } else if (image.value!!.status == DataStatus.Valid) {
                            val data = image.value!!.data!!
                            val imageBitmap =
                                BitmapFactory.decodeByteArray(data, 0, data.size).asImageBitmap()
                            Image(imageBitmap, imageURL, modifier = Modifier.align(
                                Alignment.Center))
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(imageURL) {
        if (imageURL.isNotEmpty()) {
            scope.launch(Dispatchers.Default) {
                networkViewModel.fetchImage(imageURL)
            }
        }
    }
    LaunchedEffect(Config.config.relayList) {
        networkViewModel.connect(Config.config.relayList, onConnectFailure = { relay ->
            scope.launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_failed_to_connect).format(relay.url),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, onNewPost = {
            isNewPostReceived = true
        }, onNewPosts = {
            isNewPostsReceived = true
        }, onPostSuccess = { _ ->
        }, onPostFailure = { relay ->
            scope.launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_failed_to_post).format(relay.url),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
    LaunchedEffect(postDataList.value!!) {
        if (channelId.value!!.isNotEmpty() && isChannelSwitched) {
            isChannelSwitched = false
            val index = postFirstVisibleIndex[channelId.value!!] ?: 0
            launch(Dispatchers.Main) {
                postListState.scrollToItem(index)
            }
        }
    }
    LaunchedEffect(postListState) {
        snapshotFlow { postListState.layoutInfo.totalItemsCount }
            .collect {
                if (it > 0) {
                    if ((isNewPostReceived && isLatestBefore) || isNewPostsReceived) {
                        if (isNewPostReceived) {
                            isNewPostReceived = false
                        }
                        if (isNewPostsReceived) {
                            isNewPostsReceived = false
                        }
                        scope.launch(Dispatchers.Main) {
                            postListState.scrollToItem(it - 1)
                        }
                    }
                }
            }
    }
}
