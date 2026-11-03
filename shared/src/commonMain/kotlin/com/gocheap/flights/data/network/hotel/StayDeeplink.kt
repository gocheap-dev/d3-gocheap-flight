package com.gocheap.flights.data.network.hotel

import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.data.model.HotelSearch
import com.gocheap.flights.data.model.hotel.HotelDestination
import com.gocheap.flights.data.user.platformAnonymousUserId
import com.gocheap.flights.data.user.platformAppCode
import com.gocheap.flights.utils.BaseUrls
import com.gocheap.flights.utils.Constants
import com.gocheap.flights.utils.logDebug
import io.ktor.http.encodeURLParameter

/**
 * Builds the URL the results page loads.
 *
 * The stay supplier does not expose a results API — it exposes a web page, and the whole search is
 * encoded in the query string. So this is not a convenience wrapper over something else: it *is*
 * the search, and getting a parameter name wrong returns a results page for the wrong place rather
 * than an error.
 *
 * Ported from the reference app's `generateUri`, parameter for parameter.
 */
internal object StayDeeplink {

    /**
     * Its own tag rather than the sign-up's: this is the supplier handoff, and the two are read at
     * different times for different reasons. Both end in "Deeplink", so one `grep` still catches
     * the stay and rental builders together.
     */
    private const val LogTag = "StayDeeplink"

    /** Sent in place of a real account id. The reference uses the same string when none is known. */

    fun forSearch(
        search: HotelSearch,
        country: String = Constants.Defaults.Country,
        currency: String = Constants.Defaults.Currency,
        language: String = Constants.Defaults.Language,
        route: String = Constants.Defaults.Route,
        /**
         * The fallback by default, not the registered id: this object is pure string work and reads
         * no storage, which is what keeps it testable. The screens that build a real link pass
         * `UserSession.deeplinkUserId`, which is the stored id once `users/add` has produced one.
         */
        userId: String = platformAnonymousUserId,
    ): String {
        val destination = search.destination
        val checkIn = CalendarDate.parse(search.checkInDate)
        // A stay is at least one night, so a missing or same-day check-out becomes the next day
        // rather than a zero-night range the supplier would reject.
        val checkOut = CalendarDate.parse(search.checkOutDate)
            ?.takeIf { checkIn == null || it > checkIn }
            ?: checkIn?.plusDays(1)

        val parameters = buildList {
            add("country" to country.uppercase())
            add("currency" to currency)
            add("language" to language)
            add("user_id" to userId)
            add("app_code" to platformAppCode)
            add("route" to route)

            // City/country go out as names as well as ids: the page uses them for its own heading,
            // and they are all there is when the pick came from the popular-locations row.
            add("city_name" to (destination?.cityName ?: search.city))
            addAll(destination.idParameters())
            add("country_name" to destination?.countryName.orEmpty())

            checkIn?.let { add("checkin" to it.isoDate) }
            checkOut?.let { add("checkout" to it.isoDate) }
            add("rooms" to search.roomCount.toString())
            // Adults and children go out separately: the supplier prices a child off their age, so
            // counting them as adults would quote the wrong stay rather than merely a vaguer one.
            add("adults" to search.adults.toString())
            // Flattened across the rooms and comma-joined, as the reference sends them. Left off
            // entirely when nobody is bringing a child, which is also what the reference does.
            val childAges = search.childAges
            if (childAges.isNotEmpty()) {
                add("children_ages" to childAges.joinToString(","))
            }
        }

        val query = parameters.joinToString("&") { (name, value) ->
            // spaceToPlus = false to match the reference byte for byte: its Uri.Builder writes
            // "%20", and a supplier that parses strictly would read a "+" as a literal plus.
            "$name=${value.encodeURLParameter(spaceToPlus = false)}"
        }
        val url = "${BaseUrls.Hotel.trimEnd('/')}/${Constants.Hotel.DeeplinkPath}?$query"
        // The whole handoff in one line. Everything above is a decision about a single parameter,
        // and which of them went wrong is only answerable against the URL they add up to — which
        // the app never shows, because this goes straight into a web view.
        logDebug(LogTag, "Final URL: $url")
        return url
    }

    /**
     * The supplier keys its lookup on exactly one of these, and which one depends on what the
     * autocomplete row was. Sending the id under the wrong name resolves to the wrong place rather
     * than failing, so this mirrors the reference's branch exactly.
     */
    private fun HotelDestination?.idParameters(): List<Pair<String, String>> {
        if (this == null) return emptyList()
        return buildList {
            val idParameter = if (idType == "place_id") "place_id" else "entity_id"
            supplierId?.let { add(idParameter to it) }
            hotelId?.let { add("hotel_id" to it) }
            cityId?.let { add("city_id" to it) }
        }
    }
}