package io.github.tatakinov.treegrove.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import io.github.tatakinov.treegrove.LoadingData
import io.github.tatakinov.treegrove.R
import io.github.tatakinov.treegrove.TreeGroveViewModel

@Composable
fun ImageViewer(viewModel: TreeGroveViewModel, url: String) {
    val data by viewModel.fetchImage(url).collectAsState()
    when (val d = data) {
        is LoadingData.Valid -> {
            val bitmap = BitmapFactory.decodeByteArray(d.data, 0, d.data.size)
            if (bitmap != null) {
                val image = bitmap.asImageBitmap()
                Image(bitmap = image, contentDescription = "image")
            }
            else {
                Text(stringResource(id = R.string.error_invalid_image), modifier = Modifier.fillMaxSize())
            }
        }
        is LoadingData.Invalid -> {
            Text(stringResource(id = R.string.error_invalid_data, d.reason))
        }
        is LoadingData.Loading -> {
            Text(stringResource(id = R.string.loading))
        }
        is LoadingData.NotLoading -> {
            Text(stringResource(id = R.string.not_loading))
        }
    }
}
