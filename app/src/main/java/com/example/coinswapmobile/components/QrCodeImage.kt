package com.example.coinswapmobile.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
    sizePx: Int = 512,
) {
    var bitmap by remember(data, sizePx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(data, sizePx) {
        bitmap = withContext(Dispatchers.Default) {
            runCatching {
                val matrix = MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx)
                val pixels = IntArray(sizePx * sizePx)
                var i = 0
                for (y in 0 until sizePx) {
                    for (x in 0 until sizePx) {
                        pixels[i++] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                    }
                }
                Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.RGB_565)
            }.getOrNull()
        }
    }

    bitmap?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = "QR code", modifier = modifier)
    }
}
