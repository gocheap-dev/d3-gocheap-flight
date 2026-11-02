package com.gocheap.travel.data.location

import com.gocheap.travel.data.model.hotel.HotelDestination
import com.gocheap.travel.data.model.hotel.HotelDestinationDto
import com.gocheap.travel.data.model.hotel.toDestination
import flighttp.shared.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The places the Hotels tab offers before anything has been typed. The stay half of
 * [CarPopularLocations], and the same bargain: the supplier's own rows, so a pick from this list
 * carries the ids the results deeplink resolves on rather than a name to be searched for.
 *
 * The file is the reference app's `assets/hotel/HotelPopularLocation.json`, carried over unchanged.
 * It is a narrower shape than the rental one — no airport codes, no slug, and the photograph comes
 * as a `destination_images` pair rather than one flat URL — but every key it does carry is one the
 * autocomplete sends too, so the same DTO reads both.
 */
internal object HotelPopularLocations {

    private const val ResourcePath = "files/hotel/HotelPopularLocation.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Parsed once and held — the file ships inside the app and cannot change while it runs. */
    private var cached: List<HotelDestination>? = null

    /**
     * An unreadable or malformed file costs the tab its suggestion row and nothing else, so this
     * answers with an empty list rather than failing the whole screen's load.
     */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): List<HotelDestination> {
        cached?.let { return it }
        val destinations = try {
            parse(Res.readBytes(ResourcePath).decodeToString())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            emptyList()
        }
        cached = destinations
        return destinations
    }

    /** Split out from the resource read so the mapping can be tested without a resource loader. */
    fun parse(contents: String): List<HotelDestination> =
        json.decodeFromString<List<HotelDestinationDto>>(contents).mapNotNull(HotelDestinationDto::toDestination)
}
