package io.github.tatakinov.treegrove.connection

import android.util.Log
import io.github.tatakinov.treegrove.LoadingData
import okhttp3.Request
import okio.IOException
import okio.withLock
import java.util.concurrent.locks.ReentrantLock

class Downloader(private val onTransmit: (String, Int) -> Unit) {
    private val _lock = ReentrantLock()

    fun get(url: String, allowRedirect: Boolean = true, onReceive: (String, LoadingData<ByteArray>) -> Unit) {
        val request = Request.Builder().url(url).build()
        onTransmit(url, request.toString().toByteArray().size)
        val client = if (allowRedirect) { HttpClient.default } else { HttpClient.noRedirect }
        try {
            val response = client.newCall(request).execute()
            onTransmit(url, response.toString().toByteArray().size)
            val data = response.body?.bytes()
            val loadingData = if (data != null) {
                onTransmit(url, data.size)
                LoadingData.Valid(data)
            } else {
                LoadingData.Invalid(LoadingData.Reason.NotFound)
            }
            onReceive(url, loadingData)
        }
        catch (e: IOException) {
            onReceive(url, LoadingData.Invalid(LoadingData.Reason.IOError))
        }
    }
}