package io.github.tatakinov.treegrove

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

@Composable
fun ActionView(modifier : Modifier, onClickGoToTop : () -> Unit, onClickGoToBottom : () -> Unit, onClickOpenPostView : () -> Unit) {
    val context = LocalContext.current
    Row(modifier = modifier) {
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = {
            onClickGoToTop()
        }, content = {
            Image(painterResource(id = R.drawable.upward), contentDescription = stringResource(R.string.description_move_to_top), modifier = Modifier
                .width(Const.ACTION_ICON_SIZE)
                .height(Const.ACTION_ICON_SIZE))
        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
        Button(onClick = {
            onClickGoToBottom()
        }, content = {
            Image(painterResource(id = R.drawable.downward), contentDescription = stringResource(R.string.description_move_to_bottom), modifier = Modifier
                .width(Const.ACTION_ICON_SIZE)
                .height(Const.ACTION_ICON_SIZE))
        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
        Button(onClick = {
            onClickOpenPostView()
        }, content = {
            Image(painterResource(id = R.drawable.edit), contentDescription = stringResource(R.string.description_move_to_post), modifier = Modifier
                .width(Const.ACTION_ICON_SIZE)
                .height(Const.ACTION_ICON_SIZE))
        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
    }
}