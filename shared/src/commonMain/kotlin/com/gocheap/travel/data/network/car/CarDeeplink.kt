package com.gocheap.travel.data.network.car

import com.gocheap.travel.data.model.CalendarDate
import com.gocheap.travel.data.model.CarSearch
import com.gocheap.travel.data.model.car.CarLocation
import com.gocheap.travel.data.model.car.CarLocationType
import com.gocheap.travel.data.user.platformAnonymousUserId
import com.gocheap.travel.data.user.platformAppCode
import com.gocheap.travel.utils.BaseUrls
import com.gocheap.travel.utils.Constants
import com.gocheap.travel.utils.logDebug
import io.ktor.http.encodeURLParameter

/**
 * Builds the URL the rental results page loads.
 *
 * Same situation as [com.gocheap.travel.data.network.hotel.StayDeeplink]: the supplier exposes a
 * web page rather than a results API, so the whole search lives in the query string and a wrong
 * parameter name returns the wrong cars rather than an error.
 *
 * Ported from the reference app's car `generateUri`, parameter for parameter — which is *not* the
 * stay page's parameter set, despite the identical path. The app code goes out as `app` here and as
 * `app_code` there, the two ends are named `pick_up`/`drop_off` rather than by id type, and the
 * dates carry a desk time where a stay carries only a day.
 */
internal object CarDeeplink {

    /**
     * Its own tag rather than the sign-up's: this is the supplier handoff, and the two are read at
     * different times for different reasons. Both end in "Deeplink", so one `grep` still catches
     * the stay and rental builders together.
     */
    private const val LogTag = "CarDeeplink"

    fun forSearch(
        search: CarSearch,
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
        val pickup = search.pickupLocation
        // "Same location" is the absence of a drop-off, so the whole drop-off half of the query is
        // left off rather than sent back as a copy of the pick-up — which is what the reference's
        // hasDifferentDropOff branch amounts to.
        val dropoff = search.dropoffLocation.takeUnless { search.isSameDropoff }

        val pickupDate = CalendarDate.parse(search.pickupDate)
        // A rental runs at least a day, so a missing or same-day return becomes the next day rather
        // than a zero-day range the supplier would reject.
        val dropoffDate = CalendarDate.parse(search.dropoffDate)
            ?.takeIf { pickupDate == null || it > pickupDate }
            ?: pickupDate?.plusDays(1)

        val parameters = buildList {
            add("user_id" to userId)
            add("app" to platformAppCode)
            add("route" to route)
            add("country" to country.uppercase())
            add("currency" to currency)
            add("language" to language)

            // The city name is all there is when the pick came from the popular-locations row; the
            // page resolves it the same way it resolves anything typed into its own search box.
            add("pick_up" to (pickup.deeplinkValue() ?: search.city))
            pickup?.cityId?.let { add("pick_up_city_id" to it) }
            pickupDate?.let { add("pick_up_date" to it.atTime(search.pickupTime)) }
            pickup.deeplinkType()?.let { add("pick_up_type" to it) }

            // Named, not merely toggled: the card can be switched to a one-way return without a
            // drop-off ever being picked, and an empty `drop_off` resolves to nowhere — worse than
            // the same-location search the user would otherwise get.
            val dropoffName = dropoff.deeplinkValue() ?: search.dropoffCity?.takeIf(String::isNotBlank)
            if (dropoffName != null) {
                add("drop_off" to dropoffName)
                dropoff?.cityId?.let { add("drop_off_city_id" to it) }
                dropoff.deeplinkType()?.let { add("drop_off_type" to it) }
            }
            dropoffDate?.let { add("drop_off_date" to it.atTime(search.dropoffTime)) }
        }

        val query = parameters.joinToString("&") { (name, value) ->
            // spaceToPlus = false to match the reference byte for byte: its Uri.Builder writes
            // "%20", and a supplier that parses strictly would read a "+" as a literal plus.
            "$name=${value.encodeURLParameter(spaceToPlus = false)}"
        }
        val url = "${BaseUrls.Car.trimEnd('/')}/${Constants.Car.DeeplinkPath}?$query"
        // The whole handoff in one line. Everything above is a decision about a single parameter,
        // and which of them went wrong is only answerable against the URL they add up to — which
        // the app never shows, because this goes straight into a web view.
        logDebug(LogTag, "Final URL: $url")
        return url
    }

    /**
     * Which of the row's three names the page keys its lookup on, mirroring the reference's branch
     * exactly: an airport is its code, an entity row is its raw id, and everything else is the slug.
     *
     * Null when the row carries none of them — the caller then falls back to the plain city name
     * rather than sending an empty parameter the page would resolve to nowhere.
     */
    private fun CarLocation?.deeplinkValue(): String? {
        if (this == null) return null
        val value = when {
            type == CarLocationType.Airport -> iata
            idType == "entity_id" -> supplierId
            else -> deeplinkName
        }
        return value?.takeIf(String::isNotBlank)
    }

    /** The supplier's own word for the row, which is what it expects echoed back. */
    private fun CarLocation?.deeplinkType(): String? =
        this?.apiType?.takeIf(String::isNotBlank) ?: this?.type?.name?.lowercase()

    /**
     * "2022-12-19T10:00" — a rental is booked against the desk time as much as the day, so unlike a
     * stay's checkin/checkout these carry one. An unrecognised time falls back to midnight rather
     * than dropping the date with it, which is the reference's behaviour too.
     */
    private fun CalendarDate.atTime(time: String): String {
        val resolved = time.takeIf(TimeOfDay::matches) ?: "00:00"
        return "${isoDate}T$resolved"
    }

    /** 24-hour "HH:mm", the one form the search card ever stores — see `TimeSlots`. */
    private val TimeOfDay = Regex("""\d{2}:\d{2}""")
}
