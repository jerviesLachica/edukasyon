package com.edukasyon.studentai.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [text] as a QR code entirely on-device (zxing bit matrix → Canvas).
 * Pure: no network, no Android Views. The [size] square is filled with a white
 * quiet zone plus black modules. Encoding is memoised on the text so
 * recomposition doesn't re-run zxing.
 */
@Composable
fun QrCodeImage(
    text: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(text) { qrBitMatrix(text) }
    Canvas(
        modifier = modifier
            .background(Color.White)
            .size(size),
    ) {
        if (matrix == null) return@Canvas
        val modules = matrix.size
        val moduleSizePx = this.size.minDimension / modules
        for (y in 0 until modules) {
            val row = matrix[y]
            for (x in 0 until row.size) {
                if (row[x]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * moduleSizePx, y * moduleSizePx),
                        size = Size(moduleSizePx, moduleSizePx),
                    )
                }
            }
        }
    }
}

/** ZXing encode with a 1-module quiet zone; returns a square true=dark grid, or null on failure. */
private fun qrBitMatrix(text: String): Array<BooleanArray>? = runCatching {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val width = bitMatrix.width
    val height = bitMatrix.height
    Array(height) { y -> BooleanArray(width) { x -> bitMatrix.get(x, y) } }
}.getOrNull()
