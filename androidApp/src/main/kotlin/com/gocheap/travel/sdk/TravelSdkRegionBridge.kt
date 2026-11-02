package com.gocheap.travel.sdk

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.gocheap.travel.data.settings.AppSettings
import com.gocheap.travel.data.settings.AppSettingsState
import com.gocheap.travel.data.settings.Currency
import com.gocheap.travel.data.settings.FlightPriceDisplay
import com.gocheap.travel.data.settings.StayPriceDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pushes the region chosen on this app's Profile tab into the Travelpayouts SDK, so its flight
 * results come back in the same currency the stay and rental links are built with.
 *
 * The SDK has no API for this. `SdkConfig` carries a marker, an API key and some feature switches
 * and nothing about a region; what it does have is a plain `SharedPreferences` file — `ta_common_prefs`
 * — that its own Information screen writes and its searches read on demand. So this writes the same
 * keys from outside, which is the only seam there is.
 *
 * That makes this the one piece of the integration coupled to the SDK's internals rather than to
 * its public surface. It is kept to this file, it writes nothing it does not understand, and every
 * failure is swallowed: the worst case is that the SDK keeps the region it worked out for itself,
 * which is exactly where this app was before. If an SDK upgrade renames a key, flights quietly go
 * back to that — check here first.
 *
 * Nothing is written until the user has actually chosen, because the SDK geolocates a region on
 * first run and these flags are what tell it to stop. Pushing this app's untouched US/USD defaults
 * would replace a correct guess with a wrong one.
 */
internal object TravelSdkRegionBridge {

    /**
     * Collects for the life of the process: the settings page and the SDK's results screen never
     * overlap, so the write has to happen when the setting changes rather than when results open.
     */
    fun mirrorInto(context: Context, scope: CoroutineScope) {
        val applicationContext = context.applicationContext
        scope.launch {
            AppSettings.state.collectLatest { settings ->
                apply(applicationContext, settings)
            }
        }
    }

    private fun apply(context: Context, settings: AppSettingsState) {
        if (!settings.isRegionChosen && !settings.isPriceDisplayChosen) return

        try {
            val prefs = context.getSharedPreferences(PrefsFile, Context.MODE_PRIVATE)
            prefs.edit().apply {
                if (settings.isRegionChosen) {
                    putString(SelectedCurrency, currencyJson(prefs, settings.currency))
                    putBoolean(CurrencyChosenByUser, true)
                    putString(SelectedCountry, countryJson(settings))
                    putBoolean(CountryChosenByUser, true)
                    putString(SelectedLanguage, settings.languageCode)
                }
                if (settings.isPriceDisplayChosen) {
                    putInt(
                        FlightDisplayPrice,
                        when (settings.flightPrices) {
                            FlightPriceDisplay.AllPassengers -> DisplayAll
                            FlightPriceDisplay.PerPassenger -> DisplaySingle
                        },
                    )
                    putInt(
                        HotelDisplayPrice,
                        when (settings.stayPrices) {
                            StayPriceDisplay.WholeStay -> DisplayAll
                            StayPriceDisplay.PerNight -> DisplaySingle
                        },
                    )
                }
            }.apply()
        } catch (error: Exception) {
            Log.w(LogTag, "Could not hand the region to the Travelpayouts SDK.", error)
        }
    }

    /**
     * The SDK's own row for this currency where it has one.
     *
     * It caches the list it fetched from its API under [CachedCurrencies], and those rows carry a
     * sign and a display format this app does not hold. Reusing the row it already trusts beats
     * handing it one assembled here — which is the fallback, and is enough for the code the rates
     * are actually fetched by.
     */
    private fun currencyJson(prefs: SharedPreferences, currency: Currency): String {
        val cached = prefs.getString(CachedCurrencies, null)
        val row = cached?.let { raw ->
            try {
                val rows = JSONArray(raw)
                (0 until rows.length())
                    .map(rows::getJSONObject)
                    .firstOrNull { it.optString("code").equals(currency.code, ignoreCase = true) }
            } catch (error: Exception) {
                null
            }
        }
        return (row ?: JSONObject()
            .put("code", currency.code)
            .put("name", currency.name)
            .put("sign", currency.symbol))
            .toString()
    }

    /**
     * The country as the SDK stores it: a code, a name, and the currency it is normally quoted in
     * — which is the one chosen here, since the two are set together on the same page.
     */
    private fun countryJson(settings: AppSettingsState): String = JSONObject()
        .put("code", settings.country.code)
        .put("name", settings.country.name)
        .put("currency", settings.currencyCode)
        .toString()

    private const val LogTag = "TravelSdk"

    /** `ta_common_prefs`, as the SDK's own DI module names it. */
    private const val PrefsFile = "ta_common_prefs"

    private const val SelectedCurrency = "selected_currency"
    private const val CurrencyChosenByUser = "is_currency_selected_by_user_key"
    private const val SelectedCountry = "selected_country"
    private const val CountryChosenByUser = "is_country_selected_by_user_key"
    private const val SelectedLanguage = "selected_language"
    private const val CachedCurrencies = "cached_currencies"
    private const val FlightDisplayPrice = "selected_flight_display_price"
    private const val HotelDisplayPrice = "selected_hotel_display_price"

    /** The ids on the SDK's own FlightPriceDisplay and HotelPriceDisplay: ALL = 0, SINGLE = 1. */
    private const val DisplayAll = 0
    private const val DisplaySingle = 1
}
