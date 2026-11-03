package com.gocheap.flights.ui.home.location

import com.gocheap.flights.data.model.Airport
import com.gocheap.flights.data.model.flight.FlightDestination
import com.gocheap.flights.data.model.flight.FlightDestinationType
import kotlinx.serialization.json.Json

/**
 * How much of the supplier's answer the picker lists. One endpoint backs both — it returns cities
 * and the airports inside them interleaved — so this narrows the response rather than choosing a
 * different call.
 */
enum class LocationKind {
    /** Everything the endpoint indexes: airports, and the cities they sit in. */
    Airport,

    /** Cities only, for a field that cannot do anything with a single terminal. */
    City,
    ;

    companion object {
        /** Route arguments are strings; anything unrecognised falls back to cities. */
        fun fromRoute(raw: String?): LocationKind =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: City
    }
}

/** What a search screen asks the location page for. */
data class LocationRequest(
    val title: String,
    val kind: LocationKind,
    /**
     * Which field is being filled, echoed back untouched in [LocationSelection]. The flight card
     * has two of them, so without this the result would be ambiguous.
     */
    val target: String,
    /** Hidden from the list — the flight card's other endpoint, so a trip cannot start and end in one place. */
    val excludeId: String? = null,
)

/** What the location page hands back: the row that was tapped, plus the field it fills. */
data class LocationSelection(
    val target: String,
    val destination: FlightDestination,
)

/** The [LocationRequest.target] values the three search cards use. */
object LocationTargets {
    const val Origin = "from"
    const val Destination = "to"
    const val City = "city"
    const val Pickup = "pickup"
    const val Dropoff = "dropoff"
}

/**
 * The picked row travels back through the caller's saved state, which only takes values it can
 * write out — so it goes as its own JSON rather than as the bare code it used to. The list comes
 * from the supplier's autocomplete now, not the handful of airports this app ships, so a code alone
 * would leave the search card with nothing to resolve it against.
 */
internal fun FlightDestination.encodeSelection(): String =
    SelectionJson.encodeToString(FlightDestination.serializer(), this)

/** Null for anything unreadable — the same shrug the recents store takes, for the same reason. */
internal fun decodeFlightDestination(raw: String?): FlightDestination? {
    val encoded = raw ?: return null
    return try {
        SelectionJson.decodeFromString(FlightDestination.serializer(), encoded)
    } catch (error: Exception) {
        null
    }
}

private val SelectionJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * An autocomplete row as the airport a search runs on.
 *
 * Shared by the home search card and the results screen's Modify Search sheet, which pick from the
 * same list and have to read a picked row identically — a second copy of this would be a second
 * set of rules for what a city row's name means.
 *
 * The card holds an [Airport]; the supplier answers with a row of its own.
 *
 * The card draws the town large with the code beside it and the terminal underneath, so the second
 * line comes off whichever field names a terminal: the row's own name for an airport, and the
 * supplier's "All airports" for a city, which is exactly what picking a city means.
 */
internal fun FlightDestination.toAirport(): Airport = Airport(
    code = iata,
    city = cityName.ifBlank { name },
    cityCode = parentIata.orEmpty(),
    name = when (type) {
        FlightDestinationType.City -> location.ifBlank { name }
        else -> name
    },
)
