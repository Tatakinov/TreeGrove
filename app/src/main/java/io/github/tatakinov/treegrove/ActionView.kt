package io.github.tatakinov.treegrove

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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

@Composable
fun ActionView(modifier : Modifier, onClickGoToTop : () -> Unit, onClickGoToBottom : () -> Unit, onClickOpenPostView : () -> Unit) {
    val context = LocalContext.current
    Row(modifier) {
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = {
            onClickGoToTop()
        }, content = {
            Image(painterResource(id = R.drawable.upward), contentDescription = context.getString(R.string.description_move_to_top), modifier = Modifier
                .width(Icon.size)
                .height(Icon.size))
        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
        Button(onClick = {
            onClickGoToBottom()
        }, content = {
            Image(painterResource(id = R.drawable.downward), contentDescription = context.getString(R.string.description_move_to_bottom), modifier = Modifier
                .width(Icon.size)
                .height(Icon.size))
        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
        Button(onClick = {
            onClickOpenPostView()
        }, content = {
            Image(painterResource(id = R.drawable.edit), contentDescription = context.getString(R.string.description_move_to_post), modifier = Modifier
                .width(Icon.size)
                .height(Icon.size))
        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
    }
}