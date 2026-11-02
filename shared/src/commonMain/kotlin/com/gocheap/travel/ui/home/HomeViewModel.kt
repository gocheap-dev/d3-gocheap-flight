package com.gocheap.travel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.travel.data.Repository
import com.gocheap.travel.data.flight.FlightRepository
import com.gocheap.travel.data.model.Airport
import com.gocheap.travel.data.model.CalendarDate
import com.gocheap.travel.data.model.LocationSuggestion
import com.gocheap.travel.data.model.flight.FlightDestination
import com.gocheap.travel.data.model.flight.FlightDestinationType
import com.gocheap.travel.data.model.TripSearch
import com.gocheap.travel.analytics.ga4.AnalyticsEvent
import com.gocheap.travel.analytics.ga4.FlightAnalytics
import com.gocheap.travel.data.recent.RecentSearchStore
import com.gocheap.travel.ui.component.RecentSearchItem
import com.gocheap.travel.ui.component.RecentSearchSubtitle
import com.gocheap.travel.ui.home.location.LocationTargets
import com.gocheap.travel.ui.home.location.toAirport
import com.gocheap.travel.ui.home.travellers.TravellerSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import flighttp.shared.generated.resources.choose_where_you_re_flying_from_first
import flighttp.shared.generated.resources.choose_where_you_re_flying_to_first
import flighttp.shared.generated.resources.origin_and_destination_must_differ
import flighttp.shared.generated.resources.still_loading_your_trip_details

data class HomeUiState(
    val search: TripSearch? = null,
    val recentSearches: List<RecentSearchItem> = emptyList(),
    val popularLocations: List<LocationSuggestion> = emptyList(),
    val isLoading: Boolean = true,
    /**
     * The resource, not the text: a ViewModel has no composition to resolve a string in, and
     * resolving at construction would pin the message to whichever language was current then.
     */
    val snackbarMessage: StringResource? = null,
    val snackbarMessageId: Int = 0,
)

class HomeViewModel(
    private val repository: FlightRepository = FlightRepository(),
    private val supplierRepository: Repository = Repository(),
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * What the cards in the row actually stand for. Kept here rather than in the state because the
     * row draws names and pictures, and only a click needs the codes behind one.
     */
    private var popularPicks: List<FlightDestination> = emptyList()

    init {
        load()
        observeRecentSearches()
    }

    private fun load() {
        viewModelScope.launch {
            // The last trip the user actually searched for wins over the empty card, the same way
            // the Hotels and Cars tabs restore theirs — otherwise, now that nothing is prefilled,
            // the card would ask for both ends again on every launch rather than only the first.
            // The store reads through to disk, so this survives the process, not just a tab.
            val defaults = repository.defaultSearch()
            val search = RecentSearchStore.flights.value.firstOrNull()
                ?.withUpcomingDates(defaults)
                ?: defaults
            val popular = supplierRepository.flightPopularLocations()
            popularPicks = popular
            _state.update {
                it.copy(
                    search = search,
                    popularLocations = popular.map(FlightDestination::toSuggestion),
                    isLoading = false,
                )
            }
        }
    }

    /** The store outlives this ViewModel, so the row refills itself after a tab is recreated. */
    private fun observeRecentSearches() {
        viewModelScope.launch {
            RecentSearchStore.flights.collect { searches ->
                _state.update { it.copy(recentSearches = searches.map { s -> s.toRecentItem() }) }
            }
        }
    }

    /**
     * Fills the destination from the "Popular Locations" row and runs the search, leaving the dates
     * alone. Like the recent cards, the card is a shortcut to results rather than to a filled form
     * — the dates and travellers already on the card are what the search goes out with.
     *
     * Resolved back to the row behind the card, which carries the code the search actually runs on
     * — a card titled "London" stands for LON, and nothing on screen says so. A row that cannot be
     * resolved is ignored rather than guessed at: the only way here is a tap on a card this list
     * put there.
     */
    fun onPopularLocationClick(locationId: String, onSearch: (TripSearch) -> Unit) {
        val picked = popularPicks.firstOrNull { it.iata == locationId } ?: return
        onLocationPicked(LocationTargets.Destination, picked)
        // Reported as its own kind of search rather than as the card's button: a tap on a
        // destination row and a filled-in form are different intents, and one count of searches
        // would hide which of the two the row is actually earning.
        onSearchFlight(onSearch, source = AnalyticsEvent.POPULAR_LOCATION)
    }

    /** Recent cards run the search again rather than only refilling the form. */
    fun onRecentSearchClick(id: String, onSearch: (TripSearch) -> Unit) {
        val search = RecentSearchStore.flights.value.firstOrNull { it.recentId() == id } ?: return
        _state.update { it.copy(search = search) }
        RecentSearchStore.recordFlight(search)
        FlightAnalytics.flightSearch(search, source = AnalyticsEvent.RECENT_SEARCH)
        onSearch(search)
    }

    /**
     * Clears the one card, leaving the rest of the row and the card now on screen alone: this
     * forgets a search, it does not undo one. The row redraws off the store's own list, so there
     * is no state here to keep in step.
     */
    fun onRecentSearchClear(id: String) {
        val search = RecentSearchStore.flights.value.firstOrNull { it.recentId() == id } ?: return
        RecentSearchStore.forgetFlight(search)
    }

    fun onSwapAirports() {
        _state.update { current ->
            val search = current.search ?: return@update current
            current.copy(search = search.copy(from = search.to, to = search.from))
        }
    }

    fun onRoundTripChange(isRoundTrip: Boolean) {
        _state.update { current ->
            val search = current.search ?: return@update current
            // Turning the toggle on has to leave a usable return date behind it. The old one is
            // kept when the toggle goes off, so flicking it back restores what you had.
            val returnDate = if (isRoundTrip) search.validReturnDate() else search.returnDate
            current.copy(search = search.copy(isRoundTrip = isRoundTrip, returnDate = returnDate))
        }
    }

    /**
     * Applies a row chosen on the location page to whichever field asked for it.
     *
     * Taken as it comes rather than resolved against [FlightRepository.airports]: the page searches
     * the supplier now, which indexes far more places than the handful this app ships, and looking
     * the code up locally would silently drop every pick outside that list.
     */
    fun onLocationPicked(target: String, destination: FlightDestination) {
        _state.update { current ->
            val search = current.search ?: return@update current
            current.copy(search = search.withAirport(target, destination.toAirport()))
        }
    }

    /**
     * Applies what the travellers page handed back: the party split into adults, children and
     * infants, and the cabin it is flying in. The head count the card and the recent cards read
     * comes off those three, so there is nothing here to reconcile.
     */
    fun onTravellersPicked(selection: TravellerSelection) {
        _state.update { current ->
            val search = current.search ?: return@update current
            val party = TravellerSelection.of(
                adults = selection.adults,
                children = selection.children,
                infants = selection.infants,
                cabinClass = selection.cabinClass,
            )
            current.copy(
                search = search.copy(
                    adults = party.adults,
                    children = party.children,
                    infants = party.infants,
                    cabinClass = party.cabinClass,
                ),
            )
        }
    }

    /** Applies what the calendar page handed back. [end] is null when the trip is one-way. */
    fun onDatesPicked(start: String, end: String?) {
        _state.update { current ->
            val search = current.search ?: return@update current
            val departure = CalendarDate.parse(start) ?: return@update current
            // A one-way pick leaves the stored return date alone, so flipping the toggle back on
            // restores what was there; onRoundTripChange re-validates it at that point.
            val returnDate = CalendarDate.parse(end)?.takeIf { it > departure }?.label
                ?: search.returnDate
            current.copy(
                search = search.copy(departureDate = departure.label, returnDate = returnDate),
            )
        }
    }

    /**
     * The search card's button.
     *
     * [source] is for the measurement only and never changes what is searched — it separates this
     * from the two one-tap paths above, which come through here as well.
     */
    fun onSearchFlight(
        onSearch: (TripSearch) -> Unit,
        source: String = AnalyticsEvent.SEARCH_FORM,
    ) {
        val search = state.value.search
        if (search == null) {
            _state.update { it.withSnackbarMessage(Res.string.still_loading_your_trip_details) }
            return
        }
        // Nothing to search until both ends are picked — the card now opens without them rather
        // than guessing a route, so this is the ordinary first-run path, not an error.
        if (!search.from.isChosen) {
            _state.update { it.withSnackbarMessage(Res.string.choose_where_you_re_flying_from_first) }
            return
        }
        if (!search.to.isChosen) {
            _state.update { it.withSnackbarMessage(Res.string.choose_where_you_re_flying_to_first) }
            return
        }
        if (search.from.code == search.to.code) {
            _state.update { it.withSnackbarMessage(Res.string.origin_and_destination_must_differ) }
            return
        }
        RecentSearchStore.recordFlight(search)
        // After the guards, not before: a tap that only raises "choose where you're flying from"
        // is not a search, and counting it would inflate every funnel built on this event.
        FlightAnalytics.flightSearch(search, source = source)
        onSearch(search)
    }

    fun onSnackbarMessageShown() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private companion object {
        /** How long a round trip runs when the toggle goes on without a return date behind it. */
        const val DefaultTripNights = 7
    }

    /** Keeps the current return date if it still falls after departure, else picks a default. */
    private fun TripSearch.validReturnDate(): String {
        val departure = CalendarDate.parseOrFallback(departureDate)
        val existing = CalendarDate.parse(returnDate)
        return if (existing != null && existing > departure) {
            existing.label
        } else {
            departure.plusDays(DefaultTripNights).label
        }
    }
}

/**
 * Keeps a restored trip's route and party but drops dates that have already passed.
 *
 * A saved search outlives the trip it was for. Handing those dates straight back to the card is how
 * it ends up offering to fly out last March — the same repair the stay and rental cards make.
 */
private fun TripSearch.withUpcomingDates(defaults: TripSearch): TripSearch {
    val departure = CalendarDate.parse(departureDate)
    if (departure != null && departure >= CalendarDate.today()) return this
    return copy(
        departureDate = defaults.departureDate,
        // Only a round trip needs one back, and the stored one is as stale as the departure.
        returnDate = if (isRoundTrip) defaults.returnDate else returnDate,
    )
}

/**
 * The picker already hides the opposite endpoint, so the two can only collide when the airport
 * arrives from somewhere else — the Popular Locations row. Swapping beats refusing the tap.
 */
/** One card in the "Popular Locations" row: the town, the country under it, and its photograph. */
private fun FlightDestination.toSuggestion(): LocationSuggestion = LocationSuggestion(
    // The row's own code, so a click can find its way back to the place the card stands for.
    id = iata,
    title = cityName.ifBlank { name },
    subtitle = countryName,
    imageUrl = imageUrl,
)


private fun TripSearch.withAirport(target: String, picked: Airport): TripSearch = when (target) {
    LocationTargets.Origin ->
        if (picked.code == to.code) copy(from = picked, to = from) else copy(from = picked)
    LocationTargets.Destination ->
        if (picked.code == from.code) copy(from = to, to = picked) else copy(to = picked)
    else -> this
}

/** Route pair, which is also what the store dedupes on — so it is unique within the row. */
private fun TripSearch.recentId(): String = "${from.code}-${to.code}"

private fun TripSearch.toRecentItem(): RecentSearchItem = RecentSearchItem(
    id = recentId(),
    title = "${from.code} → ${to.code}",
    // A round trip shows the range it was booked for; the arrow already says it is a return.
    subtitle = RecentSearchSubtitle.Trip(
        departure = departureDate,
        returnDate = returnDate.takeIf { isRoundTrip },
    ),
)

private fun HomeUiState.withSnackbarMessage(message: StringResource): HomeUiState = copy(
    snackbarMessage = message,
    snackbarMessageId = snackbarMessageId + 1,
)
