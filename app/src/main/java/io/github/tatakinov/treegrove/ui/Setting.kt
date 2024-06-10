package io.github.tatakinov.treegrove.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import fr.acinq.secp256k1.Secp256k1
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.UserPreferences
import io.github.tatakinov.treegrove.UserPreferencesRepository
import io.github.tatakinov.treegrove.connection.RelayConfig
import io.github.tatakinov.treegrove.nostr.Keys
import io.github.tatakinov.treegrove.nostr.NIP19
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun Setting(privateKey: String, publicKey: String, relayConfigListState: State<List<RelayConfig>>, fetchSizeState: State<Long>, onNavigate: (UserPreferences) -> Unit) {
    val context = LocalContext.current
    val coroutineScope  = rememberCoroutineScope()
    val state   = rememberLazyListState()
    var inputPrivateKey by remember { mutableStateOf(UserPreferencesRepository.Default.privateKey) }
    val inputRelayList = remember {
        mutableStateListOf<RelayConfig>().apply {
            addAll(UserPreferencesRepository.Default.relayList)
        }
    }
    var inputFetchSize by remember {
        mutableStateOf(UserPreferencesRepository.Default.fetchSize.toString())
    }
    LaunchedEffect(privateKey) {
        inputPrivateKey = privateKey.ifEmpty { publicKey }
    }
    LaunchedEffect(publicKey) {
        inputPrivateKey = privateKey.ifEmpty { publicKey }
    }
    LaunchedEffect(relayConfigListState.value) {
        inputRelayList.clear()
        inputRelayList.addAll(relayConfigListState.value)
    }
    LaunchedEffect(fetchSizeState.value) {
        inputFetchSize = fetchSizeState.value.toString()
    }
    Column {
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            item {
                TextField(label = {
                    Text(stringResource(R.string.private_key))
                }, value = inputPrivateKey, onValueChange = {
                    inputPrivateKey = it.replace("\n", "")
                }, maxLines = 1, placeholder = {
                    Text(stringResource(R.string.nsec))
                }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
            }
            if (inputPrivateKey.isEmpty()) {
                item {
                    Button(onClick = {
                        inputPrivateKey = NIP19.encode(NIP19.NSEC, Keys.generatePrivateKey())
                    }, content = {
                        Text(stringResource(R.string.generate_private_key), textAlign = TextAlign.Center)
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                TextField(label = {
                    Text(stringResource(R.string.fetch_size_description))
                }, value = inputFetchSize, onValueChange = {
                    inputFetchSize = it
                }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
            }
            items(count = inputRelayList.size) { index ->
                Column {
                    Row {
                        TextField(label = {
                            Text(stringResource(R.string.relay))
                        }, value = inputRelayList[index].url, onValueChange = {
                            inputRelayList[index] = inputRelayList[index].copy(url = it.replace("\n", ""))
                        }, placeholder = {
                            Text(stringResource(R.string.relay_url))
                        }, modifier = Modifier.weight(1f), maxLines = 2,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )
                        Button(onClick = {
                            inputRelayList.removeAt(index)
                        }, content = {
                            Icon(Icons.Default.Remove, "delete")
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var read by remember { mutableStateOf(inputRelayList[index].read) }
                        var write by remember { mutableStateOf(inputRelayList[index].write) }
                        Checkbox(checked = read, onCheckedChange = {
                            // なぜか知らんがrelayList[index]  = relayList[index].copy(read = it)だと更新できない。
                            read    = it
                            inputRelayList[index] = inputRelayList[index].copy(read = read)
                        })
                        Text(stringResource(R.string.read))
                        Checkbox(checked = write, onCheckedChange = {
                            write   = it
                            inputRelayList[index] = inputRelayList[index].copy(write = write)
                        })
                        Text(stringResource(R.string.write))
                    }
                }
            }
            item {
                Button(onClick = {
                    inputRelayList.add(RelayConfig(url = "", read = true, write = true))
                }, content = {
                    Text(stringResource(R.string.add_relay_server))
                }, modifier = Modifier.fillMaxWidth())
            }
        }
        Button(onClick = {
            var valid = true
            var privKey = ""
            var pubKey = ""
            if (inputPrivateKey.isNotEmpty()) {
                if (inputPrivateKey.startsWith(NIP19.NSEC)) {
                    val (hrp, priv) = NIP19.decode(inputPrivateKey)
                    if (hrp.isEmpty() || priv.size != 32 || !Secp256k1.secKeyVerify(priv)) {
                        coroutineScope.launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.error_invalid_secret_key),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        valid = false
                    }
                    privKey = inputPrivateKey
                    pubKey = NIP19.encode(NIP19.NPUB, Keys.getPublicKey(priv))
                }
                else if (inputPrivateKey.startsWith(NIP19.NPUB)) {
                    val pub = NIP19.parse(inputPrivateKey)
                    if (pub !is NIP19.Data.Pub) {
                        valid = false
                    }
                    pubKey = inputPrivateKey
                }
                else {
                    valid = false
                }
            }
            val list    = inputRelayList.filter { it.url.isNotEmpty() && (it.url.startsWith("ws://") || it.url.startsWith("wss://")) }
            if (list.isEmpty()) {
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.error_no_valid_URL), Toast.LENGTH_SHORT).show()
                }
                valid = false
            }
            val size = inputFetchSize.toLongOrNull()
            if (size != null) {
                if (size < 10 || size > 200) {
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.error_invalid_range_of_fetch_size),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    valid = false
                }
            }
            else {
                valid = false
            }

            if (valid) {
                onNavigate(UserPreferences(privateKey = privKey, publicKey = pubKey,
                    relayList = list, fetchSize = inputFetchSize.toLong())
                )
            }
        }, content = {
            Text(stringResource(R.string.save))
        })
    }
}
