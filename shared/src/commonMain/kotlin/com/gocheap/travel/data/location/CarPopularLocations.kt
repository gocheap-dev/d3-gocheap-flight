package com.gocheap.travel.data.location

import com.gocheap.travel.data.model.car.CarDestinationDto
import com.gocheap.travel.data.model.car.CarLocation
import com.gocheap.travel.data.model.car.toCarLocation
import flighttp.shared.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The places the Cars tab offers before anything has been typed.
 *
 * Shipped as the supplier's own JSON rather than as a hand-written list of city names, because the
 * rows are the same shape the autocomplete returns — ids, `city_id`, airport codes and all — and a
 * pick from this row therefore reaches the results deeplink with exactly as much to go on as a pick
 * from the search sheet. A name-only list could only be searched for by name.
 *
 * The file is the reference app's `assets/car/CarPopularLocation.json`, carried over unchanged so
 * that refreshing it later is a file copy rather than a transcription.
 *
 * Deliberately separate from [PopularLocations], which the stay side still uses: the two verticals
 * were sharing one list of cities, and the rental supplier's own picks are half airports.
 */
internal object CarPopularLocations {

    private const val ResourcePath = "files/car/CarPopularLocation.json"

    private val json = Json {
        // The file is a snapshot of an autocomplete response, so it carries keys this app has no
        // use for — `image`, `country_code` — and may carry more after the next refresh.
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parsed once and held: the file ships inside the app, so it cannot change while running, and
     * the Cars tab asks for it again every time it is recreated.
     */
    private var cached: List<CarLocation>? = null

    /**
     * An unreadable or malformed file costs the tab its suggestion row and nothing else — the
     * search card, the autocomplete and the recents all work without it — so this answers with an
     * empty list rather than failing the whole screen's load.
     */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): List<CarLocation> {
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
    fun parse(contents: String): List<CarLocation> =
        json.decodeFromString<List<CarDestinationDto>>(contents).mapNotNull(CarDestinationDto::toCarLocation)
}
