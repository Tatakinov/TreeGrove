package io.github.tatakinov.treegrove.connection

import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import okhttp3.Response

interface OnRelayListener {
    fun onConnected(relay: Relay)
    fun onEvent(relay: Relay, event : Event)
    fun onEOSE(relay: Relay, eventList: List<Event>)
    fun onFailure(relay: Relay, t : Throwable, res : Response?)
    fun onTransmit(relay : Relay, dataSize : Int)
    fun onClose(relay : Relay, code : Int, reason : String)
}