package io.github.tatakinov.treegrove.nostr

import io.github.tatakinov.treegrove.LoadingData

object NIP05 {
    val NAMES = "names"
    val ADDRESS_REGEX = "^([\\w!?/+\\-_~;.,*&@#$%()'\\[\\]]+)@([0-9A-Za-z!?/+\\-_~;.,*&@#\$%()'\\[\\]]+)$".toRegex()

    fun generateIdentifyURL(domain : String, username : String) : String {
        return "https://$domain/.well-known/nostr.json?name=$username"
    }

    data class Data(val domain: String, val identify: LoadingData<Boolean>)
}