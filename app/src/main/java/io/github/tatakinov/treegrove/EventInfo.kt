package io.github.tatakinov.treegrove

import io.github.tatakinov.treegrove.nostr.Event

data class EventInfo(val from : List<String>, val event : Event)
