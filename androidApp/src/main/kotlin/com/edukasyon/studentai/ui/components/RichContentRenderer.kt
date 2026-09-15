package com.edukasyon.studentai.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders HTML content with KaTeX (math), Mermaid (diagrams), and
 * Chart.js (graphs) in a WebView sized to its measured content height.
 *
 * The page's viewport meta is width=device-width, so 1 CSS px maps to
 * 1 dp and scrollHeight can be applied directly as a Compose height.
 * AndroidView(Wrap-content WebView) does not self-size inside a scrolling
 * Column — hence the explicit bridge-measured height.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichContentRenderer(
    html: String,
    modifier: Modifier = Modifier,
) {
    var webHeight by remember { mutableStateOf<Dp>(0.dp) }
    // Tracks which html the WebView actually holds; guards reload-on-recompose.
    val loadedHtml = remember { mutableStateOf<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    AndroidView(
        modifier = modifier.let { if (webHeight > 0.dp) it.height(webHeight) else it },
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                // System font-scale/textZoom breaks the CSS-px≈dp assumption
                // used to map scrollHeight to Modifier.height — pin it.
                settings.textZoom = 100
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                // @JavascriptInterface callbacks run on the WebView's JS
                // thread — hop to main before touching Compose state.
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onHeightChanged(h: Int) {
                            if (h > 0) mainHandler.post { webHeight = h.dp }
                        }
                    },
                    "Bridge",
                )

                // Backstop for the bridge: measure once page load finishes
                // (MutationObserver fires late for KaTeX/Mermaid reflow).
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        mainHandler.post {
                            view.evaluateJavascript(
                                "(function(){return document.body.scrollHeight;})()",
                            ) { value ->
                                val px = value?.trim()?.removeSurrounding("\"")?.toIntOrNull()
                                if (px != null && px > 0) webHeight = px.dp
                            }
                        }
                    }
                }
                webChromeClient = WebChromeClient()

                loadDataWithBaseURL("https://unused", html, "text/html", "UTF-8", null)
                loadedHtml.value = html
            }
        },
        update = { webView ->
            // Reload ONLY on real content change. Recompositions happen when
            // the height bridge posts; reloading there would restart the page
            // (and Mermaid/Chart animations) in an infinite loop.
            if (loadedHtml.value != html) {
                loadedHtml.value = html
                webView.loadDataWithBaseURL(
                    "https://unused",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}
