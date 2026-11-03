package com.gocheap.flights.data


import com.gocheap.flights.data.location.CarPopularLocations
import com.gocheap.flights.data.location.FlightPopularLocations
import com.gocheap.flights.data.location.HotelPopularLocations
import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.data.model.CarSearch
import com.gocheap.flights.data.model.HotelSearch
import com.gocheap.flights.data.model.LocationSuggestion
import com.gocheap.flights.data.model.StayRoom
import com.gocheap.flights.data.model.car.CarDestinationDto
import com.gocheap.flights.data.model.car.CarLocation
import com.gocheap.flights.data.model.car.toCarLocation
import com.gocheap.flights.data.model.flight.FlightDestination
import com.gocheap.flights.data.model.flight.FlightDestinationDto
import com.gocheap.flights.data.model.flight.toDestination
import com.gocheap.flights.data.model.hotel.HotelDestination
import com.gocheap.flights.data.model.hotel.HotelDestinationDto
import com.gocheap.flights.data.model.hotel.toDestination
import com.gocheap.flights.data.network.ApiService
import com.gocheap.flights.utils.Constants
import kotlinx.coroutines.CancellationException

/**
 * In-memory stand-in for a hotel supplier, shaped like the flight repository — except for
 * [searchDestinations], which calls the supplier's autocomplete endpoint for real.
 */
class Repository internal constructor(
    private val apiService: ApiService,
) {
    /** The network layer is module-private, so callers get this and the default wiring with it. */
    constructor() : this(ApiService())

    /**
     * Shown on the Hotels tab as the "Popular Locations" row.
     *
     * Full rows rather than bare names, for the same reason as [carPopularLocations]: these come
     * from the supplier's own list and carry the ids the results deeplink resolves on.
     */
    suspend fun hotelPopularLocations(): List<HotelDestination> = HotelPopularLocations.load()

    /**
     * Dates are offsets from today, never literals: a fixed default goes stale the day after it is
     * written, and this one used to open the search card on December 2022.
     */
    suspend fun hotelDefaultSearch(): HotelSearch {
        val checkIn = CalendarDate.today().plusDays(DefaultDaysUntilCheckIn)
        return HotelSearch(
            // No destination: dates and a party size are safe to guess at, but where someone is
            // going is not, and a prefilled city is one the user never chose and may not notice.
            city = "",
            checkInDate = checkIn.label,
            checkOutDate = checkIn.plusDays(DefaultNights).label,
            nights = DefaultNights,
            // One room for two adults, which is what most stays are booked as.
            rooms = listOf(StayRoom(adults = 2)),
        )
    }

    /**
     * Type-ahead for the destination picker — the one call in this app that hits a real supplier.
     *
     * Failures come back as a failed [Result] rather than an empty list: "the search did not run"
     * and "the search found nothing" need different words on screen, and an exception here is
     * almost always the connection.
     */
    suspend fun hotelSearchDestinations(
        query: String,
        country: String = Constants.Defaults.Country,
        language: String = Constants.Defaults.Language,
    ): Result<List<HotelDestination>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        return try {
            val response = apiService.hotelAutocomplete(
                search = trimmed,
                country = country,
                language = language,
            )
            Result.success(response.mapNotNull(HotelDestinationDto::toDestination))
        } catch (cancellation: CancellationException) {
            // Every keystroke cancels the in-flight search. Swallowing this would turn a cancelled
            // request into a failure the picker then reports as an error.
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /**
     * Shown on the Cars tab as the "Popular Locations" row.
     *
     * Full rows rather than the stay side's names: these come from the supplier's own list and
     * carry the ids the results deeplink needs, so picking one is as precise as searching for it.
     */
    suspend fun carPopularLocations(): List<CarLocation> = CarPopularLocations.load()

    /**
     * Dates are offsets from today, never literals: a fixed default goes stale the day after it is
     * written, and this one used to open the search card on December 2022.
     */
    suspend fun carDefaultSearch(): CarSearch {
        val pickup = CalendarDate.today().plusDays(DefaultDaysUntilPickup)
        return CarSearch(
            // Empty for the same reason as [hotelDefaultSearch]: the desk is the user's to choose.
            city = "",
            pickupPoint = "",
            pickupDate = pickup.label,
            dropoffDate = pickup.plusDays(DefaultRentalDays).label,
            days = DefaultRentalDays,
            pickupTime = "10:00",
            dropoffTime = "10:00",
            // Returned to the pick-up location until the search card says otherwise.
            dropoffCity = null,
            dropoffPoint = null,
        )
    }
    /**
     * Type-ahead for the pick-up and drop-off picker, over the same autocomplete endpoint the stay
     * picker uses.
     *
     * Properties are dropped: the endpoint answers with hotels mixed in, and a rental is collected
     * from a city depot or an airport desk, never from someone's lobby. Everything else is kept —
     * a district is a perfectly good place to look for a depot.
     *
     * Failures come back as a failed [Result] rather than an empty list: "the search did not run"
     * and "the search found nothing" need different words on screen.
     */
    suspend fun carSearchLocations(
        query: String,
        country: String = Constants.Defaults.Country,
        language: String = Constants.Defaults.Language,
    ): Result<List<CarLocation>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        return try {
            val response = apiService.carAutocomplete(
                search = trimmed,
                country = country,
                language = language,
            )
            Result.success(response.mapNotNull(CarDestinationDto::toCarLocation))
        } catch (cancellation: CancellationException) {
            // Every keystroke cancels the in-flight search. Swallowing this would turn a cancelled
            // request into a failure the picker then reports as an error.
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /**
     * Shown on the Home tab as the "Popular Locations" row.
     *
     * Full rows rather than bare codes, for the same reason as [carPopularLocations]: these come
     * from the supplier's own list and carry everything the search card is filled from, so picking
     * one is as precise as searching for it.
     */
    suspend fun flightPopularLocations(): List<FlightDestination> = FlightPopularLocations.load()

    /**
     * Type-ahead for the flight origin and destination picker, over the plane supplier's own
     * autocomplete endpoint.
     *
     * Rows come back as a flat mix of cities and the airports inside them; the picker lists them in
     * the order the supplier ranked them rather than regrouping, which is how the reference app
     * shows the same response.
     *
     * Failures come back as a failed [Result] rather than an empty list: "the search did not run"
     * and "the search found nothing" need different words on screen.
     */
    suspend fun flightSearchDestinations(
        query: String,
        country: String = Constants.Defaults.Country,
        language: String = Constants.Defaults.Language,
    ): Result<List<FlightDestination>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())
        return try {
            val response = apiService.flightAutocomplete(
                search = trimmed,
                country = country,
                language = language,
            )
            Result.success(response.mapNotNull(FlightDestinationDto::toDestination))
        } catch (cancellation: CancellationException) {
            // Every keystroke cancels the in-flight search. Swallowing this would turn a cancelled
            // request into a failure the picker then reports as an error.
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private companion object {

        /** Tomorrow, for three nights — near enough to be useful, never already in the past. */
        const val DefaultDaysUntilCheckIn = 1
        const val DefaultNights = 3

        /** Tomorrow, for three days — near enough to be useful, never already in the past. */
        const val DefaultDaysUntilPickup = 1
        const val DefaultRentalDays = 3

    }
}
