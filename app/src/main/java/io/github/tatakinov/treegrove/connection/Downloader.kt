package io.github.tatakinov.treegrove.connection

import android.util.Log
import io.github.tatakinov.treegrove.LoadingData
import okhttp3.Request
import okio.withLock
import java.util.concurrent.locks.ReentrantLock

class Downloader(private val onTransmit: (String, Int) -> Unit) {
    private val _lock = ReentrantLock()
    private val _cache = mutableMapOf<String, LoadingData<ByteArray>>()

    fun get(url: String, allowRedirect: Boolean = true, force: Boolean = false, onReceive: (String, LoadingData<ByteArray>) -> Unit) {
        _lock.withLock {
            if (!_cache.containsKey(url)) {
                _cache[url] = LoadingData.Loading()
            } else if (!force && _cache.containsKey(url)) {
                val data = _cache[url]!!
                when (data) {
                    is LoadingData.Valid -> {
                        onReceive(url, _cache[url]!!)
                        return
                    }

                    is LoadingData.Invalid -> {
                        onReceive(url, _cache[url]!!)
                        return
                    }

                    is LoadingData.Loading -> {
                        return
                    }

                    else -> {
                        // unreachable
                    }
                }
            }
        }
        val request = Request.Builder().url(url).build()
        onTransmit(url, request.toString().toByteArray().size)
        val client = if (allowRedirect) { HttpClient.default } else { HttpClient.noRedirect }
        val response = client.newCall(request).execute()
        onTransmit(url, response.toString().toByteArray().size)
        val data = response.body
        val loadingData = if (data != null) {
            LoadingData.Valid(data.bytes())
        }
        else {
            LoadingData.Invalid(LoadingData.Reason.NotFound)
        }
        _cache[url] = loadingData
        onReceive(url, loadingData)
    }
}