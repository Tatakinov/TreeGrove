package io.github.tatakinov.treegrove.nostr

object NIP05 {
    val NAMES = "names"
    val ADDRESS_REGEX = "^([\\w!?/+\\-_~;.,*&@#$%()'\\[\\]]+)@([0-9A-Za-z!?/+\\-_~;.,*&@#\$%()'\\[\\]]+)$".toRegex()
}