package io.github.tatakinov.treegrove.connection

interface OnDownloadListener {
    fun onData(url: String, data: ByteArray)
    fun onNoData(url: String)
    fun onTransmit(url: String, dataSize: Int)
}