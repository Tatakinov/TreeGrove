package io.github.tatakinov.treegrove.connection

import android.util.Log
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class Data1(val status: ConnectionStatus, val onReceive: (List<Event>) -> Unit)
data class Data2(val event: Event, val onSuccess: () -> Unit, val onFailure: (String, String) -> Unit)

class Relay (private val _url : String, private val _listener : OnRelayListener) {
    private val streamID = "stream"
    private val oneShotID = "oneshot"
    private var _socket: WebSocket? = null
    private val _lock = ReentrantLock()
    private val _postQueue = mutableListOf<Data2>()
    private var _stream: List<Filter>? = null
    private var _streamEventList: MutableList<Event>? = null
    private val _oneShotQueue = mutableMapOf<List<Filter>, Data1>()
    private var _oneShotEventList: MutableList<Event>? = null
    private var _read = true
    private var _write = true

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
                                        _streamEventList?.let { list ->
                                            list.add(event)
                                            list.sortByDescending { e -> e.createdAt }
                                        } ?: _listener.onEvent(this@Relay, event)
                                    }
                                    oneShotID -> {
                                        _oneShotEventList?.let { list ->
                                            list.add(event)
                                            list.sortByDescending { e -> e.createdAt }
                                        }
                                    }
                                }
                            } else {
                                Log.d("Relay.EVENT", "invalid id or sig")
                            }
                        }

                        "EOSE" -> {
                            val subscriptionID = json.getString(1)
                            when (subscriptionID) {
                                streamID -> {
                                    _listener.onEOSE(this@Relay, _streamEventList!!)
                                    _streamEventList = null
                                }
                                oneShotID -> {
                                    close(subscriptionID)
                                    _lock.withLock {
                                        lateinit var key: List<Filter>
                                        for ((k, v) in _oneShotQueue) {
                                            if (v.status == ConnectionStatus.Connecting) {
                                                key = k
                                                break
                                            }
                                        }
                                        _oneShotQueue[key]!!.onReceive(_oneShotEventList!!)
                                        _oneShotQueue.remove(key)
                                    }
                                    _oneShotEventList = null
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

    private fun send(filterList: List<Filter>, id : String) {
        if (!_read) {
            return
        }
        val request = JSONArray()
        request.put("REQ")
        request.put(id)
        for (filter in filterList) {
            request.put(filter.toJSONObject())
        }
        send(request.toString())
    }

    private fun sendStream() {
        _stream?.let {
            _streamEventList = mutableListOf()
            send(it, streamID)
        }
    }

    fun sendStream(filterList: List<Filter>) {
        _stream = filterList
        sendStream()
    }

    private fun sendOneShot() {
        var filterList: List<Filter>? = null
        _lock.withLock {
            if (_oneShotQueue.any { it.value.status == ConnectionStatus.Connecting }) {
                return
            }
            for ((k, v) in _oneShotQueue) {
                if (v.status == ConnectionStatus.Wait) {
                    _oneShotQueue[k] = _oneShotQueue[k]!!.copy(status = ConnectionStatus.Connecting)
                    filterList = k
                    break
                }
            }
        }
        filterList?.let {
            _oneShotEventList = mutableListOf()
            send(it, oneShotID)
        }
    }

    private fun sendOneShot(filterList: List<Filter>, onReceive: (List<Event>) -> Unit) {
        _lock.withLock {
            if (!_oneShotQueue.containsKey(filterList)) {
                _oneShotQueue[filterList] = Data1(ConnectionStatus.Wait, onReceive)
            }
        }
        sendOneShot()
    }

    fun sendOneShot(filter: Filter, onReceive: (List<Event>) -> Unit) {
        sendOneShot(listOf(filter), onReceive)
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
