package com.gocheap.flights.ui.component

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun ResultsWebView(
    url: String,
    onProgress: (Float) -> Unit,
    modifier: Modifier,
) {
    // Remembered rather than captured: the web view's clients are built once, in `factory`, so a
    // lambda captured there would be the first composition's forever.
    val reporter = remember { ProgressReporter() }
    reporter.onProgress = onProgress

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    // The results page is a web app: it renders nothing without these two.
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // Without this it loads the desktop layout into a phone-width viewport.
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        reporter.loading(newProgress.coerceIn(0, 100) / 100f)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        reporter.start()
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        // The first frame of the page has been painted: the results are on screen,
                        // so the loader has nothing left to cover and the rest of the load —
                        // images, trackers — can finish behind a page the user is already reading.
                        reporter.settle()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Backstop: a page served from cache can finish without ever reporting a
                        // commit, and the loader has to come off either way.
                        reporter.settle()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?,
                    ) {
                        // Release the loader so the supplier's error page is visible, rather than
                        // leaving a spinner over a page that will never progress.
                        reporter.settle()
                    }
                }
            }
        },
        update = { webView ->
            // Guarded: AndroidView runs update on every recomposition, and reloading the page on
            // each progress tick would never let it finish.
            if (webView.url != url && webView.originalUrl != url) {
                webView.loadUrl(url)
            }
        },
        onRelease = WebView::destroy,
    )
}

/**
 * Keeps the reported progress moving in one direction, and stops reporting altogether once the
 * page has been shown.
 *
 * Everything after that point is dropped — the part-done percentages WebView keeps reporting for
 * subresources still arriving, and the fresh start of whatever the page navigates to next. Either
 * would drag the caller back under 1f and pull the loader back over results that are already on
 * screen; a page navigating within itself is the supplier's to narrate, not this loader's.
 */
private class ProgressReporter {
    var onProgress: (Float) -> Unit = {}
    private var isSettled = false

    fun start() {
        if (isSettled) return
        onProgress(0f)
    }

    fun loading(fraction: Float) {
        if (isSettled) return
        if (fraction >= 1f) settle() else onProgress(fraction)
    }

    fun settle() {
        isSettled = true
        onProgress(1f)
    }
}
