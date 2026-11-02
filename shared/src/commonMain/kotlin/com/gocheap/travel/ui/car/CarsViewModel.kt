package com.gocheap.travel.ui.car

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.travel.data.Repository
import com.gocheap.travel.data.model.CalendarDate
import com.gocheap.travel.data.model.CarSearch
import com.gocheap.travel.data.model.LocationSuggestion
import com.gocheap.travel.data.model.car.CarLocation
import com.gocheap.travel.analytics.ga4.AnalyticsEvent
import com.gocheap.travel.analytics.ga4.FlightAnalytics
import com.gocheap.travel.data.recent.RecentSearchStore
import com.gocheap.travel.ui.component.RecentSearchItem
import com.gocheap.travel.ui.component.RecentSearchSubtitle
import com.gocheap.travel.ui.home.location.LocationTargets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import flighttp.shared.generated.resources.choose_a_pick_up_location_first
import flighttp.shared.generated.resources.still_loading_your_rental_details

data class CarsUiState(
    val search: CarSearch? = null,
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

class CarsViewModel(
    private val repository: Repository = Repository(),
) : ViewModel() {
    private val _state = MutableStateFlow(CarsUiState())
    val state: StateFlow<CarsUiState> = _state.asStateFlow()

    /**
     * What the cards in the row actually stand for. Kept here rather than in the state because the
     * row draws names and only a click needs the supplier's ids behind one.
     */
    private var popularPicks: List<CarLocation> = emptyList()

    init {
        viewModelScope.launch {
            // The last rental the user actually searched for wins over the empty card, the same way
            // the Flights and Hotels tabs restore theirs: the desk, the dates and the times all come
            // back, so a second look at the same rental is one tap rather than a refilled form. The
            // store reads through to disk, so this survives the process, not just a tab recreation.
            val defaults = repository.carDefaultSearch()
            val search = RecentSearchStore.rentals.value.firstOrNull()
                ?.withUpcomingDates(defaults)
                ?: defaults
            val popular = repository.carPopularLocations()
            popularPicks = popular
            _state.update {
                it.copy(
                    search = search,
                    popularLocations = popular.map(CarLocation::toSuggestion),
                    isLoading = false,
                )
            }
        }
        /** The store outlives this ViewModel, so the row refills itself after a tab is recreated. */
        viewModelScope.launch {
            RecentSearchStore.rentals.collect { searches ->
                _state.update { it.copy(recentSearches = searches.map { s -> s.toRecentItem() }) }
            }
        }
    }

    /**
     * Applies a picked location to whichever end of the rental asked for it. Dates are untouched.
     *
     * The city and the depot are set from the same row but are not the same string: an airport row
     * fills the depot with the terminal and the city with the town it serves, which is what the
     * results header and the recent cards read.
     *
     * The row itself rides along too — the results deeplink needs the supplier's ids off it, and no
     * part of this app can get them back from a name alone.
     */
    fun onLocationPicked(target: String, location: CarLocation) {
        _state.update { current ->
            val search = current.search ?: return@update current
            val updated = when (target) {
                LocationTargets.Dropoff -> search.copy(
                    dropoffCity = location.cityLabel,
                    dropoffPoint = location.name,
                    dropoffLocation = location,
                )

                else -> search.copy(
                    city = location.cityLabel,
                    pickupPoint = location.name,
                    pickupLocation = location,
                )
            }
            current.copy(search = updated)
        }
    }

    /**
     * Applies a bare city name, which is all the "Popular Locations" row carries. The depot is
     * derived rather than picked — see [asPoint] — because there is no autocomplete row behind it.
     *
     * Any row picked earlier is dropped along with it: its ids name a different place than the one
     * now on the card, and the deeplink would believe the ids over the name.
     */
    fun onLocationPicked(target: String, city: String) {
        _state.update { current ->
            val search = current.search ?: return@update current
            val updated = when (target) {
                LocationTargets.Dropoff -> search.copy(
                    dropoffCity = city,
                    dropoffPoint = city.asPoint(),
                    dropoffLocation = null,
                )

                else -> search.copy(
                    city = city,
                    pickupPoint = city.asPoint(),
                    pickupLocation = null,
                )
            }
            current.copy(search = updated)
        }
    }

    /**
     * The row only ever fills the pick-up location, then runs the search. Like the recent cards,
     * the card is a shortcut to results rather than to a filled form — the dates and times already
     * on the card are what the search goes out with.
     *
     * Resolved back to the row behind the card so the pick keeps its ids: these come from the
     * supplier's own list, so unlike the stay side there is no reason to fall back to a bare name.
     * It does fall back if the list has not loaded, which only leaves the card doing less, not
     * nothing.
     */
    fun onPopularLocationClick(locationId: String, onResults: (CarSearch) -> Unit) {
        val picked = popularPicks.firstOrNull { it.id == locationId }
        if (picked == null) {
            onLocationPicked(LocationTargets.Pickup, locationId)
        } else {
            onLocationPicked(LocationTargets.Pickup, picked)
        }
        // See HomeViewModel.onPopularLocationClick.
        onSearch(onResults, source = AnalyticsEvent.POPULAR_LOCATION)
    }

    /**
     * Switching back to a same-location return clears the drop-off outright rather than leaving a
     * stale city behind the toggle — "same" is the absence of a drop-off, so the two cannot
     * disagree. Switching away seeds it with the pick-up city, which is the usual starting point.
     */
    fun onSameDropoffChange(isSame: Boolean) {
        _state.update { current ->
            val search = current.search ?: return@update current
            val updated = if (isSame) {
                search.copy(dropoffCity = null, dropoffPoint = null, dropoffLocation = null)
            } else {
                search.copy(
                    dropoffCity = search.city,
                    dropoffPoint = search.pickupPoint,
                    dropoffLocation = search.pickupLocation,
                )
            }
            current.copy(search = updated)
        }
    }

    /** Recent cards run the search again rather than only refilling the form. */
    fun onRecentSearchClick(id: String, onResults: (CarSearch) -> Unit) {
        val search = RecentSearchStore.rentals.value.firstOrNull { it.pickupPoint == id } ?: return
        _state.update { it.copy(search = search) }
        RecentSearchStore.recordRental(search)
        FlightAnalytics.rentalSearch(search, source = AnalyticsEvent.RECENT_SEARCH)
        onResults(search)
    }

    /** Clears the one card and leaves the rest of the row — and the search card — alone. */
    fun onRecentSearchClear(id: String) {
        val search = RecentSearchStore.rentals.value.firstOrNull { it.pickupPoint == id } ?: return
        RecentSearchStore.forgetRental(search)
    }


    /**
     * Applies what the calendar page handed back, deriving the day count from the span. The times
     * come from the same page; an absent one keeps whatever was set before.
     */
    fun onDatesPicked(start: String, end: String?, startTime: String?, endTime: String?) {
        _state.update { current ->
            val search = current.search ?: return@update current
            val pickup = CalendarDate.parse(start) ?: return@update current
            // A rental runs at least a day, so a same-day pick rolls drop-off to the next day.
            val dropoff = CalendarDate.parse(end)?.takeIf { it > pickup } ?: pickup.plusDays(1)
            current.copy(
                search = search.copy(
                    pickupDate = pickup.label,
                    dropoffDate = dropoff.label,
                    days = pickup.daysUntil(dropoff),
                    pickupTime = startTime ?: search.pickupTime,
                    dropoffTime = endTime ?: search.dropoffTime,
                ),
            )
        }
    }

    /** [source] is for the measurement only; see `HomeViewModel.onSearchFlight`. */
    fun onSearch(
        onResults: (CarSearch) -> Unit,
        source: String = AnalyticsEvent.SEARCH_FORM,
    ) {
        val search = state.value.search
        if (search == null) {
            _state.update { it.withSnackbarMessage(Res.string.still_loading_your_rental_details) }
            return
        }
        // Nothing to search until a pick-up is chosen — the card opens without one rather than
        // guessing a depot, so this is the ordinary first-run path, not an error.
        if (search.pickupPoint.isBlank()) {
            _state.update { it.withSnackbarMessage(Res.string.choose_a_pick_up_location_first) }
            return
        }
        RecentSearchStore.recordRental(search)
        // After the guards: a tap that only raises "choose a pick-up location" is not a search.
        FlightAnalytics.rentalSearch(search, source = source)
        onResults(search)
    }

    fun onSnackbarMessageShown() {
        _state.update { it.copy(snackbarMessage = null) }
    }

}

/**
 * Keeps a restored rental's depots and desk times but drops dates that have already passed.
 *
 * A saved search outlives the rental it was for. Handing those dates straight back to the card is
 * how it ends up offering to collect a car last March — the same repair the trip and stay cards
 * make.
 *
 * The whole span is replaced rather than the pick-up alone: a drop-off left behind a new pick-up
 * would read as a rental that ends before it starts, and [CarsUiState.search] is what the search
 * goes out with.
 */
private fun CarSearch.withUpcomingDates(defaults: CarSearch): CarSearch {
    val pickup = CalendarDate.parse(pickupDate)
    if (pickup != null && pickup >= CalendarDate.today()) return this
    return copy(
        pickupDate = defaults.pickupDate,
        dropoffDate = defaults.dropoffDate,
        days = defaults.days,
    )
}

/**
 * What a suggestion card shows: the town and the country it is in, never the supplier's qualified
 * name — "Los Angeles / United States" rather than "Los Angeles International Airport". Half of
 * this list is airports, and a card is 148dp wide.
 */
private fun CarLocation.toSuggestion(): LocationSuggestion = LocationSuggestion(
    // The row's own id, so a click can find its way back to the ids the deeplink needs.
    id = id,
    title = cityLabel,
    subtitle = countryLabel,
    imageUrl = imageUrl,
)

/** Keyed by pick-up point, which is also what the store dedupes on — unique within the row. */
private fun CarSearch.toRecentItem(): RecentSearchItem = RecentSearchItem(
    id = pickupPoint,
    // A one-way rental names both ends; a same-location one would just repeat itself.
    title = if (isSameDropoff) city else "$city → $dropoffCity",
    subtitle = RecentSearchSubtitle.Rental(pickupDate, pickupTime, days),
)

private fun CarsUiState.withSnackbarMessage(message: StringResource): CarsUiState = copy(
    snackbarMessage = message,
    snackbarMessageId = snackbarMessageId + 1,
)

/** The sample supplier has one depot per city; a real one would offer a list of them. */
private fun String.asPoint(): String = "$this downtown"
