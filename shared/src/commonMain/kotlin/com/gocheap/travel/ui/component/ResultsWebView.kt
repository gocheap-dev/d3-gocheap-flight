package com.gocheap.travel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A web view showing [url], because the stay supplier returns a results *page* rather than a
 * results API — see `StayDeeplink`.
 *
 * [onProgress] reports 0f..1f so the caller can hold a loader over the top until the page is worth
 * showing.
 *
 * It reaches 1f as soon as the page is on screen, which is sooner than the page is finished: the
 * trailing images and trackers load behind results the user can already read, rather than under a
 * loader. It reaches 1f when the load fails too — a results screen stuck behind a spinner forever
 * is worse than one showing the supplier's own error page — and once there it stays there until a
 * new page starts, so the loader never comes back over a page that is already showing.
 */
@Composable
internal expect fun ResultsWebView(
    url: String,
    onProgress: (Float) -> Unit,
    modifier: Modifier = Modifier,
)
