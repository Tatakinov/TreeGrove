package io.github.tatakinov.treegrove

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DrawerMenuItem(metaData : MetaData, onClick : () -> Unit, onLongPress : () -> Unit) {
    Row(modifier = Modifier.fillMaxSize().padding(top = 10.dp, bottom = 10.dp).pointerInput(Unit) {
        detectTapGestures(
            onLongPress = {
                onLongPress()
            },
            onTap = {
                onClick()
            }
        )
    }, verticalAlignment = Alignment.CenterVertically) {
        if (Config.config.displayProfilePicture) {
            if (metaData.image.status != DataStatus.Valid) {
                Image(
                    painterResource(id = R.drawable.no_image),
                    contentDescription = "no image",
                    modifier = Modifier
                        .width(Const.ICON_SIZE)
                        .height(Const.ICON_SIZE)
                )
            } else {
                Image(
                    metaData.image.data!!,
                    contentDescription = metaData.name,
                    modifier = Modifier
                        .width(Const.ICON_SIZE)
                        .height(Const.ICON_SIZE)
                )
            }
        }
        Text(metaData.name, modifier = Modifier.padding(start = 10.dp)
            , maxLines = 2, overflow = TextOverflow.Clip)
    }
}