package com.gocheap.travel.ui.car.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.travel.data.settings.AppSettings
import com.gocheap.travel.analytics.ga4.AnalyticsEvent
import com.gocheap.travel.analytics.ga4.FlightAnalytics
import com.gocheap.travel.data.model.CarSearch
import com.gocheap.travel.data.network.car.CarDeeplink
import com.gocheap.travel.data.recent.RecentSearchStore
import com.gocheap.travel.data.user.SessionTracking
import com.gocheap.travel.data.user.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CarResultsUiState(
    val search: CarSearch? = null,
    /** Null until the deeplink is built; the screen holds its loader until then. */
    val resultUrl: String? = null,
    /** True when there was no search to build a URL from — nothing to show, and no way to retry. */
    val hasNoSearch: Boolean = false,
) {
    /** What the loader says while the page is on its way, e.g. "Searching cars in Paris". */
    /**
     * The town being searched, or blank where it is not known yet.
     *
     * The city alone, not a sentence: the sentence around it is translated, and a ViewModel has
     * no composition to resolve one in. The screen puts the two together.
     */
    val destinationCity: String
        get() = search?.city?.takeIf(String::isNotBlank).orEmpty()
}

/**
 * Turns the rental search the user just ran into the one URL the results page loads.
 *
 * The same shape as [com.gocheap.travel.ui.hotel.results.HotelResultsViewModel], and for the same
 * reason: it reads the search back from [RecentSearchStore] rather than taking it as a navigation
 * argument, so the picked rows reach the deeplink with the supplier's ids still on them instead of
 * being flattened into route text. The search card records the search on its way here, so the newest
 * entry is always this one.
 */
class CarResultsViewModel : ViewModel() {

    private val _state = MutableStateFlow(CarResultsUiState())
    val state: StateFlow<CarResultsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val search = RecentSearchStore.rentals.value.firstOrNull()
            if (search == null) {
                _state.update { it.copy(hasNoSearch = true) }
                return@launch
            }
            // See HotelResultsViewModel: the same three codes, for the same reason.
            val settings = AppSettings.current
            // See HotelResultsViewModel: the exit is recorded where it happens, not where it lands.
            SessionTracking.rentalSearch(search)
            // The GA4 half of the same handoff — see FlightAnalytics.exitClick. As with a stay, a
            // rental only ever leaves as a deeplink.
            FlightAnalytics.exitClick(
                vertical = AnalyticsEvent.RENTAL,
                exit = AnalyticsEvent.EXIT_DEEPLINK,
                screenName = AnalyticsEvent.CUSTOM_WEB_SCREEN,
            )
            _state.update {
                it.copy(
                    search = search,
                    resultUrl = CarDeeplink.forSearch(
                        search = search,
                        country = settings.countryCode,
                        currency = settings.currencyCode,
                        language = settings.languageCode,
                        // Empty until the sign-up behind the splash lands, and the fallback stands
                        // in until it does — see UserSession.deeplinkUserId.
                        userId = UserSession.deeplinkUserId,
                    ),
                )
            }
        }
    }
}
