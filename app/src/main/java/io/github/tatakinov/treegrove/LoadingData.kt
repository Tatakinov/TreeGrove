package io.github.tatakinov.treegrove

sealed class LoadingData<T> {
    data class Valid<T> (val data: T): LoadingData<T>()
    class Invalid<T>(val reason: Reason) : LoadingData<T>()
    class Loading<T>: LoadingData<T>()
    class NotLoading<T>: LoadingData<T>()

    enum class Reason (reason: String) {
        NotFound("not_found"),
        ParseError("parse_error"),
        IOError("io_error")
    }
}