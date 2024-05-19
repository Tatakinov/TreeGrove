package io.github.tatakinov.treegrove.connection

data class RelayInfo(val url: String, val isConnected: Boolean = false, val transmittedSize: Int = 0)
