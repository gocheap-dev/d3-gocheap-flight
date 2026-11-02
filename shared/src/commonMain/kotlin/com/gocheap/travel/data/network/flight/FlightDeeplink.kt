package com.gocheap.travel.data.network.flight

import com.gocheap.travel.data.model.CabinClass
import com.gocheap.travel.data.model.CalendarDate
import com.gocheap.travel.data.model.TripSearch
import com.gocheap.travel.data.user.platformAnonymousUserId
import com.gocheap.travel.data.user.platformAppCode
import com.gocheap.travel.utils.BaseUrls
import com.gocheap.travel.utils.Constants
import com.gocheap.travel.utils.logDebug
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Builds the URL the flight results page loads, for the `KYK_DL` half of the controller's choice.
 *
 * The third of this app's three supplier handoffs, and the odd one out: stays and rentals name
 * their search in flat query parameters, while this one carries its legs as a JSON array in a
 * single `legs` parameter. That is the endpoint's shape, not a choice made here — the reference
 * app's `generateFlightUrl` sends exactly this.
 *
 * Pure string work with no storage reads, like [com.gocheap.travel.data.network.hotel.StayDeeplink]
 * and for the same reason: it is the part worth testing, and the screen that builds a real link
 * passes the stored user id in.
 */
internal object FlightDeeplink {

    /** Its own tag, alongside the other two handoffs — one `grep` for "Deeplink" catches all three. */
    private const val LogTag = "FlightDeeplink"

    /**
     * One leg of the trip. A one-way carries a single entry; a round trip carries the return as a
     * second leg rather than as a separate date field, which is how the endpoint reads it.
     */
    @Serializable
    private data class Leg(
        @SerialName("origin") val origin: String,
        @SerialName("destination") val destination: String,
        @SerialName("date") val date: String,
    )

    fun forSearch(
        search: TripSearch,
        country: String = Constants.Defaults.Country,
        currency: String = Constants.Defaults.Currency,
        language: String = Constants.Defaults.Language,
        route: String = Constants.Defaults.Route,
        /**
         * The fallback by default, exactly as in the other two builders. The screen that opens a
         * real link passes `UserSession.deeplinkUserId`.
         */
        userId: String = platformAnonymousUserId,
    ): String {
        val departure = CalendarDate.parse(search.departureDate)?.isoDate.orEmpty()
        // A return leg only exists on a round trip, and only once its date is after the outbound —
        // the same guard the stay and rental builders put on their second date.
        val returning = search.returnDate
            ?.takeIf { search.isRoundTrip }
            ?.let { CalendarDate.parse(it) }
            ?.takeIf { back -> CalendarDate.parse(search.departureDate)?.let { back > it } ?: true }
            ?.isoDate

        val legs = buildList {
            add(Leg(search.from.code, search.to.code, departure))
            returning?.let { add(Leg(search.to.code, search.from.code, it)) }
        }

        val parameters = buildList {
            add("country" to country.uppercase())
            add("legs" to Json.encodeToString(ListSerializer(Leg.serializer()), legs))
            add("cabin_class" to search.cabinClass.deeplinkValue())
            add("adults" to search.adults.toString())
            // Left off entirely when nobody is bringing a child, which is what the reference does.
            val childAges = search.childAges()
            if (childAges.isNotEmpty()) {
                add("children_ages" to childAges.joinToString(","))
            }
            // Lowercase currency against an uppercase country: the endpoint is specified that way
            // and the reference sends it that way.
            add("currency" to currency.lowercase())
            add("language" to language)
            add("user_id" to userId)
            add("app_code" to platformAppCode)
            add("route" to route)
        }

        val query = parameters.joinToString("&") { (name, value) ->
            // spaceToPlus = false to match the other two builders, and here it matters more: the
            // legs array is JSON, so its braces and quotes have to survive the trip intact.
            "$name=${value.encodeURLParameter(spaceToPlus = false)}"
        }
        val url = "${BaseUrls.Flight.trimEnd('/')}/${Constants.Flight.DeeplinkPath}?$query"
        // The whole handoff in one line, as the stay and rental builders log theirs.
        logDebug(LogTag, "Final URL: $url")
        return url
    }

    /**
     * Ages rather than counts, flattened and comma-joined.
     *
     * The reference sends a placeholder age per traveller — 9 for a child, 1 for an infant —
     * because the card collects counts and the endpoint prices on ages. Same numbers here, so a
     * search produces the same quote it would there.
     */
    private fun TripSearch.childAges(): List<Int> =
        List(children) { ChildAge } + List(infants) { InfantAge }

    /** Lowercase and unseparated — "premiumeconomy", not the session body's "premiumEconomy". */
    private fun CabinClass.deeplinkValue(): String = when (this) {
        CabinClass.PremiumEconomy -> "premiumeconomy"
        CabinClass.Business -> "business"
        CabinClass.First -> "first"
        CabinClass.Economy -> "economy"
    }

    private const val ChildAge = 9
    private const val InfantAge = 1
}
