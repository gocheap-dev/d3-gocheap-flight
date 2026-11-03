package com.gocheap.flights.ui.home.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.flights.analytics.ga4.AnalyticsEvent
import com.gocheap.flights.analytics.ga4.FlightAnalytics
import com.gocheap.flights.data.model.TripSearch
import com.gocheap.flights.data.network.flight.FlightDeeplink
import com.gocheap.flights.data.recent.RecentSearchStore
import com.gocheap.flights.data.settings.AppSettings
import com.gocheap.flights.data.user.SessionTracking
import com.gocheap.flights.data.user.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlightResultsUiState(
    val search: TripSearch? = null,
    /** Null until the deeplink is built; the screen holds its loader until then. */
    val resultUrl: String? = null,
    /** True when there was no search to build a URL from — nothing to show, and no way to retry. */
    val hasNoSearch: Boolean = false,
) {
    /** Feeds the loader's line; see [destinationCity]. */
    /**
     * The town being searched, or blank where it is not known yet.
     *
     * The city alone, not a sentence: the sentence around it is translated, and a ViewModel has
     * no composition to resolve one in. The screen puts the two together.
     */
    val destinationCity: String
        get() = search?.to?.city?.takeIf(String::isNotBlank).orEmpty()
}

/**
 * Turns the flight search the user just ran into the one URL the results page loads.
 *
 * Only reached when the controller's `new_tab` says `KYK_DL` — the SDK half of that choice never
 * opens this screen, because the SDK brings its own. See `AppController.flightSearchMode`.
 *
 * Reads the search back from [RecentSearchStore] rather than taking it as a navigation argument,
 * exactly as the stay and rental results do: the card records the search on its way out, so the
 * newest entry is always this one, and the airports ride along with their codes intact instead of
 * being flattened into route text.
 */
class FlightResultsViewModel : ViewModel() {
    private val _state = MutableStateFlow(FlightResultsUiState())
    val state: StateFlow<FlightResultsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val search = RecentSearchStore.flights.value.firstOrNull()
            if (search == null) {
                _state.update { it.copy(hasNoSearch = true) }
                return@launch
            }
            // The region chosen on the Profile tab rides along, as it does for the other two
            // verticals: the supplier prices and localises its page off these three.
            val settings = AppSettings.current
            // The handoff itself. Reported as a deeplink rather than as the SDK, which is the whole
            // point of being on this screen — see SessionTracking.FlightExit.
            SessionTracking.flightSearch(search, SessionTracking.FlightExit.Deeplink)
            // The GA4 half of the same handoff, fired beside the backend's and never apart from
            // it — see FlightAnalytics.exitClick. "deeplink" because this screen is the supplier's
            // own page in a web view, which is the whole reason the controller sent us here.
            FlightAnalytics.exitClick(
                vertical = AnalyticsEvent.FLIGHT,
                exit = AnalyticsEvent.EXIT_DEEPLINK,
                screenName = AnalyticsEvent.CUSTOM_WEB_SCREEN,
            )
            _state.update {
                it.copy(
                    search = search,
                    resultUrl = FlightDeeplink.forSearch(
                        search = search,
                        country = settings.countryCode,
                        currency = settings.currencyCode,
                        language = settings.languageCode,
                        userId = UserSession.deeplinkUserId,
                    ),
                )
            }
        }
    }
}
