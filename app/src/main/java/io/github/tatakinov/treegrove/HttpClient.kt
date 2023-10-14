package io.github.tatakinov.treegrove

import okhttp3.OkHttpClient

object HttpClient {
    val default    = OkHttpClient.Builder().build()
    val noRedirect = OkHttpClient.Builder().followRedirects(false).build()
}