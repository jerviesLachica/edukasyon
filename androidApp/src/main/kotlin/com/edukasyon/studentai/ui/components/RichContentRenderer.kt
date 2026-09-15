package com.edukasyon.studentai.ui.components

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders HTML content with KaTeX (math), Mermaid (diagrams), and
 * Chart.js (graphs) in a WebView that auto-sizes to content height.
 */
@Composable
fun RichContentRenderer(
    html: String,
    modifier: Modifier = Modifier,
) {
    val height = remember { mutableStateOf(1) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setBackgroundColor(Color.TRANSPARENT)
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()

                addJavascriptInterface(
                    object {
                        @android.webkit.JavascriptInterface
                        fun onHeightChanged(h: Int) {
                            if (h > 0 && h != height.value) {
                                height.value = h
                            }
                        }
                    },
                    "Bridge"
                )

                loadDataWithBaseURL(
                    "https://unused",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://unused",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        },
    )
}
