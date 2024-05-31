package io.github.tatakinov.treegrove.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val icon : ImageVector) {
    abstract val id: String

    data class OwnTimeline(override val id: String): Screen(icon = Icons.Default.Home)
    data class Timeline(override val id: String): Screen(icon = Icons.Default.Person)
    data class Channel(override val id: String, val pubkey: String): Screen(icon = Icons.Default.Forum)
    data class EventDetail(override val id: String, val pubkey: String): Screen(icon = Icons.Default.Info)
    data class ChannelEventDetail(override val id: String, val pubkey: String, val channelID: String): Screen(icon = Icons.Default.Info)
    data class Notification(override val id: String): Screen(icon = Icons.Default.Notifications)
}
