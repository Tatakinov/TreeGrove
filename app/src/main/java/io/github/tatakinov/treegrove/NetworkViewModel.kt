package io.github.tatakinov.treegrove

import android.graphics.BitmapFactory
import android.util.Log
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
    private val _channelList = MutableLiveData<List<EventData>>(ArrayList())
    val channelList : LiveData<List<EventData>> get() = _channelList
    private val _channelProfileData = MutableLiveData<MutableMap<String, ProfileData>>(HashMap())
    val channelProfileData : LiveData<MutableMap<String, ProfileData>> get() = _channelProfileData
    private val _postDataListMap = mutableMapOf<String, List<EventData>>("" to listOf())
    private val _postDataList = MutableLiveData<List<EventData>>(listOf())
    val postDataList : LiveData<List<EventData>> get() = _postDataList
    private val _postProfileDataInternal = mutableMapOf<String, MutableList<String>>()
    private val _postProfileData = MutableLiveData<MutableMap<String, ProfileData>>(mutableMapOf<String, ProfileData>())
    val postProfileData : LiveData<MutableMap<String, ProfileData>> get() = _postProfileData
    private val _transmittedDataSize = MutableLiveData<Int>(0)
    val transmittedDataSize : LiveData<Int> get() = _transmittedDataSize
    private val _imageDataMap = mutableMapOf<String, LoadingDataStatus<ByteArray>>()
    private var _image = MutableLiveData<LoadingDataStatus<ByteArray>>()
    val image : LiveData<LoadingDataStatus<ByteArray>> get() = _image

    var _state : NetworkState = NetworkState.Other

    override fun onResume(owner: LifecycleOwner) {
        viewModelScope.launch(Dispatchers.IO) {
            reconnect()
        }
    }

    private suspend fun connect(config : ConfigRelayData, onConnectFailure: (Relay) -> Unit, onNewPost : (Relay, Event) -> Unit,
                                onPostSuccess : (Relay) -> Unit, onPostFailure: (Relay) -> Unit) = withContext(Dispatchers.Default) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock() {
                lateinit var relay: Relay
                if (!_relays.any { it.url() == config.url }) {
                    relay = Relay(config, object : OnRelayListener {
                        override fun onConnected(relay: Relay) {
                            viewModelScope.launch(Dispatchers.IO) {
                                _mutex.withLock {
                                    sendInInitialize(relay)
                                }
                            }
                        }

                        override fun onEvent(relay: Relay, event: Event) {
                            viewModelScope.launch(Dispatchers.Default) {
                                _mutex.withLock {
                                    val channelListBuffer = mutableListOf<EventData>().apply {
                                        addAll(_channelList.value!!)
                                    }
                                    val postDataListBuffer = mutableListOf<EventData>().apply {
                                        addAll(_postDataList.value!!)
                                    }
                                    processEvent(
                                        relay,
                                        event,
                                        channel = channelListBuffer,
                                        post = postDataListBuffer
                                    )
                                    channelListBuffer.sortByDescending { it.event.createdAt }
                                    postDataListBuffer.sortByDescending { it.event.createdAt }
                                    withContext(Dispatchers.Main) {
                                        _channelList.value = channelListBuffer.toList()
                                        _postDataList.value = postDataListBuffer.toList()
                                        _postDataListMap[_channelId.value!!] = postDataListBuffer
                                    }
                                    if (postDataListBuffer.isNotEmpty() && postDataListBuffer.first().event == event) {
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
                            filter: Filter,
                            events: List<Event>
                        ): Boolean {
                            viewModelScope.launch(Dispatchers.Default) {
                                _mutex.withLock {
                                    val channelListBuffer = mutableListOf<EventData>().apply {
                                        addAll(_channelList.value!!)
                                    }
                                    val postDataListBuffer = mutableListOf<EventData>().apply {
                                        addAll(_postDataList.value!!)
                                    }
                                    for (event in events) {
                                        processEvent(
                                            relay,
                                            event,
                                            channel = channelListBuffer,
                                            post = postDataListBuffer
                                        )
                                    }
                                    channelListBuffer.sortByDescending { it.event.createdAt }
                                    postDataListBuffer.sortByDescending { it.event.createdAt }
                                    withContext(Dispatchers.Main) {
                                        _channelList.value = channelListBuffer.toList()
                                        _postDataList.value = postDataListBuffer.toList()
                                        _postDataListMap[_channelId.value!!] = postDataListBuffer
                                    }
                                }
                            }
                            if (filter.until > 0 || !(filter.kinds.contains(40) || filter.kinds.contains(
                                    42
                                ))
                            ) {
                                return true
                            }
                            return false
                        }

                        override fun onFailure(relay: Relay, t: Throwable, res: Response?) {
                            viewModelScope.launch(Dispatchers.Main) {
                                onConnectFailure(relay)
                            }
                        }

                        override fun onTransmit(relay: Relay, dataSize: Int) {
                            viewModelScope.launch(Dispatchers.Main) {
                                _transmittedDataSize.value =
                                    _transmittedDataSize.value!!.plus(dataSize)
                            }
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
                    _relays.filter {it.url() == config.url}.forEach {
                        it.read = config.read
                        it.write = config.write
                    }
                }
                _relays.filter { it.url() == config.url }.forEach {
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

    private suspend fun sendInInitialize(relay : Relay) = withContext(Dispatchers.IO) {
        relay.closeAllFilter()
        val filter = Filter(kinds = listOf(40), limit = Config.config.fetchSize)
        relay.send(filter)
        val list = _postDataList.value!!.filter { it.from.contains(relay.url()) }
        if (channelId.value!!.isNotEmpty()) {
            val filter = if (list.isEmpty()) {
                Filter(
                    kinds = listOf(42),
                    limit = Config.config.fetchSize,
                    tags = mapOf("e" to listOf(channelId.value!!))
                )
            } else {
                val first = list.first()
                Filter(
                    kinds = listOf(42),
                    limit = Config.config.fetchSize,
                    tags = mapOf("e" to listOf(channelId.value!!)),
                    since = first.event.createdAt
                )
            }
            relay.send(filter)
        }
        if (Config.config.privateKey.isNotEmpty()) {
            fetchUserProfile(Config.config.getPublicKey(), relay)
        }
    }

    private suspend fun processEvent(relay : Relay, event : Event, channel : MutableList<EventData>, post : MutableList<EventData>) {
        when (event.kind) {
            Kind.Metadata.num -> {
                try {
                    val json = JSONObject(event.content)
                    val name = json.optString(ProfileData.NAME, "")
                    val about = json.optString(ProfileData.ABOUT, "")
                    val picture = json.optString(ProfileData.PICTURE, "")
                    val map = mutableMapOf<String, ProfileData>().apply {
                        for ((k, v) in _postProfileData.value!!) {
                            put(k, v)
                        }
                    }
                    map[event.pubkey] =
                        ProfileData(name, about, picture)
                    withContext(Dispatchers.Main) {
                        _postProfileData.value = map
                    }
                    // fetch image
                    if (picture.isNotEmpty() && canFetchProfileImage()) {
                        fetchProfileImage(picture, data = _postProfileData, id = event.pubkey)
                    }
                    _postProfileDataInternal.remove(event.pubkey)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            Kind.ChannelCreation.num -> {
                if (!channel.any { it.event == event }) {
                    if (!_channelProfileData.value!!.contains(event.id)) {
                        try {
                            val json = JSONObject(event.content)
                            val name = json.optString(
                                ProfileData.NAME,
                                "Invalid json object"
                            )
                            val about = json.optString(ProfileData.ABOUT, "")
                            val picture = json.optString(ProfileData.PICTURE, "")
                            val map = mutableMapOf<String, ProfileData>().apply {
                                for ((k, v) in _channelProfileData.value!!) {
                                    put(k, v)
                                }
                            }
                            map[event.id]   =
                                ProfileData(name, about, picture)
                            withContext(Dispatchers.Main) {
                                _channelProfileData.value = map
                            }
                            if (picture.isNotEmpty() && canFetchProfileImage()) {
                                fetchProfileImage(picture, data = _channelProfileData, id = event.id)
                            }
                        } catch (e: JSONException) {
                            _channelProfileData.value!![event.id] =
                                ProfileData("Invalid json object")
                        }
                        val filter = Filter(
                            kinds = listOf(41),
                            tags = mapOf("e" to listOf(event.id)),
                            limit = 1
                        )
                        viewModelScope.launch(Dispatchers.IO) {
                            relay.send(filter)
                        }
                    }
                    channel.add(EventData(from = listOf(relay.url()), event = event))
                }
                else {
                    for (i in channel.indices) {
                        if (channel[i].event == event) {
                            channel[i] = channel[i].copy(from = mutableListOf<String>().apply {
                                addAll(channel[i].from)
                                add(relay.url())
                            })
                            break
                        }
                    }
                }
            }

            Kind.ChannelMetadata.num -> {
                try {
                    var id = ""
                    for (tag in event.tags) {
                        if (tag[0] == "e") {
                            id = tag[1]
                            break
                        }
                    }
                    val json = JSONObject(event.content)
                    val name = json.optString(ProfileData.NAME, "")
                    val about = json.optString(ProfileData.ABOUT, "")
                    val picture = json.optString(ProfileData.PICTURE, "")
                    val map = mutableMapOf<String, ProfileData>().apply {
                        for ((k, v) in _channelProfileData.value!!) {
                            put(k, v)
                        }
                    }
                    map[id] = ProfileData(name, about, picture)

                    withContext(Dispatchers.Main) {
                        _channelProfileData.value = map
                    }
                    if (picture.isNotEmpty() && canFetchProfileImage()) {
                        _channelProfileData.value!![id]!!.image = _channelProfileData.value!![id]!!.image.copy(status = DataStatus.NotLoading, data = null)
                        fetchProfileImage(picture, data = _channelProfileData, id = id)
                    }
                }
                // エラーは出さず受信したイベントを無視する
                catch (_: ArrayIndexOutOfBoundsException) {
                }
                catch (_: JSONException) {
                }
            }

            Kind.ChannelMessage.num -> {
                if (!event.tags.any {
                    if (it.size < 2) {
                        false
                    } else {
                        it[1] == _channelId.value
                    }
                }) {
                    return
                }
                if (!post.any { it.event == event }) {
                    viewModelScope.launch(Dispatchers.Default) {
                        fetchUserProfile(event.pubkey, relay)
                    }
                    post.add(EventData(from = listOf(relay.url()), event = event))
                }
                else {
                    for (i in post.indices) {
                        if (post[i].event == event) {
                            val from = mutableListOf<String>().apply {
                                addAll(post[i].from)
                                add(relay.url())
                            }
                            post[i] = post[i].copy(from = from)
                        }
                    }
                }
            }

            else -> {
                Log.d("NetworkViewModel", "Unknown kind: ${event.kind}")
            }
        }
    }

    suspend fun connect(configs : List<ConfigRelayData>, onConnectFailure : (Relay) -> Unit, onNewPost: (Relay, Event) -> Unit,
                        onPostSuccess : (Relay) -> Unit, onPostFailure : (Relay) -> Unit) = withContext(Dispatchers.Default) {
        val list = mutableListOf<EventData>().apply {
            addAll(_channelList.value!!.filter {
                for (from in it.from) {
                    for (config in configs) {
                        if (config.url == from && config.read) {
                            return@filter true
                        }
                    }
                }
                return@filter false
            })
        }
        withContext(Dispatchers.Main) {
            _channelList.value = list
        }
        for (config in configs) {
            this@NetworkViewModel.connect(config, onConnectFailure, onNewPost, onPostSuccess, onPostFailure)
        }
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                _relays.filter { !configs.contains(ConfigRelayData(it.url())) }.forEach {
                    it.close()
                }
                _relays = mutableListOf<Relay>().apply {
                    addAll(_relays.filter { configs.contains(ConfigRelayData(it.url())) })
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
                    if (relay.url() == url) {
                        remove(relay)
                    }
                }
            }
        }
    }

    suspend fun send(event : Event)    = withContext(Dispatchers.IO) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock() {
                for (relay in _relays) {
                    relay.send(event)
                }
            }
        }
    }

    suspend fun send(filter : Filter, kind : Kind, since : Boolean = false, until : Boolean = false)   = withContext(Dispatchers.IO) {
        viewModelScope.launch(Dispatchers.Default) {
            val list : List<EventData>? = when (kind) {
                Kind.ChannelCreation -> {
                    _channelList.value!!
                }
                Kind.ChannelMessage -> {
                    _postDataList.value!!
                }
                else -> {
                    null
                }
            }
            _mutex.withLock() {
                for (relay in _relays) {
                    var u : Long = -1
                    var s : Long = -1
                    if (list != null) {
                        list.filter { it.from.contains(relay.url()) }.forEach {
                            if (u < 0 || u > it.event.createdAt) {
                                u = it.event.createdAt
                            }
                        }
                        list.filter { it.from.contains(relay.url()) }.forEach {
                            if (s < it.event.createdAt) {
                                s = it.event.createdAt
                            }
                        }
                    }
                    // 今のところsinceとuntilを同時に指定することはない
                    if (until && u > 0) {
                        relay.send(filter.copy(kinds = listOf(kind.num), until = u))
                    }
                    else if (since && s > 0) {
                        relay.send(filter.copy(kinds = listOf(kind.num), since = s))
                    }
                    else {
                        relay.send(filter.copy(kinds = listOf(kind.num)))
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
                withContext(Dispatchers.Main) {
                    _channelId.value = channelId
                }
                if (_channelId.value!!.isNotEmpty()) {
                    if (!_postDataListMap.contains(_channelId.value!!)) {
                        _postDataListMap[_channelId.value!!] = mutableListOf()
                    }
                    withContext(Dispatchers.Main) {
                        _postDataList.value = _postDataListMap[_channelId.value!!]
                    }
                    closePostFilter()
                    val filter = Filter(
                        limit = Config.config.fetchSize,
                        tags = mapOf("e" to listOf(channelId))
                    )
                    viewModelScope.launch(Dispatchers.IO) {
                        send(filter, Kind.ChannelMessage, since = true)
                    }
                }
            }
        }
    }

    private suspend fun fetchUserProfile(pubkey : String, relay : Relay) = withContext(Dispatchers.IO) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                if (_postProfileData.value!!.contains(pubkey)) {
                    return@launch
                }
                if (!_postProfileDataInternal.contains(pubkey)) {
                    _postProfileDataInternal[pubkey] = mutableListOf()
                }
                if (_postProfileDataInternal[pubkey]!!.contains(relay.url())) {
                    return@launch
                }
                _postProfileDataInternal[pubkey]!!.add(relay.url())
            }
            val filter = Filter(
                kinds = listOf(0),
                authors = listOf(pubkey),
                limit = 1
            )
            viewModelScope.launch(Dispatchers.IO) {
                relay.send(filter)
            }
        }
    }

    fun fetchUserProfile(pubkey : String) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                if (_postProfileData.value!!.contains(pubkey)) {
                    val data = _postProfileData.value!![pubkey]!!
                    if (data.pictureUrl.isNotEmpty() && data.image.status == DataStatus.NotLoading && canFetchProfileImage()) {
                        fetchProfileImage(data.pictureUrl, data = _postProfileData, id = pubkey)
                    }
                    return@launch
                }
            }
            for (relay in _relays) {
                fetchUserProfile(pubkey, relay)
            }
        }
    }

    private suspend fun fetch(url : String, onSuccess : (ByteArray) -> Unit, onInvalid : () -> Unit, onFailure: () -> Unit) = withContext(Dispatchers.Default) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext
        }
        val request = Request.Builder().url(url).build()
        withContext(Dispatchers.Main) {
            _transmittedDataSize.value =
                _transmittedDataSize.value!!.plus(
                    request.toString().toByteArray().size
                )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response =
                    HttpClient.instance.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    _mutex.withLock {
                        _transmittedDataSize.value =
                            _transmittedDataSize.value!!.plus(
                                response.headers.toString().toByteArray().size
                            )
                    }
                }
                if (response.code == 200) {
                    val data = response.body?.bytes()
                    if (data != null) {
                        withContext(Dispatchers.Main) {
                            _mutex.withLock {
                                withContext(Dispatchers.Main) {
                                    _transmittedDataSize.value =
                                        _transmittedDataSize.value!!.plus(
                                            data.size
                                        )
                                }
                            }
                            onSuccess(data)
                        }
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
            withContext(Dispatchers.Main) {
                _image.value = _imageDataMap[url]!!
            }
        }
        else {
            _imageDataMap[url] = LoadingDataStatus()
            withContext(Dispatchers.Main) {
                _image.value = _imageDataMap[url]!!
            }
            fetch(url, onSuccess = {data ->
                _imageDataMap[url] = _imageDataMap[url]!!.copy(status = DataStatus.Valid, data = data)
                viewModelScope.launch(Dispatchers.Main) {
                    _image.value = _imageDataMap[url]!!
                }
            }, onInvalid = {
                _imageDataMap[url] = _imageDataMap[url]!!.copy(status = DataStatus.Invalid)
                viewModelScope.launch(Dispatchers.Main) {
                    _image.value = _imageDataMap[url]!!
                }
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
                for ((k, v) in _channelProfileData.value!!) {
                    if (v.pictureUrl.isNotEmpty()) {
                        fetchProfileImage(v.pictureUrl, _channelProfileData, k)
                    }
                }
                for ((k, v) in _postProfileData.value!!) {
                    if (v.pictureUrl.isNotEmpty()) {
                        fetchProfileImage(v.pictureUrl, _postProfileData, k)
                    }
                }
            }
        }
    }

    private suspend fun fetchProfileImage(url : String, data : MutableLiveData<MutableMap<String, ProfileData>>, id : String) = withContext(Dispatchers.IO) {
        viewModelScope.launch(Dispatchers.Default) {
            _mutex.withLock {
                if (data.value!![id]!!.image.status != DataStatus.NotLoading) {
                    return@withLock
                }
                data.value!![id]!!.image = data.value!![id]!!.image.copy(status = DataStatus.Loading)
                val refresh : (DataStatus, ImageBitmap?) -> Unit = { status, image ->
                    viewModelScope.launch(Dispatchers.Default) {
                        _mutex.withLock {
                            val map = mutableMapOf<String, ProfileData>().apply {
                                for ((k, v) in data.value!!) {
                                    put(k, v.copy())
                                }
                            }
                            map[id]!!.image = map[id]!!.image.copy(status = status, data = image)
                            withContext(Dispatchers.Main) {
                                data.value = map
                            }
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

    private fun canFetchProfileImage() : Boolean {
        if (!Config.config.displayProfilePicture) {
            return false
        }
        if (Config.config.fetchProfilePictureOnlyWifi && _state == NetworkState.Other) {
            return false
        }
        return true
    }
}