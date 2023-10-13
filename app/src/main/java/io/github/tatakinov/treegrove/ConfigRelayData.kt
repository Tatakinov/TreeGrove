package io.github.tatakinov.treegrove

data class ConfigRelayData(val url : String, var read : Boolean = true, var write : Boolean = true) {
    override fun equals(other: Any?): Boolean {
        if (other is ConfigRelayData) {
            return url == other.url
        }
        return false
    }
}