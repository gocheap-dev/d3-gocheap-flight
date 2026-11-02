package com.gocheap.travel.data.recent

import com.gocheap.travel.data.model.car.CarLocation
import com.gocheap.travel.data.model.flight.FlightDestination
import com.gocheap.travel.data.model.hotel.HotelDestination
import com.gocheap.travel.data.storage.PersistedList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Remembers the places picked on the location pages, so the next visit offers them first.
 *
 * Backed by [PersistedList], so a pick outlives the process — reopening the app a day later still
 * offers what was searched last. Each list loads once, when this object is first touched, and is
 * rewritten whenever it changes.
 *
 * A singleton for the same reason as [RecentSearchStore]: the pickers are separate destinations
 * with their own ViewModels, recreated on every visit.
 */
object RecentLocationStore {
    /** Enough to be a shortcut without pushing the full list off the screen. */
    private const val MaxEntries = 5

    private val flightStorage =
        PersistedList("recent_flight_destinations", FlightDestination.serializer())
    private val stayStorage =
        PersistedList("recent_stay_destinations", HotelDestination.serializer())
    private val carStorage =
        PersistedList("recent_car_locations", CarLocation.serializer())

    /**
     * Flight origins and destinations, held whole for the same reason as the two lists below: they
     * come from the supplier's autocomplete, not from the handful of airports this app ships, so
     * there is nothing local to resolve a bare code against.
     *
     * One list across both ends of a trip rather than two. Somewhere flown from is as likely to be
     * flown back to, so either end is a useful shortcut for the next pick.
     */
    private val _flightDestinations = MutableStateFlow(flightStorage.load())
    val flightDestinations: StateFlow<List<FlightDestination>> = _flightDestinations.asStateFlow()

    /**
     * Stay destinations, held whole for the same reason as the flight list above.
     *
     * The cost of holding a row rather than an id is that it can go stale; picking it simply
     * searches a name the supplier no longer indexes, which the results page already handles.
     */
    private val _stayDestinations = MutableStateFlow(stayStorage.load())
    val stayDestinations: StateFlow<List<HotelDestination>> = _stayDestinations.asStateFlow()

    /**
     * Rental pick-up and drop-off places, held whole for the same reason as the stays above: they
     * come from the supplier's autocomplete, not from a catalogue this app ships.
     *
     * One list across both ends of a rental rather than two. The pair is usually the same place or
     * two points of the same trip, so a drop-off pick is a useful shortcut for the next pick-up.
     */
    private val _carLocations = MutableStateFlow(carStorage.load())
    val carLocations: StateFlow<List<CarLocation>> = _carLocations.asStateFlow()

    fun recordFlightDestination(destination: FlightDestination) {
        _flightDestinations.update { current ->
            (listOf(destination) + current.filterNot { it.iata == destination.iata })
                .take(MaxEntries)
        }
        flightStorage.save(_flightDestinations.value)
    }

    /** The flight picker offers a "Clear" action next to its recent list; this is what it calls. */
    fun clearFlightDestinations() {
        _flightDestinations.value = emptyList()
        flightStorage.save(emptyList())
    }

    fun recordStayDestination(destination: HotelDestination) {
        _stayDestinations.update { current ->
            (listOf(destination) + current.filterNot { it.id == destination.id }).take(MaxEntries)
        }
        stayStorage.save(_stayDestinations.value)
    }

    /** The stay picker offers a "Clear" action next to its recent list; this is what it calls. */
    fun clearStayDestinations() {
        _stayDestinations.value = emptyList()
        // Written through rather than just removed from memory, or the list would come back on the
        // next launch after the user explicitly cleared it.
        stayStorage.save(emptyList())
    }

    fun recordCarLocation(location: CarLocation) {
        _carLocations.update { current ->
            (listOf(location) + current.filterNot { it.id == location.id }).take(MaxEntries)
        }
        carStorage.save(_carLocations.value)
    }

    /** Same contract as [clearStayDestinations] — written through, not just dropped from memory. */
    fun clearCarLocations() {
        _carLocations.value = emptyList()
        carStorage.save(emptyList())
    }
}
