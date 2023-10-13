package io.github.tatakinov.treegrove

data class LoadingDataStatus<T>(val status : DataStatus = DataStatus.NotLoading, val data : T? = null) {
}