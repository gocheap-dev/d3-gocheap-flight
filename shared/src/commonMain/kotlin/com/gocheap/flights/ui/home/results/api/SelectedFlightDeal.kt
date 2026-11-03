package com.gocheap.flights.ui.home.results.api

import com.gocheap.flights.data.model.flight.api.FlightDeal

/**
 * The deal the results screen is handing to the checkout page.
 *
 * Held here rather than passed as a navigation argument, for the same reason
 * [com.gocheap.flights.ui.home.results.FlightResultsViewModel] reads its search back out of the
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

    /** What the checkout page should load, or null if it was opened without anything picked. */
    var url: String? = null
        private set

    /** Blank when the destination is unnamed; the checkout screen supplies a word for it. */
    var title: String = ""
        private set

    fun set(deal: FlightDeal) {
        url = deal.deeplink?.takeIf(String::isNotBlank)
        title = deal.name?.takeIf(String::isNotBlank).orEmpty()
    }

    /**
     * An inline ad being opened, which goes to the same page for the same reason: it is a
     * supplier's own URL loaded in a web view, and whether the user got there from a deal or from
     * an ad changes nothing about how it is shown.
     *
     * Takes a resolved URL rather than the ad, because resolving one is `AdsRepository`'s job —
     * an ad's deeplink arrives relative to the supplier's site for the user's country.
     */
    fun set(url: String, title: String) {
        this.url = url.takeIf(String::isNotBlank)
        this.title = title
    }
}
