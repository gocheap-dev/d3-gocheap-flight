package com.gocheap.travel.data.model.car

import kotlinx.serialization.Serializable

/**
 * Somewhere a car can be collected or returned, as the supplier's autocomplete returns it.
 *
 * The same endpoint backs this and the stay picker, but a rental is only ever booked against a city
 * or an airport, so properties are filtered out upstream and what is left is a flat list — no
 * [com.gocheap.travel.data.model.hotel.HotelDestination.parentId] equivalent, because with the
 * hotels gone there is nothing left to nest under a city.
 */
@Serializable
data class CarLocation(
    /** Unique within a response and stable enough to key a list row on. */
    val id: String,
    /** The headline, e.g. "San Francisco International Airport". */
    val name: String,
    /** The line underneath, e.g. "San Francisco, United States". May be blank. */
    val location: String,
    val type: CarLocationType,
    /**
     * The supplier's own id for this row, kept raw and separate from [id] — which is salted to stay
     * unique in a list — because the results deeplink has to send it back unchanged.
     */
    val supplierId: String? = null,
    /** What [supplierId] actually is, e.g. "place_id". Decides whether the deeplink may send it. */
    val idType: String? = null,
    /** The town this sits in, by the supplier's id for it. Sent alongside the pick-up when known. */
    val cityId: String? = null,
    /** Airport code, e.g. "SFO". How the deeplink names an airport, in place of any id. */
    val iata: String? = null,
    /** The supplier's own slug, e.g. "San-Francisco,California,United-States". */
    val deeplinkName: String? = null,
    /**
     * The supplier's own word for [type], e.g. "airport". Kept beside the narrowed enum because the
     * deeplink echoes it back verbatim and the supplier knows words this app does not model.
     */
    val apiType: String? = null,
    /**
     * A photograph of the place, which the supplier sends on its own suggestion list and leaves off
     * autocomplete rows. Null for anything typed into the search sheet.
     */
    val imageUrl: String? = null,
) {
    /**
     * Which city this sits in. The search card tracks a city separately from the depot, and an
     * airport row has to fill both.
     *
     * The first segment either way, never the whole string: the supplier qualifies both fields down
     * to the country — a city row is named "San Francisco, California, United States" and carries no
     * [location] at all — and the card has room for the town, not the address of it.
     */
    val cityLabel: String
        get() = when (type) {
            CarLocationType.City -> name.substringBefore(",").trim().ifEmpty { name }
            else -> location.substringBefore(",").trim().ifEmpty { name }
        }

    /**
     * The country, out of the same string [cityLabel] takes the town from — the supplier always puts
     * it last. Blank when the row names only one place, which is all a suggestion card can say then.
     */
    val countryLabel: String
        get() {
            val qualified = if (type == CarLocationType.City) name else location
            return qualified.substringAfterLast(",", missingDelimiterValue = "").trim()
        }
}

/** The `type` field of an autocomplete row, narrowed to what a rental can be picked up from. */
enum class CarLocationType {
    City,
    Airport,

    /** A district or region — still somewhere a depot can sit, so it is kept and drawn as a place. */
    Other,
    ;

    companion object {
        /**
         * Null for a property: a rental is collected from a city depot or an airport desk, and the
         * caller drops the row rather than offering someone's lobby as a pick-up point.
         *
         * Anything unrecognised is still a place a depot can sit, so it falls back to [Other].
         */
        fun fromApi(raw: String?): CarLocationType? = when {
            raw.equals("hotel", ignoreCase = true) -> null
            raw.equals("airport", ignoreCase = true) -> Airport
            raw.equals("city", ignoreCase = true) -> City
            else -> Other
        }
    }
}
