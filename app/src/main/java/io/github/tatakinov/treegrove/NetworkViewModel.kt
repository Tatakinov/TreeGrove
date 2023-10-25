package io.github.tatakinov.treegrove

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP05
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class NetworkViewModel : ViewModel(), DefaultLifecycleObserver {
    private var _relays = mutableListOf<Relay>()
    private val _mutex  = Mutex()
    private var _channelId = MutableLiveData<String>("")
    val channelId : LiveData<String> get() = _channelId
    private var _channelListInternal = mutableMapOf<String, MutableList<EventData>>()
    private val _channelList = MutableLiveData<List<EventData>>(listOf())
    val channelList : LiveData<List<EventData>> get() = _channelList
    private val _channelMetaDataInternal = mutableMapOf<String, MetaData>()
    private val _channelMetaData = MutableLiveData<Map<String, MetaData>>(mapOf())
    val channelMetaData : LiveData<Map<String, MetaData>> get() = _channelMetaData
    private val _eventMapInternal = mutableMapOf<String, MutableSet<Event>>()
    private val _eventMap = MutableLiveData<Map<String, Set<Event>>>(mapOf())
    val eventMap : LiveData<Map<String, Set<Event>>> get() = _eventMap
    private val _postLastBrowsed = mutableMapOf<String, Long>()
    private val _postDataListInternal = mutableMapOf<String, MutableMap<String, MutableList<EventData>>>("" to mutableMapOf())
    private val _postDataList = MutableLiveData<List<EventData>>(listOf())
    val postDataList : LiveData<List<EventData>> get() = _postDataList
    private val _userMetaDataInternal = mutableMapOf<String, MetaData>()
    private val _userMetaData = MutableLiveData<Map<String, MetaData>>(mapOf())
    val userMetaData : LiveData<Map<String, MetaData>> get() = _userMetaData
    private var _transmittedDataSizeInternal = 0
    private val _transmittedDataSize = MutableLiveData<Int>(0)
    val transmittedDataSize : LiveData<Int> get() = _transmittedDataSize
    private val _imageDataMap = mutableMapOf<String, LoadingDataStatus<ByteArray>>()
    private var _image = MutableLiveData<LoadingDataStatus<ByteArray>>()
    val image : LiveData<LoadingDataStatus<ByteArray>> get() = _image
    private var _pinnedChannelListInternal = mutableListOf<String>()
    private val _pinnedChannelList = MutableLiveData<List<String>>(listOf())
    val pinnedChannelList : LiveData<List<String>> get() = _pinnedChannelList

    private var _state : NetworkState = NetworkState.Other
    private val _relayConnectionStatusInternal = mutableMapOf<String, Boolean>()
    private val _relayConnectionStatus = MutableLiveData<Map<String, Boolean>>()
    val relayConnectionStatus : LiveData<Map<String, Boolean>> get() = _relayConnectionStatus

    override fun onResume(owner: LifecycleOwner) {
        viewModelScope.launch(Dispatchers.IO) {
            reconnect()
        }
    }

    private suspend fun connect(config : ConfigRelayData, onConnectFailure: (Relay) -> Unit, onNewPost : (Relay, Event) -> Unit,
                                onFirstPostRefreshed : () -> Unit,
                                onPostSuccess : (Relay) -> Unit, onPostFailure: (Relay) -> Unit) = withContext(Dispatchers.Default) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                lateinit var relay: Relay
                if (!_relays.any { it.url == config.url }) {
                    relay = Relay(config, object : OnRelayListener {
                        override fun onConnected(relay: Relay) {
                            _relayConnectionStatusInternal[relay.url] = true
                            _relayConnectionStatus.postValue(mutableMapOf<String, Boolean>().apply {
                                for ((k, v) in _relayConnectionStatusInternal) {
                                    put(k, v)
                                }
                            })
                            viewModelScope.launch(Dispatchers.IO) {
                                _mutex.withLock {
                                    sendInInitialize(relay)
                                }
                            }
                        }

                        override fun onEvent(relay: Relay, event: Event) {
                            viewModelScope.launch(Dispatchers.Default) {
                                _mutex.withLock {
                                    val channelMetaDataIdList = mutableListOf<String>()
                                    val userMetaDataIdList = mutableListOf<String>()
                                    processEvent(
                                        relay,
                                        event,
                                        channelMetaDataIdList = channelMetaDataIdList,
                                        userMetaDataIdList = userMetaDataIdList
                                    )
                                    val channelList = mutableListOf<EventData>().apply {
                                        addAll(_channelListInternal.values.flatten())
                                        sortByDescending { it.event.createdAt }
                                    }
                                    val postDataList = mutableListOf<EventData>().apply {
                                        addAll(_postDataListInternal[_channelId.value!!]!!.values.flatten())
                                        sortByDescending { it.event.createdAt }
                                    }
                                    val channelMap = mutableMapOf<String, MetaData>().apply {
                                        for ((k, v) in _channelMetaDataInternal) {
                                            put(k, v.copy())
                                        }
                                    }
                                    val userMap = mutableMapOf<String, MetaData>().apply {
                                        for ((k, v) in _userMetaDataInternal) {
                                            put(k, v.copy())
                                        }
                                    }
                                    val map = mutableMapOf<String, MutableSet<Event>>().apply {
                                        for ((k, v) in _eventMapInternal) {
                                            put(k, v)
                                        }
                                    }
                                    _channelList.postValue(channelList)
                                    _postDataList.postValue(postDataList)
                                    _channelMetaData.postValue(channelMap)
                                    _userMetaData.postValue(userMap)
                                    _eventMap.postValue(map)
                                    if (channelMetaDataIdList.isNotEmpty()) {
                                        val l = channelMetaDataIdList.distinct()
                                        val f = Filter(
                                            kinds = listOf(Kind.ChannelMetadata.num),
                                            tags = mapOf("e" to l),
                                            limit = l.size.toLong()
                                        )
                                        viewModelScope.launch(Dispatchers.IO) {
                                            relay.send(f)
                                        }
                                    }
                                    if (userMetaDataIdList.isNotEmpty()) {
                                        fetchUserProfile(userMetaDataIdList.distinct(), relay)
                                    }
                                    if (postDataList.isNotEmpty() && postDataList.first().event == event) {
                                        onNewPost(relay, event)
                                    }
                                }
                            }
                        }

                        override fun onSuccessToPost(relay: Relay, event: Event, reason: String) {
                            onPostSuccess(relay)
                        }

                        override fun onFailureToPost(relay: Relay, event: Event, reason: String) {
                            if (reason.startsWith("invalid:") && event.id == Event.generateHash(
                                    event,
                                    true
                                )
                            ) {
                                event.id = Event.generateHash(event, false)
                                event.sig = Event.sign(event, Config.config.privateKey)
                                relay.send(event)
                            } else {
                                onPostFailure(relay)
                            }
                        }

                        override fun onEOSE(
                            relay: Relay,
                            filterList: List<Filter>,
                            events: List<Event>
                        ): Boolean {
                            viewModelScope.launch(Dispatchers.Default) {
                                _mutex.withLock {
                                    val channelMetaDataIdList = mutableListOf<String>()
                                    val userMetaDataIdList = mutableListOf<String>()
                                    for (event in events) {
                                        processEvent(
                                            relay,
                                            event,
                                            channelMetaDataIdList = channelMetaDataIdList,
                                            userMetaDataIdList = userMetaDataIdList,
                                        )
                                    }
                                    val channelList = mutableListOf<EventData>().apply {
                                        addAll(_channelListInternal.values.flatten())
                                        sortByDescending { it.event.createdAt }
                                    }
                                    val postDataList = mutableListOf<EventData>().apply {
                                        addAll(_postDataListInternal[_channelId.value!!]!!.values.flatten())
                                        sortByDescending { it.event.createdAt }
                                    }
                                    val channelMap = mutableMapOf<String, MetaData>().apply {
                                        for ((k, v) in _channelMetaDataInternal) {
                                            put(k, v.copy())
                                        }
                                    }
                                    val userMap = mutableMapOf<String, MetaData>().apply {
                                        for ((k, v) in _userMetaDataInternal) {
                                            put(k, v.copy())
                                        }
                                    }
                                    val map = mutableMapOf<String, MutableSet<Event>>().apply {
                                        for ((k, v) in _eventMapInternal) {
                                            put(k, v)
                                        }
                                    }
                                    _channelList.postValue(channelList)
                                    _postDataList.postValue(postDataList)
                                    _channelMetaData.postValue(channelMap)
                                    _userMetaData.postValue(userMap)
                                    _eventMap.postValue(map)
                                    if (channelMetaDataIdList.isNotEmpty()) {
                                        val l = channelMetaDataIdList.distinct()
                                        val f = Filter(
                                            kinds = listOf(Kind.ChannelMetadata.num),
                                            tags = mapOf("e" to l),
                                            limit = l.size.toLong()
                                        )
                                        viewModelScope.launch(Dispatchers.IO) {
                                            relay.send(f)
                                        }
                                    }
                                    if (userMetaDataIdList.isNotEmpty()) {
                                        fetchUserProfile(userMetaDataIdList.distinct(), relay)
                                    }
                                    events.sortedByDescending { it.createdAt }
                                    if (postDataList.isNotEmpty() && events.isNotEmpty() && events.first() == postDataList.first().event) {
                                        onFirstPostRefreshed()
                                    }
                                }
                            }
                            if (filterList.any { it.until > 0 }) {
                                return true
                            }
                            return false
                        }

                        override fun onFailure(relay: Relay, t: Throwable, res: Response?) {
                            _relayConnectionStatusInternal[relay.url] = false
                            _relayConnectionStatus.postValue(mutableMapOf<String, Boolean>().apply {
                                for ((k, v) in _relayConnectionStatusInternal) {
                                    put(k, v)
                                }
                            })
                            onConnectFailure(relay)
                        }

                        override fun onTransmit(relay: Relay, dataSize: Int) {
                            _transmittedDataSizeInternal += dataSize
                            _transmittedDataSize.postValue(_transmittedDataSizeInternal)
                        }

                        override fun onClose(relay: Relay, code: Int, reason: String) {
                            if (code == 4000) {
                                viewModelScope.launch(Dispatchers.Default) {
                                    this@NetworkViewModel.remove(relay)
                                }
                            }
                        }
                    })
                    _relays.add(relay)
                }
                else {
                    _relays.filter {it.url == config.url}.forEach {
                        it.read = config.read
                        it.write = config.write
                    }
                }
                _relays.filter { it.url == config.url }.forEach {
                    reconnect(it)
                }
            }
        }
    }

    private suspend fun reconnect() = withContext(Dispatchers.Default) {
        for (relay in _relays) {
            reconnect(relay)
        }
    }

    private suspend fun reconnect(relay : Relay) = withContext(Dispatchers.Default) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                withContext(Dispatchers.IO) {
                    if (relay.isConnected()) {
                        sendInInitialize(relay)
                    }
                    else {
                        relay.connect()
                    }
                }
            }
        }
    }

    fun reconnect(url : String) {
        for (relay in _relays) {
            if (relay.url == url) {
                viewModelScope.launch(Dispatchers.Default) {
                    reconnect(relay)
                }
            }
        }
    }

    private suspend fun sendInInitialize(relay : Relay) = withContext(Dispatchers.IO) {
        relay.closeAllFilter()
        if (Config.config.privateKey.isEmpty()) {
            relay.send(
                Filter(
                    kinds = listOf(Kind.ChannelCreation.num),
                    limit = Config.config.fetchSize
                )
            )
        }
        else {
            relay.send(
                listOf(
                    Filter(
                        kinds = listOf(Kind.ChannelCreation.num),
                        limit = Config.config.fetchSize
                    ),
                    Filter(kinds = listOf(Kind.PinList.num),
                        authors = listOf(Config.config.getPublicKey()), limit = 1),
                    Filter(kinds = listOf(Kind.Metadata.num),
                        authors = listOf(Config.config.getPublicKey()), limit = 1)
                )
            )
        }
        val list = mutableListOf<EventData>().apply {
            for ((_, v) in _postDataListInternal[channelId.value!!]!!) {
                addAll(v.filter { it.from.contains(relay.url) })
            }
            sortByDescending { it.event.createdAt }
        }
        if (channelId.value!!.isNotEmpty()) {
            val filter = if (list.isEmpty()) {
                Filter(
                    kinds = listOf(Kind.ChannelMessage.num),
                    limit = Config.config.fetchSize,
                    tags = mapOf("e" to listOf(channelId.value!!))
                )
            } else {
                val first = list.first()
                Filter(
                    kinds = listOf(Kind.ChannelMessage.num),
                    limit = Config.config.fetchSize,
                    tags = mapOf("e" to listOf(channelId.value!!)),
                    since = first.event.createdAt
                )
            }
            relay.send(filter)
        }
        if (Config.config.privateKey.isNotEmpty()) {
            fetchUserProfile(listOf(Config.config.getPublicKey()), relay)
        }
    }

    private suspend fun processEvent(relay : Relay, event : Event,
                                     channelMetaDataIdList : MutableList<String>, userMetaDataIdList : MutableList<String>) {
        when (event.kind) {
            Kind.Metadata.num -> {
                try {
                    val json = JSONObject(event.content)
                    val name = json.optString(MetaData.NAME, "")
                    val about = json.optString(MetaData.ABOUT, "")
                    val picture = json.optString(MetaData.PICTURE, "")
                    val nip05 = json.optString(MetaData.NIP05, "")
                    val map = _userMetaDataInternal
                    if (map.contains(event.pubkey)) {
                        if (map[event.pubkey]!!.createdAt < event.createdAt) {
                            map[event.pubkey] =
                                MetaData(event.createdAt, name, about, picture, nip05Address = nip05)
                        }
                    }
                    else {
                        map[event.pubkey] =
                            MetaData(event.createdAt, name, about, picture, nip05Address = nip05)
                    }
                    // fetch image
                    if (picture.isNotEmpty()) {
                        fetchProfileImage(picture, dest = _userMetaData, internal = map, pubkey = event.pubkey)
                    }
                    if (nip05.isNotEmpty()) {
                        fetchProfileIdentify(nip05, event.pubkey)
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            Kind.ChannelCreation.num -> {
                if (!_eventMapInternal.contains(event.id)) {
                    _eventMapInternal[event.id] = mutableSetOf(event)
                }
                else if (!_eventMapInternal[event.id]!!.contains(event)) {
                    _eventMapInternal[event.id]!!.add(event)
                }
                if (_channelListInternal.none { map -> map.key == event.id &&
                            map.value.any { it.event == event } &&
                            map.value.first { it.event == event }.from.contains(relay.url) }) {
                    try {
                        val json = JSONObject(event.content)
                        val name = json.optString(
                            MetaData.NAME,
                            "Invalid json object"
                        )
                        val about = json.optString(MetaData.ABOUT, "")
                        val picture = json.optString(MetaData.PICTURE, "")
                        val map = _channelMetaDataInternal
                        if (map.contains(event.id)) {
                            if (map[event.id]!!.createdAt < event.createdAt) {
                                map[event.id]   =
                                    MetaData(event.createdAt, name, about, picture)
                            }
                        }
                        else {
                            map[event.id] =
                                MetaData(event.createdAt, name, about, picture)
                        }
                        if (picture.isNotEmpty()) {
                            fetchProfileImage(picture, dest = _channelMetaData, internal = map, pubkey = event.id)
                        }
                        channelMetaDataIdList.add(event.id)
                        if (_channelListInternal.contains(event.id) && _channelListInternal[event.id]!!.any {it.event == event}) {
                            val list = _channelListInternal[event.id]!!
                            for (i in list.indices) {
                                if (list[i].event == event) {
                                    list[i] = list[i].copy(from = mutableListOf<String>().apply {
                                        addAll(list[i].from)
                                        add(relay.url)
                                    })
                                    break
                                }
                            }
                        }
                        else {
                            if (!_channelListInternal.contains(event.id)) {
                                _channelListInternal[event.id] = mutableListOf()
                            }
                            _channelListInternal[event.id]!!.add(EventData(from = listOf(relay.url), event = event))
                        }
                    }
                    // エラーは出さず受信したイベントを無視する
                    catch (_: JSONException) {
                    }
                }
            }

            Kind.ChannelMetadata.num -> {
                try {
                    var id = ""
                    for (tag in event.tags) {
                        if (tag.size >= 2 && tag[0] == "e") {
                            id = tag[1]
                            break
                        }
                    }
                    val json = JSONObject(event.content)
                    val name = json.optString(MetaData.NAME, "")
                    val about = json.optString(MetaData.ABOUT, "")
                    val picture = json.optString(MetaData.PICTURE, "")
                    val map = _channelMetaDataInternal
                    var isChanged = true
                    if (map.contains(id)) {
                        if (map[id]!!.pictureUrl == picture) {
                            isChanged = false
                        }
                        if (map[id]!!.createdAt < event.createdAt) {
                            map[id] = MetaData(event.createdAt, name, about, picture)
                        }
                    }
                    else {
                        map[id] = MetaData(event.createdAt, name, about, picture)
                    }
                    if (picture.isNotEmpty() && isChanged) {
                        map[id]!!.image = map[id]!!.image.copy(status = DataStatus.NotLoading, data = null)
                        fetchProfileImage(picture, dest = _channelMetaData, internal = map, pubkey = id)
                    }
                }
                // エラーは出さず受信したイベントを無視する
                catch (_: JSONException) {
                }
            }

            Kind.ChannelMessage.num -> {
                if (!_eventMapInternal.contains(event.id)) {
                    _eventMapInternal[event.id] = mutableSetOf(event)
                }
                else if (!_eventMapInternal[event.id]!!.contains(event)) {
                    _eventMapInternal[event.id]!!.add(event)
                }
                if (!event.tags.any {
                    if (it.size < 2) {
                        false
                    } else {
                        it[1] == _channelId.value
                    }
                }) {
                    return
                }
                val map = _postDataListInternal[_channelId.value!!]!!
                if (map.none { m -> m.key == event.id &&
                            m.value.any { it.event == event } &&
                            m.value.first { it.event == event }.from.contains(relay.url) }) {
                    if (!_userMetaDataInternal.contains(event.pubkey)) {
                        userMetaDataIdList.add(event.pubkey)
                    }
                    if (map.contains(event.id) &&
                        map[event.id]!!.any { it.event == event }) {
                        val list = map[event.id]!!
                        for (i in list.indices) {
                            if (list[i].event == event && !list[i].from.contains(relay.url)) {
                                list[i] = list[i].copy(from = mutableListOf<String>().apply {
                                    addAll(list[i].from)
                                    add(relay.url)
                                })
                            }
                        }
                    }
                    else {
                        if (!map.contains(event.id)) {
                            map[event.id] = mutableListOf()
                        }
                        map[event.id]!!.add(EventData(from = listOf(relay.url), event = event))
                    }
                }
            }

            Kind.Text.num -> {
                if (!_eventMapInternal.contains(event.id)) {
                    _eventMapInternal[event.id] = mutableSetOf(event)
                }
                else if (!_eventMapInternal[event.id]!!.contains(event)) {
                    _eventMapInternal[event.id]!!.add(event)
                }
                if (!_userMetaDataInternal.contains(event.pubkey)) {
                    userMetaDataIdList.add(event.pubkey)
                }
            }

            Kind.PinList.num -> {
                val list = event.tags.filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
                _pinnedChannelListInternal  = mutableListOf<String>().apply { addAll(list) }
                _pinnedChannelList.postValue(_pinnedChannelListInternal)
                val l = list.filterNot { _channelMetaDataInternal.contains(it) }
                if (l.isNotEmpty()) {
                    send(Filter(ids = l, kinds = listOf(Kind.ChannelCreation.num), limit = l.size.toLong(), until = Misc.now()))
                }
            }

            else -> {
            }
        }
    }

    suspend fun connect(configs : List<ConfigRelayData>, onConnectFailure : (Relay) -> Unit, onNewPost: (Relay, Event) -> Unit,
                        onFirstPostChanged: () -> Unit,
                        onPostSuccess : (Relay) -> Unit, onPostFailure : (Relay) -> Unit) = withContext(Dispatchers.Default) {
        _mutex.withLock {
            _channelListInternal = mutableMapOf<String, MutableList<EventData>>().apply {
                for ((k, v) in _channelListInternal) {
                    val list = mutableListOf<EventData>().apply {
                        addAll(v.filter { f -> configs.any { f.from.contains(it.url) } })
                    }
                    if (list.isNotEmpty()) {
                        put(k, list)
                    }
                }
            }
            _channelList.postValue(mutableListOf<EventData>().apply {
                addAll(_channelListInternal.values.flatten())
                sortByDescending { it.event.createdAt }
            })
        }
        for (config in configs) {
            this@NetworkViewModel.connect(config, onConnectFailure = onConnectFailure, onNewPost = onNewPost,
                onFirstPostRefreshed = onFirstPostChanged, onPostSuccess = onPostSuccess, onPostFailure = onPostFailure)
        }
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                _relays.filter { !configs.contains(ConfigRelayData(it.url)) }.forEach {
                    it.close()
                }
                _relays = mutableListOf<Relay>().apply {
                    addAll(_relays.filter { configs.contains(ConfigRelayData(it.url)) })
                }
            }
        }
    }

    suspend fun remove(relay : Relay)   = withContext(Dispatchers.Default) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock() {
                _relays.remove(relay)
            }
        }
    }

    suspend fun remove(url : String)    = withContext(Dispatchers.Default) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock() {
                for (relay in _relays) {
                    if (relay.url == url) {
                        remove(relay)
                    }
                }
            }
        }
    }

    suspend fun send(event : Event)    = withContext(Dispatchers.Default) {
        viewModelScope.launch(Dispatchers.IO) {
            _mutex.withLock() {
                for (relay in _relays) {
                    relay.send(event)
                }
            }
        }
    }

    suspend fun send(filter : Filter, since : Boolean = false, until : Boolean = false)   = withContext(Dispatchers.IO) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock() {
                val list : List<EventData>? = if (filter.kinds.contains(Kind.ChannelCreation.num)) {
                    mutableListOf<EventData>().apply {
                        addAll(_channelListInternal.values.flatten())
                        sortByDescending { it.event.createdAt }
                    }
                } else if (filter.kinds.contains(Kind.ChannelMessage.num)) {
                    mutableListOf<EventData>().apply {
                        addAll(_postDataListInternal[_channelId.value!!]!!.values.flatten())
                        sortByDescending { it.event.createdAt }
                    }
                }
                else {
                    null
                }
                for (relay in _relays) {
                    var u : Long = -1
                    var s : Long = -1
                    list?.filter { it.from.contains(relay.url) }?.forEach {
                        if (u < 0 || u > it.event.createdAt) {
                            u = it.event.createdAt
                        }
                        if (s < it.event.createdAt) {
                            s = it.event.createdAt
                        }
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        // 今のところsinceとuntilを同時に指定することはない
                        if (until && u > 0) {
                            relay.send(filter.copy(until = u))
                        }
                        else if (since && s > 0) {
                            relay.send(filter.copy(since = s))
                        }
                        else {
                            relay.send(filter)
                        }
                    }
                }
            }
        }
    }

    private suspend fun closePostFilter() = withContext(Dispatchers.Default) {
        for (relay in _relays) {
            relay.closePostFilter()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        _relays.forEach {
            it.close()
        }
    }

    suspend fun setChannel(channelId : String) = withContext(Dispatchers.Default) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                if (_channelId.value!!.isNotEmpty()) {
                    _postLastBrowsed[_channelId.value!!]    = Misc.now()
                }
                withContext(Dispatchers.Main) {
                    _channelId.value = channelId
                }
                if (_channelId.value!!.isNotEmpty()) {
                    var limit : Long = 0
                    if (!_postDataListInternal.contains(_channelId.value!!)) {
                        _postDataListInternal[_channelId.value!!] = mutableMapOf()
                        limit = Config.config.fetchSize
                    }
                    val since = if (_postLastBrowsed.contains(_channelId.value!!)) {
                        _postLastBrowsed[_channelId.value!!]!!
                    }
                    else {
                        0
                    }
                    val postDataList = mutableListOf<EventData>().apply {
                        addAll(_postDataListInternal[_channelId.value!!]!!.values.flatten())
                        sortByDescending { it.event.createdAt }
                    }
                    _postDataList.postValue(postDataList)
                    closePostFilter()
                    val filter = Filter(
                        kinds = listOf(Kind.ChannelMessage.num),
                        limit = limit,
                        tags = mapOf("e" to listOf(channelId)),
                        since = since
                    )
                    viewModelScope.launch(Dispatchers.IO) {
                        send(filter)
                    }
                }
            }
        }
    }

    private suspend fun fetchUserProfile(pubkeyList : List<String>, relay : Relay) = withContext(Dispatchers.IO) {
        viewModelScope.launch(Dispatchers.Default) {
            lateinit var list : List<String>
            _mutex.withLock {
                list = pubkeyList.filterNot { _userMetaDataInternal.contains(it) }
            }
            if (list.isNotEmpty()) {
                val filter = Filter(
                    kinds = listOf(Kind.Metadata.num),
                    authors = list,
                    limit = list.size.toLong()
                )
                viewModelScope.launch(Dispatchers.IO) {
                    relay.send(filter)
                }
            }
        }
    }

    fun fetchUserProfile(pubkeyList : List<String>) {
        viewModelScope.launch(Dispatchers.Default) {
            lateinit var list : List<String>
            _mutex.withLock {
                pubkeyList.filter {
                    _userMetaDataInternal.contains(it)
                }.forEach {
                    val data = _userMetaDataInternal[it]!!
                    if (data.pictureUrl.isNotEmpty() && data.image.status == DataStatus.NotLoading) {
                        fetchProfileImage(data.pictureUrl, dest = _userMetaData, internal = _userMetaDataInternal, pubkey = it)
                    }
                    if (data.nip05Address.isNotEmpty() && data.nip05.status == DataStatus.NotLoading) {
                        fetchProfileIdentify(data.nip05Address, pubkey = it)
                    }
                }
                list = pubkeyList.filterNot { _userMetaDataInternal.contains(it) }
            }
            for (relay in _relays) {
                fetchUserProfile(list, relay)
            }
        }
    }

    private suspend fun fetch(url : String, followRedirect : Boolean = true, onSuccess : (ByteArray) -> Unit, onInvalid : () -> Unit, onFailure: () -> Unit) = withContext(Dispatchers.Default) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext
        }
        val request = Request.Builder().url(url).build()
        _mutex.withLock {
            _transmittedDataSizeInternal += request.toString().toByteArray().size
            _transmittedDataSize.postValue(_transmittedDataSizeInternal)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = if (followRedirect) { HttpClient.default } else { HttpClient.noRedirect }
                val response =
                    client.newCall(request).execute()
                _mutex.withLock {
                    _transmittedDataSizeInternal += response.headers.toString().toByteArray().size
                    _transmittedDataSize.postValue(_transmittedDataSizeInternal)
                }
                if (response.code == 200) {
                    val data = response.body?.bytes()
                    if (data != null) {
                        _mutex.withLock {
                            _transmittedDataSizeInternal += data.size
                            _transmittedDataSize.postValue(_transmittedDataSizeInternal)
                        }
                        onSuccess(data)
                    } else {
                        onInvalid()
                    }
                } else {
                    onInvalid()
                }
            } catch (e: IOException) {
                onFailure()
            }
        }
    }

    suspend fun fetchImage(url : String) = withContext(Dispatchers.Default) {
        if (_imageDataMap.contains(url) && _imageDataMap[url]!!.status != DataStatus.NotLoading) {
            _image.postValue(_imageDataMap[url]!!)
        }
        else {
            _imageDataMap[url] = LoadingDataStatus()
            withContext(Dispatchers.Main) {
                _image.value = _imageDataMap[url]!!
            }
            fetch(url, onSuccess = {data ->
                _imageDataMap[url] = _imageDataMap[url]!!.copy(status = DataStatus.Valid, data = data)
                _image.postValue(_imageDataMap[url]!!)
            }, onInvalid = {
                _imageDataMap[url] = _imageDataMap[url]!!.copy(status = DataStatus.Invalid)
                _image.postValue(_imageDataMap[url]!!)
            }, onFailure = {
                _imageDataMap[url] = _imageDataMap[url]!!.copy(status = DataStatus.NotLoading)
            })
        }
    }

    fun setNetworkState(state: NetworkState) {
        _state = state
        viewModelScope.launch(Dispatchers.IO) {
            close()
            reconnect()
            if (state == NetworkState.Wifi) {
                for ((k, v) in _channelMetaDataInternal) {
                    if (v.pictureUrl.isNotEmpty()) {
                        fetchProfileImage(v.pictureUrl, _channelMetaData, _channelMetaDataInternal, k)
                    }
                }
                for ((k, v) in _userMetaDataInternal) {
                    if (v.pictureUrl.isNotEmpty()) {
                        fetchProfileImage(v.pictureUrl, _userMetaData, _userMetaDataInternal, k)
                    }
                }
            }
        }
    }

    private suspend fun fetchProfileImage(url : String, dest : MutableLiveData<Map<String, MetaData>>,
                                          internal : MutableMap<String, MetaData>, pubkey : String) = withContext(Dispatchers.Default) {
        if (!canFetchProfileImage()) {
            return@withContext
        }
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                if (internal[pubkey]!!.image.status != DataStatus.NotLoading) {
                    return@withLock
                }
                internal[pubkey]!!.image = internal[pubkey]!!.image.copy(status = DataStatus.Loading)
                val refresh : (DataStatus, ImageBitmap?) -> Unit = { status, image ->
                    viewModelScope.launch(Dispatchers.Default) {
                        _mutex.withLock {
                            internal[pubkey]!!.image = internal[pubkey]!!.image.copy(status = status, data = image)
                            val map = mutableMapOf<String, MetaData>().apply {
                                for ((k, v) in internal) {
                                    put(k, v.copy())
                                }
                            }
                            dest.postValue(map)
                        }
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    fetch(url, onSuccess = { data ->
                        viewModelScope.launch(Dispatchers.Default) {
                            _mutex.withLock {
                                try {
                                    val image = BitmapFactory.decodeByteArray(
                                        data,
                                        0,
                                        data.size
                                    )?.asImageBitmap()
                                    if (image != null) {
                                        refresh(DataStatus.Valid, image)
                                    } else {
                                        refresh(DataStatus.Invalid, null)
                                    }
                                } catch (e: IllegalArgumentException) {
                                    refresh(DataStatus.Invalid, null)
                                }
                            }
                        }
                    }, onInvalid = {
                        refresh(DataStatus.Invalid, null)
                    }, onFailure = {
                        refresh(DataStatus.NotLoading, null)
                    })
                }
            }
        }
    }

    private suspend fun fetchProfileIdentify(id : String, pubkey : String) = withContext(Dispatchers.IO) {
        val match = NIP05.ADDRESS_REGEX.find(id) ?: return@withContext
        val username = match.groups[1]!!.value
        val domain = match.groups[2]!!.value
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                val internal = _userMetaDataInternal
                val dest = _userMetaData
                if (internal[pubkey]!!.nip05.status != DataStatus.NotLoading) {
                    return@withLock
                }
                internal[pubkey]!!.nip05 = internal[pubkey]!!.nip05.copy(status = DataStatus.Loading)
                val refresh : (DataStatus, Boolean?) -> Unit = { status, nip05 ->
                    viewModelScope.launch(Dispatchers.Default) {
                        _mutex.withLock {
                            internal[pubkey]!!.nip05 = internal[pubkey]!!.nip05.copy(status = status, data = nip05)
                            val map = mutableMapOf<String, MetaData>().apply {
                                for ((k, v) in internal) {
                                    put(k, v.copy())
                                }
                            }
                            dest.postValue(map)
                        }
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    fetch(NIP05.generateIdentifyURL(domain, username), followRedirect = false, onSuccess = { data ->
                        viewModelScope.launch(Dispatchers.Default) {
                            _mutex.withLock {
                                try {
                                    val json = JSONObject(String(data))
                                    val names = json.optJSONObject(NIP05.NAMES)
                                    if (names == null) {
                                        refresh(DataStatus.Invalid, null)
                                    }
                                    else {
                                        val p = names.optString(username, "")
                                        if (p == pubkey) {
                                            refresh(DataStatus.Valid, true)
                                        }
                                        else {
                                            refresh(DataStatus.Invalid, null)
                                        }
                                    }
                                } catch (e: JSONException) {
                                    refresh(DataStatus.Invalid, null)
                                }
                            }
                        }
                    }, onInvalid = {
                        refresh(DataStatus.Invalid, null)
                    }, onFailure = {
                        refresh(DataStatus.NotLoading, null)
                    })
                }
            }
        }
    }

    private fun canFetchProfileImage() : Boolean {
        if (!Config.config.displayProfilePicture) {
            return false
        }
        if (Config.config.fetchProfilePictureOnlyWifi && _state == NetworkState.Other) {
            return false
        }
        return true
    }

    private suspend fun refreshPinnedChannel() = withContext(Dispatchers.Default) {
        lateinit var event : Event
        _mutex.withLock {
            event = Event(Kind.PinList.num, "", Misc.now(), Config.config.getPublicKey(),
                tags = _pinnedChannelListInternal.map { listOf("e", it) })
        }
        event.id = Event.generateHash(event, true)
        event.sig = Event.sign(event, Config.config.privateKey)
        send(event)
    }

    suspend fun unpinChannel(id : String) = withContext(Dispatchers.Default) {
        _mutex.withLock {
            if (!_pinnedChannelListInternal.remove(id)) {
                return@withContext
            }
        }
        refreshPinnedChannel()
    }

    suspend fun pinChannel(id : String) = withContext(Dispatchers.Default) {
        if (_pinnedChannelListInternal.contains(id)) {
            return@withContext
        }
        _mutex.withLock {
            _pinnedChannelListInternal.add(id)
        }
        refreshPinnedChannel()
    }
}
