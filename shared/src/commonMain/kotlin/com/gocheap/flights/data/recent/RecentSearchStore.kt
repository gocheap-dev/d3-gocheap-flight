package com.gocheap.flights.data.recent

import com.gocheap.flights.data.model.CarSearch
import com.gocheap.flights.data.model.HotelSearch
import com.gocheap.flights.data.model.TripSearch
import com.gocheap.flights.data.storage.PersistedList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Remembers the searches run on each tab so the three search screens can offer them back.
 *
 * A singleton because a search is recorded on the way out to the results screen and read back later
 * by a tab whose ViewModel may have been recreated in between. Backed by [PersistedList], so the
 * "Recent Searches" row is still populated after the app is killed and relaunched.
 */
object RecentSearchStore {
    /** Enough to fill the row without it quietly becoming a history page. */
    private const val MaxEntries = 6

    /**
     * The key carries a version because an airport stored by an older build has no city code, and
     * a flight search handed to the results SDK without one is the crash this field was added to
     * fix — see [com.gocheap.flights.data.model.Airport.cityCode]. Dropping the row once is the
     * cheaper half of that trade; the user's next search records it again, in the newer shape.
     */
    private val flightStorage = PersistedList("recent_flight_searches_v2", TripSearch.serializer())
    private val stayStorage = PersistedList("recent_stay_searches", HotelSearch.serializer())
    private val rentalStorage = PersistedList("recent_rental_searches", CarSearch.serializer())

    private val _flights = MutableStateFlow(flightStorage.load())
    val flights: StateFlow<List<TripSearch>> = _flights.asStateFlow()

    private val _stays = MutableStateFlow(stayStorage.load())
    val stays: StateFlow<List<HotelSearch>> = _stays.asStateFlow()

    private val _rentals = MutableStateFlow(rentalStorage.load())
    val rentals: StateFlow<List<CarSearch>> = _rentals.asStateFlow()

    fun recordFlight(search: TripSearch) {
        _flights.update { list -> list.moveToFront(search) { it.isSameRoute(search) } }
        flightStorage.save(_flights.value)
    }

    fun recordStay(search: HotelSearch) {
        _stays.update { list -> list.moveToFront(search) { it.isSameCity(search) } }
        stayStorage.save(_stays.value)
    }

    fun recordRental(search: CarSearch) {
        _rentals.update { list -> list.moveToFront(search) { it.isSamePoint(search) } }
        rentalStorage.save(_rentals.value)
    }

    /**
     * Drops one card from the row and leaves the rest of the history alone.
     *
     * Each takes the search rather than an index or a card id: the row a tab draws is built from
     * this list, so the search in hand is the one thing both ends already agree on, and matching
     * it uses the same identity the record above dedupes on — clearing a card therefore removes
     * exactly what re-running that card would have searched.
     */
    fun forgetFlight(search: TripSearch) {
        _flights.update { list -> list.filterNot { it.isSameRoute(search) } }
        flightStorage.save(_flights.value)
    }

    fun forgetStay(search: HotelSearch) {
        _stays.update { list -> list.filterNot { it.isSameCity(search) } }
        stayStorage.save(_stays.value)
    }

    fun forgetRental(search: CarSearch) {
        _rentals.update { list -> list.filterNot { it.isSamePoint(search) } }
        rentalStorage.save(_rentals.value)
    }

    /**
     * Re-running a search moves it back to the front with its new dates rather than stacking a
     * second near-identical card, so the row stays readable.
     */
    private fun <T> List<T>.moveToFront(item: T, isSame: (T) -> Boolean): List<T> =
        (listOf(item) + filterNot(isSame)).take(MaxEntries)

    /**
     * What makes two searches on a tab the same card — the route, the town, the pick-up point —
     * which is what both recording and clearing one match on, and what the tabs key their cards
     * by. Dates and party are left out on purpose: searching the same route a day later replaces
     * the card rather than stacking a second one.
     */
    private fun TripSearch.isSameRoute(other: TripSearch): Boolean =
        from.code == other.from.code && to.code == other.to.code

    private fun HotelSearch.isSameCity(other: HotelSearch): Boolean = city == other.city

    private fun CarSearch.isSamePoint(other: CarSearch): Boolean = pickupPoint == other.pickupPoint
}
