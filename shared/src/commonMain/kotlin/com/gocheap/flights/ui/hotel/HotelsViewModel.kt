package com.gocheap.flights.ui.hotel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.flights.data.Repository
import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.data.model.HotelSearch
import com.gocheap.flights.data.model.LocationSuggestion
import com.gocheap.flights.data.model.hotel.HotelDestination
import com.gocheap.flights.analytics.ga4.AnalyticsEvent
import com.gocheap.flights.analytics.ga4.FlightAnalytics
import com.gocheap.flights.data.recent.RecentSearchStore
import com.gocheap.flights.ui.component.RecentSearchItem
import com.gocheap.flights.ui.component.RecentSearchSubtitle
import com.gocheap.flights.ui.hotel.guests.GuestSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import flighttp.shared.generated.resources.choose_where_you_re_going_first
import flighttp.shared.generated.resources.still_loading_your_stay_details

data class HotelsUiState(
    val search: HotelSearch? = null,
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

class HotelsViewModel(
    private val repository: Repository = Repository(),
) : ViewModel() {
    private val _state = MutableStateFlow(HotelsUiState())
    val state: StateFlow<HotelsUiState> = _state.asStateFlow()

    /**
     * What the cards in the row actually stand for. Kept here rather than in the state because the
     * row draws names and only a click needs the supplier's ids behind one.
     */
    private var popularPicks: List<HotelDestination> = emptyList()

    init {
        viewModelScope.launch {
            // The last search the user actually ran wins over the canned default, so reopening the
            // app lands on the stay they were looking at rather than a stranger's trip. The store
            // reads through to disk, so this survives the process, not just a tab recreation.
            val defaults = repository.hotelDefaultSearch()
            val search = RecentSearchStore.stays.value.firstOrNull()
                ?.withUpcomingDates(defaults)
                ?: defaults
            val popular = repository.hotelPopularLocations()
            popularPicks = popular
            _state.update {
                it.copy(
                    search = search,
                    popularLocations = popular.map(HotelDestination::toSuggestion),
                    isLoading = false,
                )
            }
        }
        /** The store outlives this ViewModel, so the row refills itself after a tab is recreated. */
        viewModelScope.launch {
            RecentSearchStore.stays.collect { searches ->
                _state.update { it.copy(recentSearches = searches.map { s -> s.toRecentItem() }) }
            }
        }
    }

    /**
     * Applies a row picked in the destination sheet. The whole row is kept, not just its name: the
     * results deeplink needs the supplier's own ids, and nothing here could look them up again.
     * Dates are left alone.
     */
    fun onDestinationPicked(destination: HotelDestination) {
        _state.update { current ->
            val search = current.search ?: return@update current
            current.copy(search = search.copy(city = destination.name, destination = destination))
        }
    }

    /**
     * Applies a city from the "Popular Locations" row and runs the search. Like the recent cards,
     * the card is a shortcut to results rather than to a filled form — the dates and party size
     * already on the card are what the search goes out with.
     *
     * Resolved back to the row behind the card, so the pick keeps its ids and reaches the deeplink
     * as precisely as one made in the destination sheet. It falls back to treating the card as a
     * bare name if the list has not loaded — which drops any previously picked destination rather
     * than leaving the old place's ids beside the new name, since the ids are what the supplier
     * actually resolves.
     */
    fun onPopularLocationClick(locationId: String, onResults: (HotelSearch) -> Unit) {
        val picked = popularPicks.firstOrNull { it.id == locationId }
        if (picked != null) {
            onDestinationPicked(picked)
        } else {
            _state.update { current ->
                val search = current.search ?: return@update current
                current.copy(search = search.copy(city = locationId, destination = null))
            }
        }
        // See HomeViewModel.onPopularLocationClick: a tapped destination row and a filled-in form
        // are different intents and are counted apart.
        onSearch(onResults, source = AnalyticsEvent.POPULAR_LOCATION)
    }

    /** Recent cards run the search again rather than only refilling the form. */
    fun onRecentSearchClick(id: String, onResults: (HotelSearch) -> Unit) {
        val search = RecentSearchStore.stays.value.firstOrNull { it.city == id } ?: return
        _state.update { it.copy(search = search) }
        RecentSearchStore.recordStay(search)
        FlightAnalytics.staySearch(search, source = AnalyticsEvent.RECENT_SEARCH)
        onResults(search)
    }

    /** Clears the one card and leaves the rest of the row — and the search card — alone. */
    fun onRecentSearchClear(id: String) {
        val search = RecentSearchStore.stays.value.firstOrNull { it.city == id } ?: return
        RecentSearchStore.forgetStay(search)
    }


    /** Applies what the calendar page handed back, deriving the night count from the span. */
    fun onDatesPicked(start: String, end: String?) {
        _state.update { current ->
            val search = current.search ?: return@update current
            val checkIn = CalendarDate.parse(start) ?: return@update current
            // A stay is at least one night, so a same-day pick rolls check-out to the next day.
            val checkOut = CalendarDate.parse(end)?.takeIf { it > checkIn } ?: checkIn.plusDays(1)
            current.copy(
                search = search.copy(
                    checkInDate = checkIn.label,
                    checkOutDate = checkOut.label,
                    nights = checkIn.daysUntil(checkOut),
                ),
            )
        }
    }

    /**
     * Applies what the guests page handed back: the rooms themselves, each with its own party and
     * the age of every child in it. The counts the card and the deeplink read come off that list,
     * so there is nothing here to reconcile.
     */
    fun onGuestsPicked(selection: GuestSelection) {
        _state.update { current ->
            val search = current.search ?: return@update current
            current.copy(search = search.copy(rooms = GuestSelection.of(selection.rooms).rooms))
        }
    }

    /** [source] is for the measurement only; see `HomeViewModel.onSearchFlight`. */
    fun onSearch(
        onResults: (HotelSearch) -> Unit,
        source: String = AnalyticsEvent.SEARCH_FORM,
    ) {
        val search = state.value.search
        if (search == null) {
            _state.update { it.withSnackbarMessage(Res.string.still_loading_your_stay_details) }
            return
        }
        // Nothing to search until a destination is picked — the card opens without one rather than
        // guessing a city, so this is the ordinary first-run path, not an error.
        if (search.city.isBlank()) {
            _state.update { it.withSnackbarMessage(Res.string.choose_where_you_re_going_first) }
            return
        }
        RecentSearchStore.recordStay(search)
        // After the guards: a tap that only raises "choose where you're going" is not a search.
        FlightAnalytics.staySearch(search, source = source)
        onResults(search)
    }

    fun onSnackbarMessageShown() {
        _state.update { it.copy(snackbarMessage = null) }
    }

}

/**
 * Keeps a restored search's destination and party size but drops dates that have already passed.
 *
 * A saved search outlives the trip it was for. Handing those dates straight back to the card is how
 * it ends up showing a check-in from last year — the same symptom the hardcoded defaults caused.
 */
private fun HotelSearch.withUpcomingDates(defaults: HotelSearch): HotelSearch {
    val checkIn = CalendarDate.parse(checkInDate)
    if (checkIn != null && checkIn >= CalendarDate.today()) return this
    return copy(
        checkInDate = defaults.checkInDate,
        checkOutDate = defaults.checkOutDate,
        nights = defaults.nights,
    )
}

/**
 * What a suggestion card shows. The stay list names the city plainly and qualifies it in [location],
 * so the card reads the name as-is and takes only the country off the qualifier — "Barcelona /
 * Spain" rather than "Barcelona / Catalonia, Spain".
 */
private fun HotelDestination.toSuggestion(): LocationSuggestion = LocationSuggestion(
    // The row's own id, so a click can find its way back to the ids the deeplink needs.
    id = id,
    title = name,
    // countryName is blank when the qualifier names only one place ("Paris" / "France"), which is
    // then the whole of it.
    subtitle = countryName.ifBlank { location },
    imageUrl = imageUrl,
)

/** Keyed by city, which is also what the store dedupes on — so it is unique within the row. */
private fun HotelSearch.toRecentItem(): RecentSearchItem = RecentSearchItem(
    id = city,
    title = city,
    subtitle = RecentSearchSubtitle.Stay(checkInDate, nights, guests),
)

private fun HotelsUiState.withSnackbarMessage(message: StringResource): HotelsUiState = copy(
    snackbarMessage = message,
    snackbarMessageId = snackbarMessageId + 1,
)
