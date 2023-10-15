package io.github.tatakinov.treegrove

import androidx.compose.ui.graphics.ImageBitmap

data class MetaData(val createdAt : Long, val name : String, val about : String = "", val pictureUrl : String = "",
                    val nip05Address : String = "",
                    var image : LoadingDataStatus<ImageBitmap> = LoadingDataStatus(),
                    var nip05 : LoadingDataStatus<Boolean> = LoadingDataStatus()
) {

    override fun equals(other: Any?): Boolean {
        if (other is MetaData) {
            return name == other.name && about == other.about && pictureUrl == other.pictureUrl &&
                    image == other.image && nip05Address == other.nip05Address && nip05 == other.nip05
        }
        return false
    }

    companion object {
        const val NAME    = "name"
        const val ABOUT   = "about"
        const val PICTURE = "picture"
        const val NIP05   = "nip05"
    }
}