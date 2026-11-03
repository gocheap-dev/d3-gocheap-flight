package com.gocheap.flights.data.location

import com.gocheap.flights.data.model.flight.FlightDestination
import com.gocheap.flights.data.model.flight.FlightDestinationDto
import com.gocheap.flights.data.model.flight.toDestination
import flighttp.shared.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The places the Home tab offers before anything has been typed.
 *
 * Shipped as the supplier's own JSON rather than as a hand-written list of cities, for the same
 * reason as [CarPopularLocations]: the rows are the shape the autocomplete returns — codes, types
 * and all — so a pick from this row fills the search card with exactly as much as a pick from the
 * location page. A name-only list could only be searched for by name, and this one is half
 * metropolitan areas, which are not airports.
 *
 * The file is the reference app's `assets/flight/FlightPopularLocation.json`, carried over
 * unchanged so that refreshing it later is a file copy rather than a transcription.
 */
internal object FlightPopularLocations {

    private const val ResourcePath = "files/flight/FlightPopularLocation.json"

    private val json = Json {
        // The file is a snapshot of an autocomplete response with a picture pair bolted on, so it
        // carries keys this app has no use for and may carry more after the next refresh.
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parsed once and held: the file ships inside the app, so it cannot change while running, and
     * the Home tab asks for it again every time it is recreated.
     */
    private var cached: List<FlightDestination>? = null

    /**
     * An unreadable or malformed file costs the tab its suggestion row and nothing else — the
     * search card, the autocomplete and the recents all work without it — so this answers with an
     * empty list rather than failing the whole screen's load.
     */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): List<FlightDestination> {
        cached?.let { return it }
        val locations = try {
            parse(Res.readBytes(ResourcePath).decodeToString())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            emptyList()
        }
        cached = locations
        return locations
    }

    /**
     * Split out from the resource read so the mapping can be tested without a resource loader —
     * and because a row the picker cannot use is dropped here exactly as it is for a live response.
     */
    fun parse(contents: String): List<FlightDestination> =
        json.decodeFromString<List<FlightDestinationDto>>(contents)
            .mapNotNull(FlightDestinationDto::toDestination)
}
