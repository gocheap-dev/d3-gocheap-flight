package com.gocheap.travel.sdk

import android.content.Context
import com.gocheap.travel.data.model.TripSearch

/**
 * Where the Android app plugs its flight-results handler in.
 *
 * The handler itself lives in the app module rather than here because that is where the
 * Travelpayouts SDK is: this module holds every screen in the app and has no business depending on
 * an Android-only .aar to draw them. Inverting it this way also keeps the SDK out of the iOS build
 * by construction rather than by `expect`/`actual` discipline alone.
 *
 * Null until the app registers one — which it does from `Application.onCreate`, after the SDK has
 * initialised — and null forever in previews and tests, where the caller's fallback is what runs.
 */
object FlightResultsHandoff {

    fun interface Handler {
        /** False means "not handled": the caller should fall back to the app's own results page. */
        fun show(context: Context, search: TripSearch): Boolean
    }

    @Volatile
    var handler: Handler? = null
}
