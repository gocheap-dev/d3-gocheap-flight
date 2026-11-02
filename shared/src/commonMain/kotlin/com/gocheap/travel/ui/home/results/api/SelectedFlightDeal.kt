package com.gocheap.travel.ui.home.results.api

import com.gocheap.travel.data.model.flight.api.FlightDeal

/**
 * The deal the results screen is handing to the checkout page.
 *
 * Held here rather than passed as a navigation argument, for the same reason
 * [com.gocheap.travel.ui.home.results.FlightResultsViewModel] reads its search back out of the
 * recents store: a deeplink is a URL, and this app's route arguments are escaped with a
 * substitution that rewrites `_` to `/` — see `AppRoute.decodeRouteArg`. Any booking link
 * containing an underscore would arrive at the checkout page corrupted, and the ones that do are
 * not rare.
 *
 * In memory only and overwritten by each selection. It is read once, by the screen opened
 * immediately after it is written, and means nothing after that — there is nothing here worth
 * surviving the process, and a stale deeplink restored into a new launch would open a checkout
 * for a fare that has since expired.
 */
internal object SelectedFlightDeal {

    private var current: FlightDeal? = null

    fun set(deal: FlightDeal) {
        current = deal
    }

    /** What the checkout page should load, or null if it was opened without a deal having been picked. */
    val url: String? get() = current?.deeplink?.takeIf(String::isNotBlank)

    /** The seller's name for the page's title, falling back to a neutral word for the screen. */
    /** Blank when the seller is unnamed; the checkout screen supplies a word for it. */
    val title: String get() = current?.name?.takeIf(String::isNotBlank).orEmpty()
}
