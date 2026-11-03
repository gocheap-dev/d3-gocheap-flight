package com.gocheap.flights.ui.profile

import androidx.lifecycle.ViewModel
import com.gocheap.flights.analytics.ga4.AnalyticsEvent
import com.gocheap.flights.analytics.ga4.FlightAnalytics
import com.gocheap.flights.data.settings.AppSettings
import com.gocheap.flights.data.settings.AppSettingsState
import com.gocheap.flights.data.settings.FlightPriceDisplay
import com.gocheap.flights.data.settings.StayPriceDisplay
import kotlinx.coroutines.flow.StateFlow

/**
 * The one ViewModel behind every Profile page.
 *
 * All four of them — the list, display prices, regional settings and each picker — read and write
 * the same stored settings, and [AppSettings] already holds those as a flow that outlives any of
 * them. So this is a pass-through rather than a second copy of the state: a region picked on one
 * page is on the page behind it before the back animation finishes, and nothing has to be handed
 * back through saved state.
 */
class SettingsViewModel : ViewModel() {
    val state: StateFlow<AppSettingsState> = AppSettings.state

    /*
     * Each of the five reports the choice alongside writing it. The five used to be expression
     * bodies and are now blocks; the signatures are unchanged, since `AppSettings`' setters all
     * return Unit and so did these.
     *
     * Reported even for a pick that matches what is already stored: this counts people reaching
     * the setting and deciding, and the store has no "unchanged" answer to filter on anyway.
     */

    fun onCountryPicked(code: String) {
        FlightAnalytics.settingChanged(AnalyticsEvent.SETTING_COUNTRY, code)
        AppSettings.setCountry(code)
    }

    fun onCurrencyPicked(code: String) {
        FlightAnalytics.settingChanged(AnalyticsEvent.SETTING_CURRENCY, code)
        AppSettings.setCurrency(code)
    }

    fun onLanguagePicked(code: String) {
        FlightAnalytics.settingChanged(AnalyticsEvent.SETTING_LANGUAGE, code)
        AppSettings.setLanguage(code)
    }

    fun onFlightPricesPicked(display: FlightPriceDisplay) {
        FlightAnalytics.settingChanged(AnalyticsEvent.SETTING_FLIGHT_PRICES, display.name)
        AppSettings.setFlightPrices(display)
    }

    fun onStayPricesPicked(display: StayPriceDisplay) {
        FlightAnalytics.settingChanged(AnalyticsEvent.SETTING_STAY_PRICES, display.name)
        AppSettings.setStayPrices(display)
    }
}
