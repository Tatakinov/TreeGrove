package io.github.tatakinov.treegrove

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import io.github.tatakinov.treegrove.nostr.Event

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
                Image(painterResource(id = R.drawable.send), contentDescription = "Send", modifier = Modifier.width(Icon.size).height(Icon.size))
            }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background), modifier = Modifier.height(Icon.size))
            Button(onClick = {
                onCancel()
            }, content = {
                Image(painterResource(id = R.drawable.close), contentDescription = "Cancel", modifier = Modifier.width(Icon.size).height(Icon.size))
            }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background), modifier = Modifier.height(Icon.size))
        }
        val name = onGetReplyEvent()
        if (name != null) {
            Text(modifier = Modifier.fillMaxWidth(), text = "@$name")
        }
        TextField(value = message, onValueChange = {
            message = it
        }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(Icon.size))
    }
}