package com.gocheap.flights.ui.profile

import androidx.compose.runtime.Composable

/**
 * The three things the Profile screen does outside the app: hand text to a share sheet, open a
 * link, and say what build this is.
 *
 * Taken through composition rather than as top-level functions because the Android half needs the
 * Activity it was invoked from — a share sheet is raised by a Context, not by a process.
 */
interface ProfileActions {
    /** Raises the platform's share sheet on [text], which carries the store link with it. */
    fun share(text: String)

    /**
     * Opens [url] outside the app — the store page, a mail composer, the website.
     *
     * Deliberately not an in-app web view: every one of these ends somewhere the app has no
     * business hosting, and the store link only resolves to the store app at all if the system
     * handles it.
     */
    fun openUrl(url: String)

    /** The marketing version, as the About page prints it. "—" when the platform withholds it. */
    val appVersion: String

    /**
     * Where "Rate this app" goes, and what a share carries: each platform's own store page, which
     * only the platform can name.
     */
    val storeUrl: String
}

@Composable
expect fun rememberProfileActions(): ProfileActions
