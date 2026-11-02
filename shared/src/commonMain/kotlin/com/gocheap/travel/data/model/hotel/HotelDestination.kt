package com.gocheap.travel.data.model.hotel

import kotlinx.serialization.Serializable

/**
 * Somewhere a stay search can be pointed at, as the supplier's autocomplete returns it.
 *
 * Unlike [com.gocheap.travel.data.model.LocationSuggestion], which is a flat list of cities, the stay endpoint answers with a mix
 * of granularities — a city, an airport, or one specific property — and the narrower ones hang off
 * the wider one through [parentId] so the picker can indent them.
 */
@Serializable
data class HotelDestination(
    /** Unique within a response and stable enough to key a list row on. */
    val id: String,
    /** The headline, e.g. "Union Square Suites". Also what fills the search card once picked. */
    val name: String,
    /** The line underneath, e.g. "San Francisco, United States". May be blank. */
    val location: String,
    val type: HotelDestinationType,
    /**
     * Which row this sits under — a property under its city. Matched against [lookupKeys] rather
     * than [id], since the supplier points children at whichever of the parent's ids it indexed on.
     * A child whose parent is absent from the response is shown unindented rather than hidden.
     */
    val parentId: String? = null,
    /**
     * The supplier's own id for this row, kept raw and separate from [id] because the results
     * deeplink has to send it back under the right parameter name — see [idType].
     */
    val supplierId: String? = null,
    val hotelId: String? = null,
    val cityId: String? = null,
    /**
     * What [supplierId] actually is, e.g. "place_id". The deeplink sends it as `place_id` when it
     * says so and as `entity_id` otherwise, which the supplier treats as two different lookups.
     */
    val idType: String? = null,
    /**
     * A photograph of the place, which the supplier sends on its own suggestion list and leaves off
     * autocomplete rows. Null for anything typed into the destination sheet.
     */
    val imageUrl: String? = null,
) {
    /**
     * Every id the supplier may reference this row by. Derived rather than stored: these are the
     * three fields above, and keeping a second copy only creates a way for them to disagree.
     */
    val lookupKeys: List<String>
        get() = listOfNotNull(supplierId, hotelId, cityId)

    /** "San Francisco" out of "San Francisco, California, United States". Blank when unknown. */
    val cityName: String
        get() = location.substringBefore(",").trim()

    /** "United States" out of the same. The supplier always puts the country last. */
    val countryName: String
        get() = location.substringAfterLast(",", missingDelimiterValue = "").trim()
}

/** The `type` field of an autocomplete row, narrowed to the four the picker draws differently. */
enum class HotelDestinationType {
    City,
    Airport,
    Hotel,
    Other,
    ;

    companion object {
        /** Anything unrecognised is still a valid place to search, so it falls back to [Other]. */
        fun fromApi(raw: String?): HotelDestinationType = when {
            raw.equals("hotel", ignoreCase = true) -> Hotel
            raw.equals("airport", ignoreCase = true) -> Airport
            raw.equals("city", ignoreCase = true) -> City
            else -> Other
        }
    }
}
