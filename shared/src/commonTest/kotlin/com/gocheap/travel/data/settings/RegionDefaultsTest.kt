package com.gocheap.travel.data.settings

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which region a first launch opens on.
 *
 * The decisions worth pinning are not the platform reads — those are three lines each — but what
 * is done with them: which country wins, whether its currency comes with it, and when the device's
 * language is honoured versus overridden. Each is silent when wrong: the app works, it just prices
 * in the wrong currency or answers in a language the supplier does not serve.
 */
class RegionDefaultsTest {

    @BeforeTest
    fun installCatalogue() {
        Regions.install(
            """
            [
              {"name": "Japan", "code": "JP",
               "currency": {"code": "JPY", "symbol": "¥"}, "flag": "🇯🇵",
               "supported_languages": ["ja"]},
              {"name": "Germany", "code": "DE",
               "currency": {"code": "EUR", "symbol": "€"}, "flag": "🇩🇪",
               "supported_languages": ["de", "en"]},
              {"name": "Anguilla", "code": "AI",
               "currency": {"code": "XCD", "symbol": "${'$'}"}, "flag": "🇦🇮",
               "supported_languages": null},
              {"name": "Indonesia", "code": "ID",
               "currency": {"code": "IDR", "symbol": "Rp"}, "flag": "🇮🇩",
               "supported_languages": ["in", "en"]}
            ]
            """.trimIndent(),
        )
    }

    /** The piece under test, exercised directly so no device is needed to reach it. */
    private fun resolve(country: String?, language: String?, currency: String? = null) =
        RegionDefaults.resolve(
            hints = RegionHints(country, language, currency),
            settings = AppSettingsState(),
        )

    @Test
    fun a_detected_country_brings_its_own_currency() {
        // The whole reason the catalogue pairs them: a phone in Japan should see yen, not the
        // dollars the app ships defaulted to.
        val resolved = resolve(country = "JP", language = "ja")
        assertEquals("JP", resolved?.countryCode)
        assertEquals("JPY", resolved?.currencyCode)
        assertEquals("ja", resolved?.languageCode)
    }

    @Test
    fun a_country_code_is_normalised_the_way_the_catalogue_spells_it() {
        // Telephony reports lowercase; the catalogue and the wire use uppercase.
        assertEquals("DE", resolve(country = "de", language = "de")?.countryCode)
    }

    @Test
    fun a_language_the_country_is_not_served_in_falls_back_to_english() {
        // A Greek phone pointed at Japan: Japanese prices, English words. `el` is not a language
        // the Japanese site is served in, so asking for it would get a page nobody can read.
        val resolved = resolve(country = "JP", language = "el")
        assertEquals("JPY", resolved?.currencyCode)
        assertEquals("en", resolved?.languageCode)
    }

    @Test
    fun a_row_that_lists_no_languages_accepts_any_this_app_knows() {
        // Most of the catalogue says nothing about languages. Treating silence as "none supported"
        // would force every one of those countries to English.
        assertEquals("fr", resolve(country = "AI", language = "fr")?.languageCode)
    }

    @Test
    fun the_catalogues_legacy_language_codes_still_match() {
        // The file spells Indonesian "in", Java's legacy form; this app stores "id". Matching only
        // on the stored code would read Indonesia as not supporting Indonesian.
        assertEquals("id", resolve(country = "ID", language = "id")?.languageCode)
    }

    @Test
    fun a_country_outside_the_catalogue_keeps_its_code_and_takes_the_devices_currency() {
        // The supplier knows more countries than the bundled file lists, so the code is still
        // worth sending — there is simply no row to take a currency from. EUR rather than USD
        // because the currency list is derived from the catalogue, and this fixture's countries
        // quote in JPY, EUR, XCD and IDR.
        val resolved = resolve(country = "NZ", language = "en", currency = "EUR")
        assertEquals("NZ", resolved?.countryCode)
        assertEquals("EUR", resolved?.currencyCode)
    }

    @Test
    fun a_currency_this_app_cannot_offer_is_not_adopted() {
        // Leaves the setting alone rather than storing a code with no row behind it, which the
        // picker could then not show as selected.
        assertEquals(null, resolve(country = "NZ", language = "en", currency = "ZZZ")?.currencyCode)
    }

    @Test
    fun a_device_that_names_no_country_changes_nothing() {
        // A tablet with no SIM, no registration and a locale with no region.
        assertEquals(null, resolve(country = null, language = "en"))
        // And a malformed one is not half-applied.
        assertEquals(null, resolve(country = "GBR", language = "en"))
    }

    @Test
    fun a_chosen_region_only_ever_has_its_language_revisited() {
        // Someone who set their region by hand and then switched their phone to French is asking
        // for French — not to have their country and currency quietly moved with it.
        val chosen = AppSettingsState(
            countryCode = "JP",
            currencyCode = "JPY",
            languageCode = "ja",
            isRegionChosen = true,
        )
        val resolved = RegionDefaults.resolve(RegionHints("DE", "fr", "EUR"), chosen)
        assertEquals(null, resolved?.countryCode)
        assertEquals(null, resolved?.currencyCode)
        assertEquals("fr", resolved?.languageCode)
    }

    @Test
    fun a_chosen_region_whose_language_already_matches_is_left_alone() {
        val chosen = AppSettingsState(languageCode = "ja", isRegionChosen = true)
        assertEquals(null, RegionDefaults.resolve(RegionHints("JP", "ja", "JPY"), chosen))
    }

    @Test
    fun a_device_language_this_app_does_not_offer_never_overwrites_a_chosen_one() {
        val chosen = AppSettingsState(languageCode = "de", isRegionChosen = true)
        assertEquals(null, RegionDefaults.resolve(RegionHints("DE", "sw", "EUR"), chosen))
    }
}
