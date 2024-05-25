package io.github.tatakinov.treegrove

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

data class StreamFilter(val id: String, val filter: Filter)

class TreeGroveViewModel(private val userPreferencesRepository: UserPreferencesRepository): ViewModel() {
    private val _mutexRelay = Mutex()
    private val _mutexCache = Mutex()
    private val _mutexFlow = Mutex()
    private val _mutexData = Mutex()
    private val _relayList = mutableListOf<Relay>()
    private val _dataCache = mutableMapOf<String, MutableStateFlow<LoadingData<ByteArray>>>()
    private val _eventHashCache = hashSetOf<Event>()
    private val _eventCache = mutableListOf<EventInfo>()
    private val _streamEventCache = mutableMapOf<Filter, MutableStateFlow<List<Event>>>()
    private val _replaceableEventCache = mutableMapOf<Filter, MutableStateFlow<LoadingData<ReplaceableEvent>>>()
    private val _oneShotEventCache = mutableMapOf<Filter, MutableStateFlow<LoadingData<Event>>>()
    private val _streamFilterSet = mutableSetOf<StreamFilter>()
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

    private suspend fun addEventCache(url: String, event: Event) {
        _mutexCache.withLock {
            if (!_eventHashCache.contains(event)) {
                _eventHashCache.add(event)
                _eventCache.add(EventInfo(listOf(url), event))
            }
            else {
                for (i in _eventCache.indices) {
                    if (_eventCache[i].event == event && !_eventCache[i].from.contains(url)) {
                        val list = mutableListOf<String>().apply {
                            addAll(_eventCache[i].from)
                            add(url)
                        }
                        _eventCache[i] = _eventCache[i].copy(from = list)
                        break
                    }
                }
            }
        }
        if (event.kind == Kind.ChannelCreation.num) {
            val e = Event(kind = Kind.ChannelMetadata.num, content = event.content, createdAt = event.createdAt, pubkey = event.pubkey, tags = listOf(listOf("e", event.id)),)
            addEventCache(url, e)
        }
    }

    private suspend fun updateEventCache() {
        val streamKeySet = mutableSetOf<Filter>()
        val replaceableKeySet = mutableSetOf<Filter>()
        _mutexFlow.withLock {
            streamKeySet.addAll(_streamEventCache.keys)
            replaceableKeySet.addAll(_replaceableEventCache.keys)
        }
        for (k in streamKeySet) {
            val v = _streamEventCache[k]
            v?.let { flow ->
                val l = _eventCache.filter { k.cond(it.event) }.map { it.event }
                if (flow.value.size < l.size) {
                    flow.update { l }
                }
            }
        }
        for (k in replaceableKeySet) {
            val v = _replaceableEventCache[k]
            v?.let { flow ->
                val e = _eventCache.filter { k.cond(it.event) }.map { it.event }.firstOrNull()
                val r = e?.let {
                    ReplaceableEvent.parse(it)
                }
                if (e != null && r != null) {
                    val value = flow.value
                    if (value !is LoadingData.Valid) {
                        flow.update { LoadingData.Valid(r) }
                        if (r is ReplaceableEvent.MetaData && r.nip05.domain.isNotEmpty()) {
                            fetchNIP05(v, r.nip05.domain, e.pubkey)
                        }
                    } else if (value.data.createdAt < r.createdAt) {
                        flow.update { LoadingData.Valid(r) }
                    }
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
                        val relay = Relay(relayConfig.url, object : OnRelayListener {
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
                                if (_streamFilterSet.isNotEmpty()) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        relay.sendStream(_streamFilterSet.mapTo(mutableSetOf()) { it.filter.copy(limit = 0) })
                                    }
                                }
                            }

                            override fun onEvent(relay: Relay, event: Event) {
                                viewModelScope.launch {
                                    addEventCache(relay.url(), event)
                                    _mutexCache.withLock {
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
                        relay.change(relayConfig.read, relayConfig.write)
                        _relayList.add(relay)
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
                    relay.sendStream(_streamFilterSet.mapTo(mutableSetOf()) { it.filter.copy(limit = 0) })
                }
            }
        }
    }

    private suspend fun addOneShot(filter: Filter, onReceive: (String, List<Event>) -> Unit) = withContext(Dispatchers.IO) {
        viewModelScope.launch {
            _mutexRelay.withLock {
                for (relay in _relayList) {
                    relay.sendOneShot(filter, onReceive)
                }
            }
        }
    }

    fun subscribeStreamEvent(streamFilter: StreamFilter): StateFlow<List<Event>> {
        var filter: Filter?
        runBlocking {
            _mutexFlow.withLock {
                filter = _streamFilterSet.filter{ it.id == streamFilter.id }.map { it.filter }.firstOrNull()
                if (filter != null) {
                    return@runBlocking
                }
                else {
                    filter = streamFilter.filter
                }
                if (streamFilter.id != "invalid" && !_streamEventCache.containsKey(streamFilter.filter)) {
                    _streamFilterSet.add(streamFilter)
                }
                _streamEventCache[streamFilter.filter] = MutableStateFlow(listOf())
            }
            sendStream()
        }
        return _streamEventCache[filter]!!
    }

    fun unsubscribeStreamEvent(streamFilter: StreamFilter) {
        viewModelScope.launch {
            _mutexFlow.withLock {
                _streamFilterSet.removeIf { it.id == streamFilter.id }
            }
            sendStream()
        }
    }

    fun fetchPastPost(filter: Filter) {
        viewModelScope.launch {
            _mutexRelay.withLock {
                for (relay in _relayList) {
                    val list =
                        _eventCache.filter { filter.cond(it.event) && it.from.contains(relay.url()) }
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
                                for (event in eventList) {
                                    addEventCache(url, event)
                                }
                                updateEventCache()
                            }
                        })
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
                if (_replaceableEventCache.containsKey(filter)) {
                    return@runBlocking
                }
                else {
                    _replaceableEventCache[filter] = MutableStateFlow(LoadingData.Loading())
                }
            }
            viewModelScope.launch {
                addOneShot(filter.copy(limit = 1), onReceive = { url, eventList ->
                    viewModelScope.launch {
                        for (event in eventList) {
                            addEventCache(url, event)
                        }
                        _mutexCache.withLock {
                            _eventCache.sortByDescending { it.event.createdAt }
                        }
                        updateEventCache()
                    }
                })
            }
        }
        return _replaceableEventCache[filter]!!
    }

    fun subscribeOneShotEvent(filter: Filter): StateFlow<LoadingData<Event>> {
        if (_oneShotEventCache.containsKey(filter)) {
            return _oneShotEventCache[filter]!!
        }
        val eventCache = MutableStateFlow<LoadingData<Event>>(LoadingData.Loading())
        viewModelScope.launch {
            _mutexFlow.withLock {
                if (!_oneShotEventCache.containsKey(filter)) {
                    _oneShotEventCache[filter] = eventCache
                }
            }
        }
        viewModelScope.launch {
            addOneShot(filter.copy(limit = 1), onReceive = { url, eventList ->
                viewModelScope.launch {
                    if (eventList.isNotEmpty()) {
                        val event = eventList.maxByOrNull { it.createdAt }
                        event?.let {
                            eventCache.update {
                                LoadingData.Valid(event)
                            }
                        }
                    } else if (eventCache.value is LoadingData.Loading) {
                        eventCache.update {
                            LoadingData.Invalid(LoadingData.Reason.NotFound)
                        }
                    }
                }
            })
        }
        return eventCache
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
}