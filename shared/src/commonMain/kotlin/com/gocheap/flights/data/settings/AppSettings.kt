package com.gocheap.flights.data.settings

import com.gocheap.flights.data.storage.PersistedValue
import com.gocheap.flights.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** How a flight price is quoted: for one seat, or for the whole party being searched for. */
@Serializable
enum class FlightPriceDisplay { PerPassenger, AllPassengers }

/** The stay equivalent: one night of the stay, or every night of it added up. */
@Serializable
enum class StayPriceDisplay { PerNight, WholeStay }

/**
 * Everything the Profile pages collect, in the shape it is stored.
 *
 * Codes rather than the rows themselves: the lists in [Regions] can grow or be renamed between
 * builds, and a stored code still resolves against the new list where a stored copy of a row would
 * quietly go stale.
 */
@Serializable
data class AppSettingsState(
    val countryCode: String = Constants.Defaults.Country,
    val currencyCode: String = Constants.Defaults.Currency,
    val languageCode: String = Constants.Defaults.Language,
    val flightPrices: FlightPriceDisplay = FlightPriceDisplay.PerPassenger,
    val stayPrices: StayPriceDisplay = StayPriceDisplay.PerNight,
    /**
     * Whether each group was actually chosen here, as opposed to still sitting on its default.
     *
     * The Travelpayouts SDK works the same way — it keeps an `is_..._selected_by_user` flag beside
     * each value and only geolocates while that flag is false. The flags are what stop this app's
     * untouched defaults from being pushed into the SDK and overriding a region it worked out for
     * itself; see `TravelSdkRegionBridge`.
     */
    val isRegionChosen: Boolean = false,
    /**
     * Whether a human picked the language, as opposed to it being inferred.
     *
     * Separate from [isRegionChosen] because the two are undone by different things. Region
     * detection follows the device's language on every later launch — see
     * [com.gocheap.flights.data.settings.RegionDefaults] — and without this flag that would
     * quietly revert a language the user had chosen on the Profile tab the moment their phone
     * disagreed with it.
     */
    val isLanguageChosen: Boolean = false,
    val isPriceDisplayChosen: Boolean = false,
) {
    /**
     * The stored code as the catalogue describes it, or as little as the code itself says.
     *
     * The fallback is a real case, not a defensive one: the catalogue is read from a bundled file
     * after launch, so every one of these answers on the bare code for the moment before [Regions.load]
     * finishes, and for the whole run if the file could not be read. It used to take the first row
     * of the list instead — which named the wrong country, and threw outright once the list could
     * be empty.
     *
     * Nothing functional depends on it either way: what goes on the wire is [countryCode] and its
     * two siblings, and these objects only put a name and a flag on screen beside one.
     */
    val country: Country
        get() = Regions.country(countryCode)
            ?: Country(code = countryCode, name = countryCode, flag = "", currencyCode = currencyCode)

    val currency: Currency
        get() = Regions.currency(currencyCode)
            ?: Currency(code = currencyCode, name = currencyCode, symbol = currencyCode)

    val language: AppLanguage
        get() = Regions.language(languageCode)
            ?: AppLanguage(
                code = languageCode,
                name = languageCode,
                endonym = languageCode,
                apiCode = languageCode,
            )

    /** "United States · USD · English", the line the Profile row reads back. */
    val regionSummary: String get() = "${country.name} · ${currency.code} · ${language.name}"

}

/**
 * The app's own preferences, as the SDK's Information screen collects them.
 *
 * A singleton because the region is read on the way out to a supplier — by a ViewModel that may
 * have been created long before the setting was changed — and written on a settings page that knows
 * nothing about either. Backed by [PersistedValue], so a chosen region outlives the process.
 *
 * The three codes are not decoration: they go out as `country`, `currency` and `language` on every
 * stay and rental link, which is what the suppliers price and localise their results against.
 */
object AppSettings {
    private val storage = PersistedValue("app_settings", AppSettingsState.serializer())

    private val _state = MutableStateFlow(storage.load(AppSettingsState()))
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    /** Read straight through for callers building a link outside composition. */
    val current: AppSettingsState get() = _state.value

    fun setCountry(code: String) = update { it.copy(countryCode = code, isRegionChosen = true) }

    fun setCurrency(code: String) = update { it.copy(currencyCode = code, isRegionChosen = true) }

    /**
     * The one setting that changes the app's own words, not just the supplier's.
     *
     * [AppLanguageController.sync] is what carries it to the platform; storing the code alone
     * would leave every button in the app in the previous language.
     */
    fun setLanguage(code: String) {
        update { it.copy(languageCode = code, isRegionChosen = true, isLanguageChosen = true) }
        AppLanguageController.sync()
    }

    fun setFlightPrices(display: FlightPriceDisplay) = update {
        it.copy(flightPrices = display, isPriceDisplayChosen = true)
    }

    fun setStayPrices(display: StayPriceDisplay) = update {
        it.copy(stayPrices = display, isPriceDisplayChosen = true)
    }

    /**
     * Applies what the device implied about the region, without recording it as a choice.
     *
     * Each code is optional and an absent one leaves that setting alone, because the three are
     * detected independently — a country may resolve where a currency does not.
     *
     * [AppSettingsState.isRegionChosen] deliberately stays as it was. An inference is not a
     * choice: the flag is what keeps a guess out of the Travelpayouts SDK's own preferences, which
     * it geolocates for itself. See [com.gocheap.flights.data.settings.RegionDefaults].
     */
    internal fun applyDetectedRegion(
        countryCode: String? = null,
        currencyCode: String? = null,
        languageCode: String? = null,
    ) = update { current ->
        current.copy(
            countryCode = countryCode ?: current.countryCode,
            currencyCode = currencyCode ?: current.currencyCode,
            languageCode = languageCode ?: current.languageCode,
        )
    }

    private fun update(transform: (AppSettingsState) -> AppSettingsState) {
        _state.update(transform)
        storage.save(_state.value)
    }
}
