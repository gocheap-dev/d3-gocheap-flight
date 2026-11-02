@file:OptIn(ExperimentalForeignApi::class)

package com.gocheap.travel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.delay
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject

@Composable
internal actual fun ResultsWebView(
    url: String,
    onProgress: (Float) -> Unit,
    modifier: Modifier,
) {
    // Held across recompositions so the delegate is not replaced — and so it stays referenced while
    // the web view holds it weakly, which would otherwise silently stop the callbacks.
    val delegate = remember { ProgressNavigationDelegate() }
    delegate.onProgress = onProgress

    val webView = remember { WKWebView() }
    DisposableEffect(webView) {
        webView.navigationDelegate = delegate
        onDispose { webView.navigationDelegate = null }
    }

    LaunchedEffect(webView, url) {
        NSURL.URLWithString(url)?.let { webView.loadRequest(NSURLRequest(uRL = it)) }
        // WKWebView publishes fractional progress only as a KVO property, which Kotlin cannot
        // observe directly, so it is read on a tick instead. Without this the delegate below is the
        // only source, and it has nothing to say between "started" and "finished" — which left the
        // loader's bar sitting at zero for the whole load and then disappearing.
        while (!delegate.isSettled) {
            delegate.reportEstimated(webView.estimatedProgress)
            delay(POLL_MILLIS)
        }
    }

    UIKitView(
        factory = { webView },
        modifier = modifier,
    )
}

/** Often enough to read as movement, rare enough to be free. */
private const val POLL_MILLIS = 100L

/**
 * The page's own account of where it is. Estimated progress only ever creeps towards the end, so
 * finishing — or failing, which also has to release the loader — is reported from here.
 *
 * Every method needs [ObjCSignatureOverride]: these are four distinct Objective-C selectors that
 * collapse onto two Kotlin signatures, and Kotlin reads that as conflicting overloads without it.
 */
private class ProgressNavigationDelegate : NSObject(), WKNavigationDelegateProtocol {
    var onProgress: (Float) -> Unit = {}

    /**
     * True once the page is showing — or has failed, which also has to release the loader. Nothing
     * is reported after that: the polling loop stops, and so does the fresh start of whatever the
     * page navigates to next. Either would drag the caller back under 1f and bring the loader back
     * over a page that is already on screen, or over the supplier's own error page.
     */
    var isSettled = false
        private set

    /**
     * Past [PAGE_SHOWN_PROGRESS] the main document has arrived and the web view is drawing it, so
     * this settles there instead of waiting for the last of the page's images and trackers: the
     * results are on screen by then, and a loader held over a page the user could already be
     * reading only delays them.
     */
    fun reportEstimated(estimated: Double) {
        if (isSettled) return
        val fraction = estimated.toFloat().coerceIn(0f, 1f)
        if (fraction >= PAGE_SHOWN_PROGRESS) settle() else onProgress(fraction)
    }

    private fun settle() {
        isSettled = true
        onProgress(1f)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        if (isSettled) return
        onProgress(0f)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) = settle()

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError,
    ) = settle()

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) = settle()
}

/**
 * Where the page counts as shown. WKWebView weights its estimate towards the main document, so by
 * this point the page has been parsed and drawn and only trailing subresources are outstanding.
 */
private const val PAGE_SHOWN_PROGRESS = 0.9f
