package io.github.tatakinov.treegrove.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel
import io.github.tatakinov.treegrove.nostr.Filter


@Composable
fun LoadMoreEventsButton(viewModel: TreeGroveViewModel, filter: Filter) {
    val coroutineScope = rememberCoroutineScope()
    TextButton(onClick = {
        viewModel.fetchStreamPastPost(filter, -1)
    }) {
        Text(stringResource(id = R.string.load_more), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
