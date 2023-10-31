package io.github.tatakinov.treegrove

import android.widget.Toast
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.tatakinov.treegrove.nostr.NIP05

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMetaDataView(title : String, name : String, about : String, picture : String, nip05 : String,
                        modifier : Modifier, onSubmit : (String, String, String, String) -> Unit, onCancel : () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(name) }
    var about by remember { mutableStateOf(about) }
    var picture by remember { mutableStateOf(picture) }
    var nip05 by remember { mutableStateOf(nip05) }
    Box(modifier = modifier) {
        Column {
            Text(text = title, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background))
            TextField(value = name, onValueChange = {
                name = it.replace("\n", "")
            }, label = {
                Text(stringResource(R.string.name))
            }, modifier = Modifier.fillMaxWidth(), maxLines = 1)
            TextField(value = about, onValueChange = {
                about   = it.replace("\n", "")
            }, label = {
                Text(stringResource(R.string.about))
            }, modifier = Modifier.fillMaxWidth(), maxLines = 1)
            TextField(value = picture, onValueChange = {
                picture = it.replace("\n", "")
            }, label = {
                Text(stringResource(R.string.picture_url))
            }, modifier = Modifier.fillMaxWidth(), maxLines = 1)
            TextField(value = nip05, onValueChange = {
                nip05 = it.replace("\n", "")
            }, label = {
                Text(stringResource(R.string.nip05_address))
            }, modifier = Modifier.fillMaxWidth(), maxLines = 1)
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    if (name.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.error_channel_name_required), Toast.LENGTH_SHORT).show()
                    }
                    else if (nip05.isNotEmpty() && !NIP05.ADDRESS_REGEX.matches(nip05)) {
                        Toast.makeText(context, context.getString(R.string.error_invalid_address), Toast.LENGTH_SHORT).show()
                    }
                    else {
                        onSubmit(name, about, picture, nip05)
                    }
                }, content = {
                    Image(painterResource(id = R.drawable.send), contentDescription = stringResource(R.string.send), modifier = Modifier.width(Const.ACTION_ICON_SIZE).height(Const.ACTION_ICON_SIZE))
                }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
                Button(onClick = {
                    onCancel()
                }, content = {
                    Image(painterResource(id = R.drawable.close), contentDescription = stringResource(R.string.cancel), modifier = Modifier.width(Const.ACTION_ICON_SIZE).height(Const.ACTION_ICON_SIZE))
                }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
            }
        }
    }
}