package com.gocheap.travel.data.model.flight

import kotlinx.serialization.Serializable

/**
 * Somewhere a flight search can be pointed at, as the supplier's autocomplete returns it.
 *
 * Keyed on [iata] rather than an opaque supplier id: a flight is searched by code at both ends, so
 * a row that cannot name one is no use and is dropped on the way in.
 *
 * A city row carries the code of the metropolitan area ("NYC"), an airport row its own ("JFK") and
 * the city's in [parentIata] — the same shape the stay and rental rows have, minus the nesting,
 * which this endpoint does not ask the picker to draw.
 */
@Serializable
data class FlightDestination(
    /** The code the search runs on, e.g. "SFO". Unique within a response, so it keys the list row. */
    val iata: String,
    /** The headline, e.g. "San Francisco International Airport". */
    val name: String,
    /**
     * The line underneath. Where the row is, on an airport — "San Francisco, California, United
     * States" — but on a city row the supplier puts "All airports" here instead, and the place is
     * in [name]. May be blank.
     */
    val location: String,
    val type: FlightDestinationType,
    /** The city this airport sits in, by its code. Null on a city row. */
    val parentIata: String? = null,
    val countryCode: String? = null,
    /**
     * A photograph of the place, which the supplier sends on its own suggestion list and leaves off
     * autocomplete rows. Null for anything typed into the search page.
     */
    val imageUrl: String? = null,
) {
    /**
     * The town, for the search card's one-line field: "New York" out of the city row's "New York,
     * United States", "San Francisco" out of an airport row's location.
     *
     * Which field it comes from depends on [type] — see [location] — and it is the first segment
     * either way, never the whole string: the supplier qualifies both down to the country, and the
     * card has room for the town, not the country it is in.
     */
    val cityName: String
        get() = when (type) {
            FlightDestinationType.City -> name.substringBefore(",").trim().ifEmpty { name }
            else -> location.substringBefore(",").trim()
        }

    /**
     * The country, out of the same string [cityName] takes the town from — the supplier always puts
     * it last. Blank when the row names only one place, which is all a suggestion card can say then.
     */
    val countryName: String
        get() {
            val qualified = if (type == FlightDestinationType.City) name else location
            return qualified.substringAfterLast(",", missingDelimiterValue = "").trim()
        }
}

/** The `type` field of a flight autocomplete row, narrowed to what the picker draws differently. */
enum class FlightDestinationType {
    City,
    Airport,

    /** Anything else the supplier indexes — still a place to fly to, so it is kept, not dropped. */
    Other,
    ;

    companion object {
        fun fromApi(raw: String?): FlightDestinationType = when {
            raw.equals("airport", ignoreCase = true) -> Airport
            raw.equals("city", ignoreCase = true) -> City
            else -> Other
        }
    }
}
