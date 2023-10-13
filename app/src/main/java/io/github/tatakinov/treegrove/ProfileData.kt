package io.github.tatakinov.treegrove

import androidx.compose.ui.graphics.ImageBitmap

data class ProfileData(val name : String, val about : String = "", val pictureUrl : String = "", var image : LoadingDataStatus<ImageBitmap> = LoadingDataStatus()) {

    override fun equals(other: Any?): Boolean {
        if (other is ProfileData) {
            return name == other.name && about == other.about && pictureUrl == other.pictureUrl && image == other.image
        }
        return false
    }

    companion object {
        const val NAME    = "name"
        const val ABOUT   = "about"
        const val PICTURE = "picture"
    }
}