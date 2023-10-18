package io.github.tatakinov.treegrove

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import fr.acinq.secp256k1.Hex
import io.github.tatakinov.treegrove.nostr.Keys
import io.github.tatakinov.treegrove.nostr.NIP19
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingView(onUpdated : () -> Unit) {
    val context = LocalContext.current
    val coroutineScope  = rememberCoroutineScope()
    val privInit    = if (Config.config.privateKey.isEmpty()) {
        ""
    }
    else {
        NIP19.encode("nsec", Hex.decode(Config.config.privateKey))
    }
    var privateKey by remember { mutableStateOf(privInit) }
    val relayList = remember { mutableStateListOf<ConfigRelayData>().apply {
        addAll(Config.config.relayList)
    } }
    val state   = rememberLazyListState()
    var fetchSize by remember { mutableStateOf(Config.config.fetchSize.toString()) }
    var displayProfilePicture by remember { mutableStateOf(Config.config.displayProfilePicture) }
    var fetchProfilePictureOnlyWifi by remember { mutableStateOf(Config.config.fetchProfilePictureOnlyWifi) }
    Column {
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            item {
                TextField(label = {
                    Text(context.getString(R.string.private_key))
                }, value = privateKey, onValueChange = {
                    privateKey = it.replace("\n", "")
                }, maxLines = 1, placeholder = {
                    Text(context.getString(R.string.nsec))
                }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
            }
            if (privateKey.isEmpty()) {
                item {
                    Button(onClick = {
                        privateKey = NIP19.encode("nsec", Keys.generatePrivateKey())
                    }, content = {
                        Text(context.getString(R.string.generate_private_key), textAlign = TextAlign.Center)
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
            items(count = relayList.size) { index ->
                Column {
                    Row {
                        TextField(label = {
                            Text(context.getString(R.string.relay))
                        }, value = relayList[index].url, onValueChange = {
                            relayList[index] = relayList[index].copy(url = it.replace("\n", ""))
                        }, placeholder = {
                            Text(context.getString(R.string.relay_url))
                        }, modifier = Modifier.weight(1f), maxLines = 2,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                        Button(onClick = {
                            relayList.removeAt(index)
                        }, content = {
                            Image(painterResource(id = R.drawable.close), contentDescription = "Cancel", modifier = Modifier
                                .width(Const.ACTION_ICON_SIZE)
                                .height(Const.ACTION_ICON_SIZE))
                        }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.background))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var read by remember { mutableStateOf(relayList[index].read) }
                        var write by remember { mutableStateOf(relayList[index].write) }
                        Checkbox(checked = read, onCheckedChange = {
                            // なぜか知らんがrelayList[index]  = relayList[index].copy(read = it)だと更新できない。
                            read    = it
                            relayList[index].read = it
                        })
                        Text(context.getString(R.string.read))
                        Checkbox(checked = write, onCheckedChange = {
                            write   = it
                            relayList[index].write = it
                        })
                        Text(context.getString(R.string.write))
                    }
                }
            }
            item {
                Button(onClick = {
                    relayList.add(ConfigRelayData(""))
                }, content = {
                    Text(context.getString(R.string.add_relay_server))
                }, modifier = Modifier.fillMaxWidth())
            }
            item {
                TextField(label = {
                    Text(context.getString(R.string.fetch_size_description))
                }, value = fetchSize, onValueChange = {
                    fetchSize = it
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = displayProfilePicture, onCheckedChange = {
                        displayProfilePicture = it
                    })
                    Text(context.getString(R.string.display_profile_picture))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fetchProfilePictureOnlyWifi, onCheckedChange = {
                        fetchProfilePictureOnlyWifi = it
                    })
                    Text(context.getString(R.string.fetch_profile_picture_in_only_wifi))
                }
            }
        }
        Button(onClick = {
            var valid = true
            if (privateKey.isNotEmpty()) {
                val (hrp, priv) = NIP19.decode(privateKey)
                Log.d("SettingView", priv.size.toString())
                if (hrp.isEmpty() || priv.size != 32) {
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.error_invalid_secret_key), Toast.LENGTH_SHORT).show()
                    }
                    valid = false
                }
            }
            val list    = relayList.filter { it.url.isNotEmpty() && (it.url.startsWith("ws://") || it.url.startsWith("wss://")) }
            if (list.isEmpty()) {
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.error_no_valid_URL), Toast.LENGTH_SHORT).show()
                }
                valid = false
            }
            if (fetchSize.toLongOrNull() == null) {
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.error_invalid_string_in_fetch_size), Toast.LENGTH_SHORT)
                        .show()
                }
                valid = false
            } else {
                val size = fetchSize.toLong()
                if (size < 10 || size > 100) {
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.error_invalid_range_of_fetch_size), Toast.LENGTH_SHORT)
                            .show()
                    }
                    valid = false
                }
            }

            if (valid) {
                val (_, priv) = NIP19.decode(privateKey)
                Config.config.privateKey = Hex.encode(priv)
                Config.config.relayList = list
                Config.config.fetchSize = fetchSize.toLong()
                Config.config.displayProfilePicture = displayProfilePicture
                Config.config.fetchProfilePictureOnlyWifi = fetchProfilePictureOnlyWifi
                onUpdated()
            }
        }, content = {
            Text("Save")
        })
    }
}