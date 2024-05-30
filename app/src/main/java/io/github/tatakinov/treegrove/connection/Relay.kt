package io.github.tatakinov.treegrove.connection

import android.util.Log
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.withLock
import org.json.JSONArray
import org.json.JSONException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class Data1(val status: ConnectionStatus, val onReceive: (String, List<Event>) -> Unit)
data class Data2(val event: Event, val onSuccess: () -> Unit, val onFailure: (String, String) -> Unit)

class Relay (private val _url : String, private var _read: Boolean = true, private var _write: Boolean = true, private val _listener : OnRelayListener) {
    private val streamID = "stream"
    private val oneShotID = "oneshot"
    private var _socket: WebSocket? = null
    private val _lock = ReentrantLock()
    private val _postQueue = mutableListOf<Data2>()
    private var _stream: Set<Filter>? = null
    private val _oneShotQueue = mutableMapOf<Filter, Data1>()
    private var _oneShotEventList: MutableList<Event>? = null

    fun url(): String {
        return _url
    }

    fun change(read: Boolean, write: Boolean) {
        _read = read
        _write = write
    }

    fun connect() {
        if (isConnected()) {
            return
        }
        val req = Request.Builder().url(_url).build()
        _socket = HttpClient.default.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("Relay.onOpen", _url)
                _listener.onTransmit(this@Relay, response.toString().toByteArray().size)
                _listener.onConnected(this@Relay)
                sendStream()
                sendOneShot()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("Relay.onMessage.${_url}", text)
                _listener.onTransmit(this@Relay, text.toByteArray().size)
                try {
                    val json = JSONArray(text)
                    when (json.getString(0)) {
                        "EVENT" -> {
                            val subscriptionID = json.getString(1)
                            val event = Event.parse(json.getJSONObject(2))
                            if ((event.id == Event.generateHash(
                                    event,
                                    true
                                ) || event.id == Event.generateHash(event, false)) &&
                                event.verify()
                            ) {
                                when (subscriptionID) {
                                    streamID -> {
                                        _lock.withLock {
                                            _listener.onEvent(this@Relay, event)
                                        }
                                    }
                                    oneShotID -> {
                                        _lock.withLock {
                                            _oneShotEventList?.let { list ->
                                                list.add(event)
                                                list.sortByDescending { e -> e.createdAt }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Log.d("Relay.EVENT", "invalid id or sig")
                            }
                        }

                        "EOSE" -> {
                            when (val subscriptionID = json.getString(1)) {
                                streamID -> {
                                    _listener.onEOSE(this@Relay)
                                }
                                oneShotID -> {
                                    close(subscriptionID)
                                    _lock.withLock {
                                        val keySet = mutableSetOf<Filter>()
                                        for ((k, v) in _oneShotQueue) {
                                            if (v.status == ConnectionStatus.Connecting) {
                                                _oneShotQueue[k]!!.onReceive(_url, _oneShotEventList!!.filter { k.cond(it) })
                                                keySet.add(k)
                                            }
                                        }
                                        for (key in keySet) {
                                            _oneShotQueue.remove(key)
                                        }
                                        _oneShotEventList = null
                                    }
                                    sendOneShot()
                                }
                            }
                        }

                        "OK" -> {
                            val eventID = json.getString(1)
                            val isOK = json.getBoolean(2)
                            val reason = json.getString((3))
                            val list = _postQueue.filter { it.event.id == eventID }
                            if (list.isNotEmpty()) {
                                val data = list.first()
                                _lock.withLock {
                                    _postQueue.remove(data)
                                }
                                if (isOK) {
                                    data.onSuccess()
                                }
                                else {
                                    data.onFailure(this@Relay._url, reason)
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
                _lock.withLock {
                    for ((k, v) in _oneShotQueue) {
                        if (v.status == ConnectionStatus.Connecting) {
                            _oneShotQueue[k] = v.copy(status = ConnectionStatus.Wait)
                            break
                        }
                    }
                }
                _listener.onFailure(this@Relay, t, response)
                _socket = null
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("Relay", bytes.hex())
            }
        })
    }

    private fun send(message: String) {
        Log.d("Relay.send", message)
        _lock.withLock {
            _listener.onTransmit(this, message.toByteArray().size)
            _socket?.send(message)
        }
    }

    fun send(event: Event, onSuccess: () -> Unit, onFailure: (String, String) -> Unit) {
        if (!_write) {
            return
        }
        val request = JSONArray()
        request.put("EVENT")
        request.put(event.toJSONObject())
        _lock.withLock {
            _postQueue.add(Data2(event, onSuccess, onFailure))
        }
        send(request.toString())
    }

    private fun send(filterSet: Set<Filter>, id : String) {
        if (!_read) {
            return
        }
        val request = JSONArray()
        request.put("REQ")
        request.put(id)
        for (filter in filterSet) {
            request.put(filter.toJSONObject())
        }
        send(request.toString())
    }

    private fun sendStream() {
        if (_socket == null) {
            return
        }
        _stream?.let {
            if (it.isNotEmpty()) {
                send(it, streamID)
            }
        }
    }

    fun sendStream(filterSet: Set<Filter>) {
        val set = mutableSetOf<Filter>().apply {
            addAll(filterSet)
        }
        if (_stream != set) {
            _stream = set
            sendStream()
        }
    }

    private fun sendOneShot() {
        if (_socket == null) {
            return
        }
        val filterSet = mutableSetOf<Filter>()
        _lock.withLock {
            if (_oneShotQueue.any { it.value.status == ConnectionStatus.Connecting }) {
                return
            }
            for ((k, v) in _oneShotQueue) {
                if (v.status == ConnectionStatus.Wait) {
                    _oneShotQueue[k] = _oneShotQueue[k]!!.copy(status = ConnectionStatus.Connecting)
                    if (filterSet.size < 10) {
                        filterSet.add(k)
                    }
                }
            }
        }
        if (filterSet.isNotEmpty()) {
            _oneShotEventList = mutableListOf()
            send(filterSet, oneShotID)
        }
    }

    fun sendOneShot(filter: Filter, onReceive: (String, List<Event>) -> Unit) {
        _lock.withLock {
            if (!_oneShotQueue.contains(filter)) {
                _oneShotQueue[filter] = Data1(ConnectionStatus.Wait, onReceive)
            }
        }
        sendOneShot()
    }

    fun close(subscriptionID: String) {
        val array   = JSONArray()
        array.put("CLOSE")
        array.put(subscriptionID)
        send(array.toString())
    }

    fun isConnected(): Boolean {
        return _socket != null
    }
}
