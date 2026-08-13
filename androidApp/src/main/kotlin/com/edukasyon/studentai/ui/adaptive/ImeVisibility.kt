package com.edukasyon.studentai.ui.adaptive

import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Reliable IME visibility across OEMs (including Huawei) by combining Compose inset height
 * and ViewCompat root insets from the view tree.
 */
@Composable
fun rememberImeVisible(): Boolean {
    val view = LocalView.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    var viewTreeImeVisible by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        fun readImeVisible(): Boolean {
            val insets = ViewCompat.getRootWindowInsets(view) ?: return false
            return insets.isVisible(WindowInsetsCompat.Type.ime()) ||
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
        }

        val listener = ViewTreeObserver.OnPreDrawListener {
            viewTreeImeVisible = readImeVisible()
            true
        }
        viewTreeImeVisible = readImeVisible()
        view.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(listener)
        }
    }

    return imeBottom > 0 || viewTreeImeVisible
}
