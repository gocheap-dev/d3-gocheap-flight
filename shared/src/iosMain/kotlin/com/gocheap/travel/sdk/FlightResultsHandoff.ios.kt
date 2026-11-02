package com.gocheap.travel.sdk

/**
 * A finished flight search, normalised into what the Travelpayouts iOS SDK asks for.
 *
 * Handed over instead of [com.gocheap.travel.data.model.TripSearch] so the Swift side has nothing
 * left to work out: the card's dates are display labels ("15, oct 2026") and its cabin is this
 * app's own enum, and both are turned into the SDK's forms here, where the parsing already lives.
 */
data class SdkFlightSearch(
    val originIata: String,
    val destinationIata: String,
    /** ISO `yyyy-MM-dd`, which Swift turns into the `Date` the SDK's search model takes. */
    val departureDate: String,
    /** Null on a one-way trip, which the SDK reads as exactly that. */
    val returnDate: String?,
    val adults: Int,
    val children: Int,
    val infants: Int,
    /** The one-letter fare class the search runs on: Y, W, C or F. */
    val tripClass: String,
)

/**
 * Where the iOS app plugs its flight-results handler in — the counterpart of the Android
 * `FlightResultsHandoff`, and inverted for the same reason.
 *
 * The SDK is a CocoaPods dependency of the iOS app target, so this module cannot reference it: a
 * shared module that every screen lives in has no business depending on an iOS-only framework, and
 * the Kotlin/Native build has no way to see it in any case. So the app registers a handler at
 * launch, written in Swift, and this is the seam it registers into.
 *
 * Null until it does — which is also the state of a build without SDK credentials, and of previews
 * and tests. The caller then falls back to whatever else it has; see `AppNavHost`.
 */
object FlightResultsHandoff {

    /**
     * Written once from the Swift side at launch and read from the main thread on every search, so
     * no synchronisation beyond what the Kotlin/Native memory model already gives a shared object.
     */
    var handler: FlightResultsHandler? = null
}

/**
 * What the Swift side implements.
 *
 * Top-level rather than nested inside [FlightResultsHandoff] — unlike its Android counterpart —
 * because a nested one reaches Swift as `FlightResultsHandoffHandler`, and a name assembled by the
 * compiler is a poor thing for the one type another language has to spell exactly.
 */
fun interface FlightResultsHandler {
    /** False means "not handled": the caller should fall back rather than assume results. */
    fun show(search: SdkFlightSearch): Boolean
}
