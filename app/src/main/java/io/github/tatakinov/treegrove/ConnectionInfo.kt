package io.github.tatakinov.treegrove

import io.github.tatakinov.treegrove.nostr.Filter

data class ConnectionInfo(val filter : Filter, val status : ConnectionStatus) {
}

enum class ConnectionStatus {
    Wait,
    Connecting,
    Stream
}