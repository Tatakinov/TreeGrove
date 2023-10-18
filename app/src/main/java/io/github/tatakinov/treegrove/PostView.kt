package io.github.tatakinov.treegrove

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostView(onGetReplyEvent : () -> String?, onSubmit : (String) -> Unit, onCancel : () -> Unit) {
    var message by remember { mutableStateOf("") }
    Column {
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = {
                onSubmit(message)
            }, content = {
                Image(painterResource(id = R.drawable.send), contentDescription = "Send", modifier = Modifier.width(Const.ACTION_ICON_SIZE).height(Const.ACTION_ICON_SIZE))
            }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
            Button(onClick = {
                onCancel()
            }, content = {
                Image(painterResource(id = R.drawable.close), contentDescription = "Cancel", modifier = Modifier.width(Const.ACTION_ICON_SIZE).height(Const.ACTION_ICON_SIZE))
            }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
        }
        val name = onGetReplyEvent()
        if (name != null) {
            Text(modifier = Modifier.fillMaxWidth(), text = "@$name")
        }
        TextField(value = message, onValueChange = {
            message = it
        }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(Const.ICON_SIZE))
    }
}