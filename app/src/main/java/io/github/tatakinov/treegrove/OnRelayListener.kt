package io.github.tatakinov.treegrove

import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Filter
import okhttp3.Response

interface OnRelayListener {
    fun onConnected(relay: Relay)
    fun onEvent(relay: Relay, event : Event)
    fun onSuccessToPost(relay: Relay, event : Event, reason : String)
    fun onFailureToPost(relay : Relay, event : Event, reason : String)
    fun onEOSE(relay: Relay, filter : Filter, events : List<Event>) : Boolean
    fun onFailure(relay: Relay, t : Throwable, res : Response?)
    fun onTransmit(relay : Relay, dataSize : Int)
    fun onClose(relay : Relay, code : Int, reason : String)
}