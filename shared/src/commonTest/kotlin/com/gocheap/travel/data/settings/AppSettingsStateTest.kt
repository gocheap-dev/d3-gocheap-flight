package com.gocheap.travel.data.settings

import com.gocheap.travel.utils.Constants
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three codes here go out on every supplier link and are mirrored into the Travelpayouts SDK's
 * own preferences, so a wrong one quotes the wrong currency rather than merely looking odd.
 */
class AppSettingsStateTest {

    /**
     * The catalogue is read from a bundled file at launch, and a unit test has no resource loader
     * — so the rows these assertions name are installed directly. Three real entries, copied from
     * the shipped file.
     */
    @BeforeTest
    fun installCatalogue() {
        Regions.install(
            """
            [
              {"name": "United Kingdom", "code": "GB",
               "currency": {"code": "GBP", "symbol": "£"}, "flag": "🇬🇧",
               "supported_languages": ["en"]},
              {"name": "France", "code": "FR",
               "currency": {"code": "EUR", "symbol": "€"}, "flag": "🇫🇷",
               "supported_languages": ["fr"]},
              {"name": "United States", "code": "US",
               "currency": {"code": "USD", "symbol": "${'$'}"}, "flag": "🇺🇸",
               "supported_languages": ["en"]}
            ]
            """.trimIndent(),
        )
    }

    @Test
    fun it_opens_on_the_same_fallbacks_the_links_used_before_there_was_a_settings_page() {
        val settings = AppSettingsState()
        assertEquals(Constants.Defaults.Country, settings.countryCode)
        assertEquals(Constants.Defaults.Currency, settings.currencyCode)
        assertEquals(Constants.Defaults.Language, settings.languageCode)
    }

    @Test
    fun nothing_is_marked_as_chosen_until_someone_chooses_it() {
        // This is what keeps the untouched defaults out of the SDK, which geolocates its own.
        val settings = AppSettingsState()
        assertFalse(settings.isRegionChosen)
        assertFalse(settings.isPriceDisplayChosen)
    }

    @Test
    fun a_code_outside_the_catalogue_reads_back_as_itself_rather_than_as_another_row() {
        // It used to answer with the first row of each list, which named a country the user had
        // not chosen — and threw outright once the catalogue could legitimately be empty, which
        // it is for the moment before the bundled file is read.
        val settings = AppSettingsState(countryCode = "ZZ", currencyCode = "XXX", languageCode = "zz")
        assertEquals("ZZ", settings.country.code)
        assertEquals("ZZ", settings.country.name)
        assertEquals("XXX", settings.currency.code)
        assertEquals("zz", settings.language.code)
    }

    @Test
    fun the_rows_read_back_as_the_bare_codes_before_the_catalogue_is_in() {
        // The real state for the first moment of a launch, and for the whole run if the bundled
        // file cannot be read. Nothing functional depends on it — the wire carries the codes.
        Regions.install("[]")
        val settings = AppSettingsState(countryCode = "FR", currencyCode = "EUR")
        assertEquals("FR", settings.country.name)
        assertEquals("EUR", settings.currency.code)
        // The language table is not in the file, so it resolves either way.
        assertEquals("English", settings.language.name)
    }

    @Test
    fun a_stored_code_resolves_however_it_was_cased() {
        val settings = AppSettingsState(countryCode = "gb", currencyCode = "gbp")
        assertEquals("United Kingdom", settings.country.name)
        assertEquals("£", settings.currency.symbol)
    }

    @Test
    fun the_profile_rows_read_back_what_is_in_force() {
        val settings = AppSettingsState(
            countryCode = "FR",
            currencyCode = "EUR",
            languageCode = "fr",
            flightPrices = FlightPriceDisplay.AllPassengers,
            stayPrices = StayPriceDisplay.WholeStay,
        )
        assertEquals("France · EUR · French", settings.regionSummary)
        // The price line is no longer built here: it reads from resources, so the Profile row
        // composes it from the two choices rather than the model handing back English.
        assertEquals(FlightPriceDisplay.AllPassengers, settings.flightPrices)
        assertEquals(StayPriceDisplay.WholeStay, settings.stayPrices)
    }

    @Test
    fun every_catalogue_row_is_unique_and_resolvable_by_its_own_code() {
        listOf(
            Regions.countries.map { it.code },
            Regions.currencies.map { it.code },
            Regions.languages.map { it.code },
        ).forEach { codes ->
            assertEquals(codes.size, codes.toSet().size, "duplicate code in $codes")
        }
        assertTrue(Regions.countries.all { Regions.country(it.code) == it })
        assertTrue(Regions.currencies.all { Regions.currency(it.code) == it })
        assertTrue(Regions.languages.all { Regions.language(it.code) == it })
    }
}
