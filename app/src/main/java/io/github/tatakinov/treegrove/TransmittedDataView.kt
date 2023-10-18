package io.github.tatakinov.treegrove

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

@Composable
fun TransmittedDataView(modifier : Modifier, onGetTransmittedDataSize : () -> Int) {
    Box(
        modifier = modifier
    ) {
        Column {
            Spacer(
                modifier = Modifier
                    .weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(Const.ICON_SIZE)) {
                lateinit var unit: String
                var s: Double = onGetTransmittedDataSize().toDouble()
                if (s < 1024) {
                    unit = "B"
                } else if (s < 1024 * 1024) {
                    unit = "kB"
                    s /= 1024
                } else if (s < 1024 * 1024 * 1024) {
                    unit = "MB"
                    s /= 1024 * 1024
                } else {
                    unit = "GB"
                    s /= 1024 * 1024 * 1024
                }
                val text: String = if (unit == "B") {
                    "%.0f%s".format(s, unit)
                } else {
                    "%.1f%s".format(s, unit)
                }
                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(3f))
            }
        }
    }
}