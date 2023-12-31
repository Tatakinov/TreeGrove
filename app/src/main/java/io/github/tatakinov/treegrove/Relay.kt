package io.github.tatakinov.treegrove

import android.util.Log
import fr.acinq.secp256k1.Hex
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import io.github.tatakinov.treegrove.nostr.Kind
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

class Relay (private val _config : ConfigRelayData, private val _listener : OnRelayListener) {
    private var _socket : WebSocket? = null
    private var _postQueue  = HashMap<String, Event>()
    private var _filterQueue  = HashMap<String, ConnectionInfo>()
    private val _postBuffer = mutableMapOf<String, MutableList<Event>>()
    private val reentrantLock = ReentrantLock()
    var read = _config.read
    var write = _config.write
    val url : String get() = _config.url

    fun connect() {
        if (isConnected()) {
            return
        }
        val req = Request.Builder().url(_config.url).build()
        _socket = HttpClient.default.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _listener.onTransmit(this@Relay, response.toString().toByteArray().size)
                _listener.onConnected(this@Relay)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("Relay.onMessage.${_config.url}", text)
                _listener.onTransmit(this@Relay, text.toByteArray().size)
                try {
                    val json = JSONArray(text)
                    when (json.getString(0)) {
                        "EVENT" -> {
                            val subscriptionId = json.getString(1)
                            if (_filterQueue.contains(subscriptionId)) {
                                val event = Event.parse(json.getJSONObject(2))
                                if ((event.id == Event.generateHash(
                                        event,
                                        true
                                    ) || event.id == Event.generateHash(event, false)) &&
                                    event.verify()
                                ) {
                                    if (_postBuffer.contains(subscriptionId)) {
                                        _postBuffer[subscriptionId]!!.add(event)
                                    } else {
                                        _listener.onEvent(this@Relay, event)
                                    }
                                } else {
                                    Log.d("Relay.EVENT", "invalid id or sig")
                                }
                            } else {
                                Log.d("Relay.EVENT", "no subscription id matched")
                            }
                        }

                        "EOSE" -> {
                            val subscriptionId = json.getString(1)
                            if (_filterQueue.contains(subscriptionId)) {
                                val info = _filterQueue[subscriptionId]!!
                                if (_listener.onEOSE(
                                        this@Relay,
                                        info.filterList,
                                        _postBuffer[subscriptionId]!!
                                    )
                                ) {
                                    reentrantLock.withLock {
                                        _filterQueue.remove(subscriptionId)
                                    }
                                    close(subscriptionId)
                                }
                                else {
                                    reentrantLock.withLock {
                                        _filterQueue[subscriptionId] = _filterQueue[subscriptionId]!!.copy(status = ConnectionStatus.Stream)
                                    }
                                }
                                var key = ""
                                var value: ConnectionInfo? = null
                                reentrantLock.withLock {
                                    _postBuffer.remove(subscriptionId)
                                    for ((k, v) in _filterQueue) {
                                        if (v.status == ConnectionStatus.Wait) {
                                            _filterQueue[k] = _filterQueue[k]!!.copy(status = ConnectionStatus.Connecting)
                                            key = k
                                            value = v
                                            break
                                        }
                                    }
                                }
                                if (key.isNotEmpty() && value != null) {
                                    send(value!!.filterList, key)
                                }
                            }
                        }

                        "OK" -> {
                            val eventId = json.getString(1)
                            val isOK = json.getBoolean(2)
                            val reason = json.getString((3))
                            if (_postQueue.contains(eventId)) {
                                lateinit var event : Event
                                reentrantLock.withLock {
                                    event = _postQueue.remove(eventId)!!
                                }
                                if (isOK) {
                                    _listener.onSuccessToPost(this@Relay, event, reason)
                                } else {
                                    _listener.onFailureToPost(this@Relay, event, reason)
                                }
                            }
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("Relay", "OnClosing")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("Relay", "OnClosed")
                _listener.onClose(this@Relay, code, reason)
                _socket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d("Relay", "onFailure")
                Log.d("Relay", response.toString())
                Log.d("Relay", t.stackTraceToString())
                _listener.onFailure(this@Relay, t, response)
                _socket = null
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("Relay", bytes.hex())
            }
        })
    }

    private fun send(message : String) {
        _listener.onTransmit(this, message.toByteArray().size)
        Log.d("Relay.send.${_config.url}", message)
        reentrantLock.withLock {
            _socket?.send(message)
        }
    }

    private fun send(filterList: List<Filter>, id : String) {
        if (!_config.read) {
            return
        }
        reentrantLock.withLock {
            _postBuffer[id] = mutableListOf()
        }
        val request = JSONArray()
        request.put("REQ")
        request.put(id)
        for (filter in filterList) {
            request.put(filter.toJSONObject())
        }
        send(request.toString())
    }

    fun send(filterList : List<Filter>) {
        var id: String
        do {
            val bytes = ByteArray(10)
            Random.nextBytes(bytes)
            id = Hex.encode(bytes)
        } while(_filterQueue.contains(id))
        var canConnect = true
        reentrantLock.withLock {
            canConnect = _filterQueue.none { it.value.status == ConnectionStatus.Connecting }
            _filterQueue[id] = if (canConnect) {
                ConnectionInfo(filterList, ConnectionStatus.Connecting)
            }
            else {
                ConnectionInfo(filterList, ConnectionStatus.Wait)
            }
        }
        if (canConnect) {
            send(filterList, id)
        }
    }

    fun send(filter : Filter) {
        send(listOf(filter))
    }

    fun send(event : Event) {
        if (!_config.write) {
            return
        }
        val request = JSONArray()
        request.put("EVENT")
        request.put(event.toJSONObject())
        reentrantLock.withLock {
            _postQueue[event.id] = event
        }
        send(request.toString())
    }

    fun close(subscriptionId : String) {
        val array   = JSONArray()
        array.put("CLOSE")
        array.put(subscriptionId)
        send(array.toString())
    }

    fun closePostFilter() {
        val list = mutableListOf<String>()
        reentrantLock.withLock {
            for ((k, v) in _filterQueue) {
                if (v.filterList.any { it.kinds.contains(Kind.ChannelMessage.num) }) {
                    list.add((k))
                }
            }
            for (v in list) {
                _filterQueue.remove(v)
            }
        }
        for (v in list) {
            close(v)
        }
    }

    fun closeAllFilter() {
        val list = mutableListOf<String>()
        reentrantLock.withLock {
            for ((k, v) in _filterQueue) {
                list.add((k))
            }
            for (v in list) {
                _filterQueue.remove(v)
            }
        }
        for (v in list) {
            close(v)
        }
    }

    fun close(code : Int = 1000, reason : String? = null) {
        if (isConnected()) {
            _socket!!.close(code, reason)
        }
    }

    fun isConnected() : Boolean {
        return _socket != null
    }
}
