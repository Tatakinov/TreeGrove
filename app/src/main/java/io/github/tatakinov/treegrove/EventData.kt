package io.github.tatakinov.treegrove

import io.github.tatakinov.treegrove.nostr.Event

data class EventData(val from : List<String>, val event : Event)
