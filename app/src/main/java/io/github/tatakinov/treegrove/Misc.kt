package io.github.tatakinov.treegrove

import android.widget.Toast
import io.github.tatakinov.treegrove.nostr.Event
import io.github.tatakinov.treegrove.nostr.Kind
import io.github.tatakinov.treegrove.nostr.NIP19
import io.github.tatakinov.treegrove.nostr.ReplaceableEvent
import kotlinx.coroutines.launch

object Misc {
    fun postContacts(viewModel: TreeGroveViewModel, list: List<ReplaceableEvent.Contacts.Data>,
                     priv: NIP19.Data.Sec, pub: NIP19.Data.Pub, onSuccess: () -> Unit, onFailure: (String, String) -> Unit) {
        val tags = mutableListOf<List<String>>()
        for (tag in list) {
            tags.add(listOf("p", tag.key, tag.relay, tag.petname))
        }
        val e = Event(kind = Kind.Contacts.num, content = "", createdAt = now(),
            pubkey = pub.id, tags = tags)
        e.id = Event.generateHash(e, false)
        e.sig = Event.sign(e, priv.id)
        viewModel.post(e, onSuccess = onSuccess, onFailure = onFailure)
    }

    fun post(viewModel: TreeGroveViewModel, kind: Int, content: String, tags: List<List<String>> = listOf(), priv: NIP19.Data.Sec, pub: NIP19.Data.Pub,
             onSuccess: () -> Unit, onFailure: (String, String) -> Unit) {
        val e = Event(kind = kind, content = content, tags = tags,
            createdAt = now(), pubkey = pub.id)
        e.id = Event.generateHash(e, false)
        e.sig = Event.sign(e, priv.id)
        viewModel.post(e, onSuccess = onSuccess, onFailure = onFailure)
    }

    fun repost(viewModel: TreeGroveViewModel, event: Event, priv: NIP19.Data.Sec, pub: NIP19.Data.Pub,
               onSuccess: () -> Unit, onFailure: (String, String) -> Unit) {
        val urlList = viewModel.getURL(event)
        if (urlList.isNotEmpty()) {
            val url = urlList.first()
            val kind = if (event.kind == Kind.Text.num) {
                Kind.Repost.num
            }
            else {
                Kind.GenericRepost.num
            }
            val tags = mutableListOf(listOf("e", event.id, url), listOf("p", event.pubkey, url))
            if (event.kind != Kind.Text.num) {
                tags.add(listOf("k", event.kind.toString()))
            }
            val e = Event(kind = kind, content = event.toJSONObject().toString(), tags = tags,
                createdAt = now(), pubkey = pub.id)
            e.id = Event.generateHash(e, false)
            e.sig = Event.sign(e, priv.id)
            viewModel.post(e, onSuccess = onSuccess, onFailure = onFailure)
        }
        else {
            TODO("logic error?")
        }
    }

    fun now() : Long {
        return System.currentTimeMillis() / 1000
    }
}