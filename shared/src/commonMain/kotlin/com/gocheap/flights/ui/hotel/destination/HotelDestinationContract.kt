package com.gocheap.flights.ui.hotel.destination

import com.gocheap.flights.data.model.hotel.HotelDestination
import kotlinx.serialization.json.Json

/**
 * The picked row travels back through the caller's saved state, which only takes values it can
 * write out — so it goes as its own JSON rather than as the bare id the flight picker sends. These
 * rows come from the supplier's autocomplete, not a catalogue this app ships, so an id alone would
 * leave the Hotels card with nothing to resolve it against.
 */
internal fun HotelDestination.encodeSelection(): String =
    SelectionJson.encodeToString(HotelDestination.serializer(), this)

/** Null for anything unreadable — the same shrug the recents store takes, for the same reason. */
internal fun decodeHotelDestination(raw: String?): HotelDestination? {
    val encoded = raw ?: return null
    return try {
        SelectionJson.decodeFromString(HotelDestination.serializer(), encoded)
    } catch (error: Exception) {
        null
    }
}

private val SelectionJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
