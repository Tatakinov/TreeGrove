package io.github.tatakinov.treegrove

import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

class TreeGroveViewModel(private val userPreferencesRepository: UserPreferencesRepository): ViewModel() {
    private val _mutex = Mutex()
    private val _relayList = mutableListOf<Relay>()
    private val _dataCache = mutableMapOf<String, LoadingData<ByteArray>>()
    private var _transmittedSize = 0
    private val _eventHashCache = hashSetOf<Event>()
    private val _eventCache = mutableListOf<Event>()
    private val _streamEventCache = mutableMapOf<Filter, MutableStateFlow<List<Event>>>()
    private val _replaceableEventCache = mutableMapOf<Filter, MutableStateFlow<LoadingData<ReplaceableEvent>>>()
    private val _oneShotEventCache = mutableMapOf<Filter, MutableStateFlow<LoadingData<Event>>>()
    private val _streamFilterList = mutableListOf<Filter>()

    private val _tabListFlow = MutableStateFlow<List<Screen>>(listOf())

    val privateKeyFlow = userPreferencesRepository.privateKeyFlow.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = UserPreferencesRepository.Default.privateKey)
    val publicKeyFlow = userPreferencesRepository.publicKeyFlow.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = UserPreferencesRepository.Default.privateKey)
    val relayConfigListFlow = userPreferencesRepository.relayConfigListFlow.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = UserPreferencesRepository.Default.relayList)
    val fetchSizeFlow = userPreferencesRepository.fetchSizeFlow.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = UserPreferencesRepository.Default.fetchSize)

    private val _relayInfoListFlow = MutableStateFlow(listOf<RelayInfo>())
    val relayInfoListFlow: StateFlow<List<RelayInfo>> = _relayInfoListFlow

    private val _downloader = Downloader(onTransmit = { url, dataSize ->
        _transmittedSize += dataSize
    })

    private suspend fun addEventCache(event: Event) = withContext(Dispatchers.Default) {
        _mutex.withLock {
            if (!_eventHashCache.contains(event)) {
                _eventHashCache.add(event)
                _eventCache.add(event)
            }
        }
    }

    suspend fun updateEventCache() = withContext(Dispatchers.Default) {
        for ((k, v) in _streamEventCache) {
            _mutex.withLock {
                val l = _eventCache.filter { k.cond(it) }
                if (v.value.size < l.size) {
                    v.update { l }
                }
            }
        }
        for (event in _eventCache.filter { it.kind == Kind.ChannelCreation.num }) {
            val r = ReplaceableEvent.parse(event)
            if (r != null) {
                val filter = Filter(kinds = listOf(Kind.ChannelMetadata.num), tags = mapOf("e" to listOf(event.id)))
                subscribeReplaceableEvent(filter)
                _replaceableEventCache[filter]!!.update {
                    if (it !is LoadingData.Valid) {
                        LoadingData.Valid(r)
                    }
                    else {
                        if (it.data.createdAt < r.createdAt) {
                            LoadingData.Valid(r)
                        }
                        else {
                            it
                        }
                    }
                }
            }
        }
    }

    suspend fun setRelayConfigList(relayConfigList: List<RelayConfig>) {
        for (relayConfig in relayConfigList) {
            if (_relayList.isEmpty() || _relayList.none{ it.url() == relayConfig.url}) {
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
                        if (_streamFilterList.isNotEmpty()) {
                            viewModelScope.launch(Dispatchers.IO) {
                                relay.sendStream(_streamFilterList)
                            }
                        }
                    }

                    override fun onEvent(relay: Relay, event: Event) {
                        viewModelScope.launch {
                            addEventCache(event)
                            _mutex.withLock {
                                _eventCache.sortByDescending { it.createdAt }
                            }
                            updateEventCache()
                        }
                    }

                    override fun onEOSE(relay: Relay, eventList: List<Event>) {
                        viewModelScope.launch {
                            for (event in eventList) {
                                addEventCache(event)
                            }
                            _mutex.withLock {
                                _eventCache.sortByDescending { it.createdAt }
                            }
                            updateEventCache()
                        }
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
                                    l[i] = l[i].copy(transmittedSize = l[i].transmittedSize + dataSize)
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
            }
            else {
                index++
            }
        }
        connectRelay()
    }

    fun connectRelay() {
        viewModelScope.launch {
            for (relay in _relayList) {
                relay.connect()
            }
        }
    }

    suspend fun updatePreferences(userPreferences: UserPreferences) {
        userPreferencesRepository.updatePrivateKey(userPreferences.privateKey)
        userPreferencesRepository.updatePublicKey(userPreferences.publicKey)
        userPreferencesRepository.updateRelayList(userPreferences.relayList)
        userPreferencesRepository.updateFetchSize(userPreferences.fetchSize)
    }

    private suspend fun addStream(filter : Filter) = withContext(Dispatchers.IO) {
        if (!_streamFilterList.contains(filter)) {
            _streamFilterList.add(filter)
        }
        for (relay in _relayList) {
            relay.sendStream(_streamFilterList)
        }
    }

    private suspend fun addOneShot(filter: Filter, onReceive: (List<Event>) -> Unit) = withContext(Dispatchers.IO) {
        for (relay in _relayList) {
            relay.sendOneShot(filter, onReceive)
        }
        Log.d("debug", "addOneShot")
    }

    fun subscribeStreamEvent(filter: Filter): StateFlow<List<Event>> {
        if (!_streamEventCache.containsKey(filter)) {
            _streamEventCache[filter] = MutableStateFlow(listOf())
        }
        viewModelScope.launch {
            Log.d("debug", "subscribe")
            addStream(filter.copy(limit = 0))
        }
        return _streamEventCache[filter]!!
    }

    suspend fun fetchPastPost(filter: Filter) = withContext(Dispatchers.Default) {
        val list = _eventCache.filter { filter.cond(it) }
        val until = if (list.isNotEmpty()) {
            val last = list.last()
            last.createdAt
        }
        else {
            0
        }
        withContext(Dispatchers.IO) {
            addOneShot(
                filter.copy(limit = fetchSizeFlow.value, until = until),
                onReceive = { eventList ->
                    viewModelScope.launch {
                        for (event in eventList) {
                            addEventCache(event)
                        }
                        updateEventCache()
                    }
                })
        }
    }

    fun subscribeReplaceableEvent(filter: Filter): StateFlow<LoadingData<ReplaceableEvent>> {
        if (!_replaceableEventCache.containsKey(filter)) {
            _replaceableEventCache[filter] = MutableStateFlow(LoadingData.NotLoading())
        }
        val eventCache = _replaceableEventCache[filter]!!
        if (eventCache.value is LoadingData.NotLoading) {
            eventCache.update { LoadingData.Loading() }
            viewModelScope.launch {
                addOneShot(filter, onReceive = { eventList ->
                    viewModelScope.launch {
                        _mutex.withLock {
                            if (eventList.isNotEmpty()) {
                                val e = eventList.first()
                                if (eventCache.value !is LoadingData.Valid) {
                                    val r = ReplaceableEvent.parse(e)
                                    viewModelScope.launch {
                                        _mutex.withLock {
                                            if (r != null) {
                                                eventCache.update { LoadingData.Valid(r) }
                                            } else {
                                                eventCache.update { LoadingData.Invalid(LoadingData.Reason.ParseError) }
                                            }
                                        }
                                    }
                                } else {
                                    val current = eventCache.value
                                    if (current is LoadingData.Valid && current.data.createdAt < e.createdAt) {
                                        val r = ReplaceableEvent.parse(e)
                                        if (r != null) {
                                            eventCache.update { LoadingData.Valid(r) }
                                            if (r is ReplaceableEvent.MetaData && r.nip05.domain.isNotEmpty()) {
                                                viewModelScope.launch(Dispatchers.IO) {
                                                    _downloader.get(
                                                        NIP05.generateIdentifyURL(
                                                            r.nip05.domain,
                                                            r.name
                                                        ),
                                                        allowRedirect = false,
                                                        onReceive = { url, data ->
                                                            viewModelScope.launch {
                                                                _mutex.withLock {
                                                                    if (data is LoadingData.Valid) {
                                                                        try {
                                                                            val json =
                                                                                JSONObject(data.toString())
                                                                            val names =
                                                                                json.getJSONObject(
                                                                                    NIP05.NAMES
                                                                                )
                                                                            if (names.getString(r.name) == e.pubkey) {
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
                                                                                val d = value.data
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
                                                                        val value = eventCache.value
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
                                                            }
                                                        })
                                                }
                                            }
                                        } else if (eventCache.value is LoadingData.Loading) {
                                            eventCache.update { LoadingData.Invalid(LoadingData.Reason.ParseError) }
                                        }
                                    }
                                }
                            } else {
                                val data = eventCache.value
                                if (data is LoadingData.Loading) {
                                    _replaceableEventCache[filter]?.update {
                                        LoadingData.Invalid(LoadingData.Reason.NotFound)
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }
        return eventCache
    }

    fun subscribeOneShotEvent(filter: Filter): StateFlow<LoadingData<Event>> {
        if (!_oneShotEventCache.containsKey(filter)) {
            _oneShotEventCache[filter] = MutableStateFlow(LoadingData.NotLoading())
        }
        val eventCache = _oneShotEventCache[filter]!!
        if (eventCache.value is LoadingData.NotLoading) {
            eventCache.update {
                LoadingData.Loading()
            }
            viewModelScope.launch {
                addOneShot(filter.copy(limit = 1), onReceive = { eventList ->
                    viewModelScope.launch {
                        _mutex.withLock {
                            if (eventList.isNotEmpty()) {
                                val event = eventList.first()
                                eventCache.update {
                                    LoadingData.Valid(event)
                                }
                            } else if (eventCache.value is LoadingData.Loading) {
                                eventCache.update {
                                    LoadingData.Invalid(LoadingData.Reason.NotFound)
                                }
                            }
                        }
                    }
                })
            }
        }
        return eventCache
    }

    fun post(event: Event, onSuccess: () -> Unit, onFailure: (String, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            for (relay in _relayList) {
                relay.send(event, onSuccess, onFailure)
            }
        }
    }

    fun getTabListFlow(): StateFlow<List<Screen>> {
        return _tabListFlow
    }

    fun addTabList(screen: Screen) {
        _tabListFlow.update {
            val list = mutableListOf<Screen>().apply {
                addAll(it)
                if (!contains(screen)) {
                    add(screen)
                }
            }
            list
        }
    }

    fun removeTabList(screen: Screen) {
        _tabListFlow.update {
            val list = mutableListOf<Screen>().apply {
                addAll(it)
                if (contains(screen)) {
                    remove(screen)
                }
            }
            list
        }
    }

    fun replaceOwnTimelineTab(index: Int, id: String) {
        _tabListFlow.update {
            if (index >= it.size) {
                it
            }
            else {
                val list = mutableListOf<Screen>().apply {
                    addAll(it)
                }
                list[index] = Screen.OwnTimeline(id = id)
                list
            }
        }
    }
}