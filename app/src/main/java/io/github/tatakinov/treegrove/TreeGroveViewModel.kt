package io.github.tatakinov.treegrove

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tatakinov.treegrove.connection.Downloader
import io.github.tatakinov.treegrove.connection.OnRelayListener
import io.github.tatakinov.treegrove.connection.Relay
import io.github.tatakinov.treegrove.connection.RelayConfig
import io.github.tatakinov.treegrove.connection.RelayInfo
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP05
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import io.github.tatakinov.treegrove.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference

class TreeGroveViewModel(private val userPreferencesRepository: UserPreferencesRepository): ViewModel() {
    private val _mutexRelay = Mutex()
    private val _mutexCache = Mutex()
    private val _mutexFlow = Mutex()
    private val _mutexData = Mutex()
    private val _relayList = mutableListOf<Relay>()
    private val _dataCache = mutableMapOf<String, MutableStateFlow<LoadingData<ByteArray>>>()
    private val _eventHashCache = hashSetOf<Event>()
    private val _eventCache = mutableListOf<EventInfo>()
    private val _streamEventMap = hashMapOf<Filter, Pair<Int, MutableStateFlow<List<Event>>>>()
    private val _replaceableEventMap = mutableMapOf<Filter, Pair<MutableSet<String>, MutableStateFlow<LoadingData<ReplaceableEvent>>>>()
    private val _oneShotEventMap = mutableMapOf<Filter, Pair<MutableSet<String>, MutableStateFlow<List<Event>>>>()
    private var fetchSize: Long = 0
    private var _transmittedSizeFlow = MutableStateFlow(0)
    val transmittedSizeFlow: StateFlow<Int> = _transmittedSizeFlow

    val tabList = mutableStateListOf<Screen>()

    val privateKeyFlow = userPreferencesRepository.privateKeyFlow.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = UserPreferencesRepository.Default.privateKey)
    val publicKeyFlow = userPreferencesRepository.publicKeyFlow.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = UserPreferencesRepository.Default.privateKey)
    val relayConfigListFlow = userPreferencesRepository.relayConfigListFlow.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = UserPreferencesRepository.Default.relayList)
    val fetchSizeFlow = userPreferencesRepository.fetchSizeFlow.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = UserPreferencesRepository.Default.fetchSize)

    private val _relayInfoListFlow = MutableStateFlow(listOf<RelayInfo>())
    val relayInfoListFlow: StateFlow<List<RelayInfo>> = _relayInfoListFlow

    private val _downloader = Downloader(onTransmit = { url, dataSize ->
        _transmittedSizeFlow.update { it + dataSize }
    })

    init {
        viewModelScope.launch {
            fetchSizeFlow.collect {
                fetchSize = it
            }
        }
    }

    private fun addEventCache(url: String, event: Event, isOneShot: Boolean) {
        if (!_eventHashCache.contains(event)) {
            _eventHashCache.add(event)
            _eventCache.add(EventInfo(listOf(url), event, isOneShot))
        }
        else {
            for (i in _eventCache.indices) {
                if (_eventCache[i].event == event && !_eventCache[i].from.contains(url)) {
                    val list = mutableListOf<String>().apply {
                        addAll(_eventCache[i].from)
                        add(url)
                    }
                    _eventCache[i] = _eventCache[i].copy(from = list, isOneShot = _eventCache[i].isOneShot && isOneShot)
                    break
                }
            }
        }
        if (event.kind == Kind.ChannelCreation.num) {
            val e = Event(kind = Kind.ChannelMetadata.num, content = event.content, createdAt = event.createdAt, pubkey = event.pubkey, tags = listOf(listOf("e", event.id)),)
            addEventCache(url, e, isOneShot)
        }
    }

    private suspend fun updateEventCache() {
        val streamKeySet = mutableSetOf<Filter>()
        val replaceableKeySet = mutableSetOf<Filter>()
        val oneShotKeySet = mutableSetOf<Filter>()
        _mutexFlow.withLock {
            streamKeySet.addAll(_streamEventMap.keys)
            replaceableKeySet.addAll(_replaceableEventMap.keys)
            oneShotKeySet.addAll(_oneShotEventMap.keys)
        }
        for (k in streamKeySet) {
            _streamEventMap[k]?.let { (_, flow) ->
                val l = _mutexCache.withLock {
                    return@withLock _eventCache.filter { k.cond(it.event) }.map { it.event }
                }
                if (flow.value.size < l.size) {
                    flow.update { l }
                }
            }
        }
        for (k in replaceableKeySet) {
            _replaceableEventMap[k]?.let { (_, flow) ->
                val e = _mutexCache.withLock {
                    return@withLock _eventCache.firstOrNull { k.cond(it.event) }?.event
                }
                val r = e?.let {
                    ReplaceableEvent.parse(it)
                }
                if (e != null && r != null) {
                    val value = flow.value
                    if (value !is LoadingData.Valid) {
                        flow.update { LoadingData.Valid(r) }
                        if (r is ReplaceableEvent.MetaData && r.nip05.domain.isNotEmpty()) {
                            fetchNIP05(flow, r.nip05.domain, e.pubkey)
                        }
                    } else if (value.data.createdAt < r.createdAt) {
                        flow.update { LoadingData.Valid(r) }
                    }
                }
            }
        }
        for (k in oneShotKeySet) {
            _oneShotEventMap[k]?.let { (_, flow) ->
                val l = _mutexCache.withLock {
                    return@withLock _eventCache.filter { k.cond(it.event) }.map { it.event }
                }
                if (l.size > flow.value.size) {
                    flow.update { l }
                }
            }
        }
    }

    suspend fun setRelayConfigList(relayConfigList: List<RelayConfig>) {
        viewModelScope.launch(Dispatchers.IO) {
            _mutexRelay.withLock {
                for (relayConfig in relayConfigList) {
                    if (_relayList.isEmpty() || _relayList.none { it.url() == relayConfig.url }) {
                        _relayInfoListFlow.update {
                            val l = mutableListOf<RelayInfo>().apply {
                                addAll(it)
                            }
                            l.add(RelayInfo(relayConfig.url))
                            l
                        }
                        val relay = Relay(relayConfig.url, relayConfig.read, relayConfig.write, object : OnRelayListener {
                            override fun onConnected(relay: Relay) {
                                _relayInfoListFlow.update {
                                    val l = mutableListOf<RelayInfo>().apply {
                                        addAll(it)
                                    }
                                    for (i in l.indices) {
                                        if (l[i].url == relay.url()) {
                                            l[i] = l[i].copy(isConnected = true)
                                        }
                                    }
                                    l
                                }
                                if (_streamEventMap.isNotEmpty()) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        relay.sendStream(_streamEventMap.keys.mapTo(mutableSetOf()) { it.copy(limit = 0) })
                                    }
                                }
                            }

                            override fun onEvent(relay: Relay, event: Event) {
                                viewModelScope.launch {
                                    _mutexCache.withLock {
                                        addEventCache(relay.url(), event, false)
                                        _eventCache.sortByDescending { it.event.createdAt }
                                    }
                                    updateEventCache()
                                }
                            }

                            override fun onEOSE(relay: Relay) {
                                // nop
                            }

                            override fun onFailure(relay: Relay, t: Throwable, res: Response?) {
                                _relayInfoListFlow.update {
                                    val l = mutableListOf<RelayInfo>().apply {
                                        addAll(it)
                                    }
                                    for (i in l.indices) {
                                        if (l[i].url == relay.url()) {
                                            l[i] = l[i].copy(isConnected = false)
                                        }
                                    }
                                    l
                                }
                            }

                            override fun onTransmit(relay: Relay, dataSize: Int) {
                                _relayInfoListFlow.update {
                                    val l = mutableListOf<RelayInfo>().apply {
                                        addAll(it)
                                    }
                                    for (i in l.indices) {
                                        if (l[i].url == relay.url()) {
                                            l[i] =
                                                l[i].copy(transmittedSize = l[i].transmittedSize + dataSize)
                                        }
                                    }
                                    l
                                }
                            }

                            override fun onClose(relay: Relay, code: Int, reason: String) {
                                TODO("Not yet implemented")
                            }
                        })
                        _relayList.add(relay)
                    }
                    else {
                        val relay = _relayList.first { it.url() == relayConfig.url }
                        relay.change(relayConfig.read, relayConfig.write)
                    }
                }
                var index = 0
                while (relayConfigList.size < _relayList.size) {
                    if (relayConfigList.none { it.url == _relayList[index].url() }) {
                        _relayList.removeAt(index)
                    } else {
                        index++
                    }
                }
            }
            connectRelay()
        }
    }

    fun connectRelay() {
        viewModelScope.launch {
            _mutexRelay.withLock {
                for (relay in _relayList) {
                    relay.connect()
                }
            }
        }
    }

    suspend fun updatePreferences(userPreferences: UserPreferences) {
        userPreferencesRepository.updatePrivateKey(userPreferences.privateKey)
        userPreferencesRepository.updatePublicKey(userPreferences.publicKey)
        userPreferencesRepository.updateRelayList(userPreferences.relayList)
        userPreferencesRepository.updateFetchSize(userPreferences.fetchSize)
    }

    private fun sendStream() {
        viewModelScope.launch {
            _mutexRelay.withLock {
                for (relay in _relayList) {
                    relay.sendStream(_streamEventMap.filter { (k, v) -> v.first > 0 }.keys.mapTo(mutableSetOf()) { it.copy(limit = 0) })
                }
            }
        }
    }

    fun subscribeStreamEvent(filter: Filter): StateFlow<List<Event>> {
        return runBlocking {
            _mutexFlow.withLock {
                val v = _streamEventMap[filter]
                if (v != null) {
                    _streamEventMap[filter] = v.copy(first = v.first + 1)
                }
                else {
                    _streamEventMap[filter] = Pair(1, MutableStateFlow(listOf()))
                }
            }
            sendStream()
            return@runBlocking _streamEventMap[filter]!!.second
        }
    }

    fun unsubscribeStreamEvent(filter: Filter) {
        viewModelScope.launch {
            _mutexFlow.withLock {
                _streamEventMap[filter]?.let {
                    if (it.first > 0) {
                        _streamEventMap[filter] = it.copy(first = it.first - 1)
                        // TODO since
                        sendStream()
                    }
                }
            }
        }
    }

    fun fetchPastPost(filter: Filter) {
        viewModelScope.launch {
            _mutexRelay.withLock {
                for (relay in _relayList) {
                    if (relay.isConnected() && relay.readable()) {
                        val list =
                            _eventCache.filter { filter.cond(it.event) && it.from.contains(relay.url()) && !it.isOneShot }
                                .map { it.event }
                        val until = if (list.isNotEmpty()) {
                            val last = list.last()
                            last.createdAt
                        } else {
                            0
                        }
                        relay.sendOneShot(filter.copy(limit = fetchSizeFlow.value, until = until),
                            onReceive = { url, eventList ->
                                viewModelScope.launch {
                                    _mutexCache.withLock {
                                        for (event in eventList) {
                                            addEventCache(url, event, false)
                                        }
                                        _eventCache.sortByDescending { it.event.createdAt }
                                    }
                                    updateEventCache()
                                }
                            })
                    }
                }
            }
        }
    }

    private suspend fun fetchNIP05(eventCache: MutableStateFlow<LoadingData<ReplaceableEvent>>, nip05Address: String, pubKey: String) = withContext(Dispatchers.IO) {
        val match = NIP05.ADDRESS_REGEX.find(nip05Address)
        match?.let {
            val username = match.groups[1]!!.value
            val domain = match.groups[2]!!.value
            viewModelScope.launch(Dispatchers.IO) {
                _downloader.get(
                    NIP05.generateIdentifyURL(
                        domain,
                        username
                    ),
                    allowRedirect = false,
                    onReceive = { url, data ->
                        viewModelScope.launch {
                            if (data is LoadingData.Valid) {
                                try {
                                    val json =
                                        JSONObject(String(data.data))
                                    val names =
                                        json.getJSONObject(
                                            NIP05.NAMES
                                        )
                                    if (names.getString(username) == pubKey) {
                                        val value =
                                            eventCache.value
                                        if (value is LoadingData.Valid) {
                                            val d =
                                                value.data
                                            if (d is ReplaceableEvent.MetaData) {
                                                eventCache.update {
                                                    LoadingData.Valid(
                                                        d.copy(
                                                            nip05 = d.nip05.copy(
                                                                identify = LoadingData.Valid(
                                                                    true
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } catch (e: JSONException) {
                                    val value =
                                        eventCache.value
                                    if (value is LoadingData.Valid) {
                                        val d =
                                            value.data
                                        if (d is ReplaceableEvent.MetaData) {
                                            eventCache.update {
                                                LoadingData.Valid(
                                                    d.copy(
                                                        nip05 = d.nip05.copy(
                                                            identify = LoadingData.Invalid(
                                                                LoadingData.Reason.ParseError
                                                            )
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (data is LoadingData.Invalid) {
                                val value =
                                    eventCache.value
                                if (value is LoadingData.Valid) {
                                    val d = value.data
                                    if (d is ReplaceableEvent.MetaData) {
                                        eventCache.update {
                                            LoadingData.Valid(
                                                d.copy(
                                                    nip05 = d.nip05.copy(
                                                        identify = LoadingData.Invalid(
                                                            data.reason
                                                        )
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            } else {
                                // unreachable
                            }
                        }
                    })
            }
        }
    }

    fun subscribeReplaceableEvent(filter: Filter): StateFlow<LoadingData<ReplaceableEvent>> {
        runBlocking {
            _mutexFlow.withLock {
                if (!_replaceableEventMap.containsKey(filter)) {
                    _replaceableEventMap[filter] = Pair(mutableSetOf(), MutableStateFlow(LoadingData.Loading()))
                }
            }
            _replaceableEventMap[filter]?.let { (set, flow) ->
                viewModelScope.launch {
                    val e = _mutexCache.withLock {
                        return@withLock _eventCache.firstOrNull { filter.cond(it.event) }?.event
                    }
                    val r = e?.let {
                        ReplaceableEvent.parse(it)
                    }
                    if (e != null && r != null && flow.value != r) {
                        flow.update { LoadingData.Valid(r) }
                    }
                    _mutexRelay.withLock {
                        for (relay in _relayList) {
                            if (relay.isConnected() && relay.readable() && !set.contains(relay.url())) {
                                set.add(relay.url())
                                relay.sendOneShot(
                                    filter.copy(limit = 1),
                                    onReceive = { url, eventList ->
                                        if (eventList.isNotEmpty()) {
                                            viewModelScope.launch {
                                                _mutexCache.withLock {
                                                    for (event in eventList) {
                                                        addEventCache(url, event, false)
                                                    }
                                                    _eventCache.sortByDescending { it.event.createdAt }
                                                }
                                                updateEventCache()
                                            }
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
        return _replaceableEventMap[filter]!!.second
    }

    fun subscribeOneShotEvent(filter: Filter): StateFlow<List<Event>> {
        runBlocking {
            _mutexCache.withLock {
                if (!_oneShotEventMap.containsKey(filter)) {
                    _oneShotEventMap[filter] = Pair(mutableSetOf(), MutableStateFlow(listOf()))
                }
            }
        }
        _oneShotEventMap[filter]?.let { (set, flow) ->
            viewModelScope.launch {
                val l = _mutexCache.withLock {
                    return@withLock _eventCache.filter { filter.cond(it.event) }.map { it.event }
                }
                flow.update { l }
                _mutexRelay.withLock {
                    for (relay in _relayList) {
                        if (relay.isConnected() && relay.readable() && !set.contains(relay.url())) {
                            set.add(relay.url())
                            relay.sendOneShot(filter.copy(limit = fetchSize), onReceive = { url, eventList ->
                                viewModelScope.launch {
                                    _mutexCache.withLock {
                                        for (event in eventList) {
                                            addEventCache(url, event, true)
                                        }
                                        _eventCache.sortByDescending { it.event.createdAt }
                                    }
                                    updateEventCache()
                                }
                            })
                        }
                    }
                }
            }
        }
        return _oneShotEventMap[filter]!!.second
    }

    fun post(event: Event, onSuccess: () -> Unit, onFailure: (String, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _mutexRelay.withLock {
                for (relay in _relayList) {
                    relay.send(event, onSuccess, onFailure)
                }
            }
        }
    }

    fun fetchImage(url: String, force: Boolean = false): StateFlow<LoadingData<ByteArray>> {
        runBlocking {
            _mutexData.withLock {
                if (_dataCache.containsKey(url)) {
                    if (force) {
                        viewModelScope.launch {
                            _downloader.get(url = url, onReceive = { url, data ->
                                _dataCache[url]!!.update {
                                    data
                                }
                            })
                        }
                    }
                    return@runBlocking _dataCache[url]!!
                }
                else {
                    _dataCache[url] = MutableStateFlow(LoadingData.Loading())
                    viewModelScope.launch(Dispatchers.IO) {
                        _downloader.get(url = url, onReceive = { url, data ->
                            _dataCache[url]!!.update {
                                data
                            }
                        })
                    }
                }
            }
        }
        return _dataCache[url]!!
    }

    fun getURL(event: Event): List<String> {
        val createdAt = event.createdAt
        var startIndexMin = 0
        var startIndexMax = _eventCache.lastIndex
        var startIndex: Int;
        while (true) {
            startIndex = (startIndexMin + startIndexMax) / 2
            if (startIndex == startIndexMin || startIndex == startIndexMax) {
                break
            }
            if (_eventCache[startIndex].event.createdAt == createdAt) {
                if (_eventCache[startIndex - 1].event.createdAt > createdAt) {
                    break
                }
                else {
                    startIndexMax = startIndex
                }
            }
            else if (_eventCache[startIndex].event.createdAt > createdAt) {
                startIndexMin = startIndex
            }
            else {
                startIndexMax = startIndex
            }
        }
        if (_eventCache[startIndex].event.createdAt != createdAt) {
            return listOf()
        }
        var endIndexMin = 0
        var endIndexMax = _eventCache.lastIndex
        var endIndex: Int;
        while (true) {
            endIndex = (endIndexMin + endIndexMax) / 2
            if (endIndex == endIndexMin || endIndex == endIndexMax) {
                break
            }
            if (_eventCache[endIndex].event.createdAt == createdAt) {
                if (_eventCache[endIndex + 1].event.createdAt < createdAt) {
                    break
                }
                else {
                    endIndexMin = endIndex
                }
            }
            else if (_eventCache[endIndex].event.createdAt > createdAt) {
                endIndexMin = endIndex
            }
            else {
                endIndexMax = endIndex
            }
        }
        if (_eventCache[endIndex].event.createdAt != createdAt) {
            return listOf()
        }
        val list = _eventCache.slice(IntRange(startIndex, endIndex))
        return list.filter { it.event == event }.map { it.from }.firstOrNull() ?: listOf()
    }
}