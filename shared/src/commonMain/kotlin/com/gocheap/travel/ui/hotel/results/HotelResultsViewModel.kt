package com.gocheap.travel.ui.hotel.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.travel.data.settings.AppSettings
import com.gocheap.travel.data.network.hotel.StayDeeplink
import com.gocheap.travel.analytics.ga4.AnalyticsEvent
import com.gocheap.travel.analytics.ga4.FlightAnalytics
import com.gocheap.travel.data.model.HotelSearch
import com.gocheap.travel.data.recent.RecentSearchStore
import com.gocheap.travel.data.user.SessionTracking
import com.gocheap.travel.data.user.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HotelResultsUiState(
    val search: HotelSearch? = null,
    /** Null until the deeplink is built; the screen holds its loader until then. */
    val resultUrl: String? = null,
    /** True when there was no search to build a URL from — nothing to show, and no way to retry. */
    val hasNoSearch: Boolean = false,
) {
    /** What the loader says while the page is on its way, e.g. "Searching stays in Paris". */
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
 * Turns the search the user just ran into the one URL the results page loads.
 *
 * Reads the search back from [RecentSearchStore] rather than taking it as a navigation argument —
 * the same shape as the reference app, which reads its selection out of preferences here. The
 * search card records the search on its way out, so the newest entry is always this one, and a
 * destination with its supplier ids rides along on it instead of being flattened into route text.
 */
class HotelResultsViewModel : ViewModel() {

    private val _state = MutableStateFlow(HotelResultsUiState())
    val state: StateFlow<HotelResultsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val search = RecentSearchStore.stays.value.firstOrNull()
            if (search == null) {
                _state.update { it.copy(hasNoSearch = true) }
                return@launch
            }
            // The region chosen on the Profile tab rides along: the supplier prices and
            // localises its results page off these three, so a link built without them would quote
            // the wrong currency to someone who has already said which they want.
            val settings = AppSettings.current
            // The handoff itself: everything below only renders it. Fired here rather than from the
            // web view so a page that fails to load is still a search that left — see
            // SessionTracking, which is a no-op on iOS.
            SessionTracking.staySearch(search)
            // The GA4 half of the same handoff — see FlightAnalytics.exitClick. A stay always
            // leaves as a deeplink; there is no in-app results list for it.
            FlightAnalytics.exitClick(
                vertical = AnalyticsEvent.STAY,
                exit = AnalyticsEvent.EXIT_DEEPLINK,
                screenName = AnalyticsEvent.CUSTOM_WEB_SCREEN,
            )
            _state.update {
                it.copy(
                    search = search,
                    resultUrl = StayDeeplink.forSearch(
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
