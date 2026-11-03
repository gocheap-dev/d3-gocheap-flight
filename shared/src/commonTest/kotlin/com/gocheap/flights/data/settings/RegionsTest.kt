package com.gocheap.flights.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the bundled region catalogue is read.
 *
 * Tested against the file's real shapes rather than a tidy sample: a third of its rows carry no
 * flag, most carry no `domain`, and `supported_languages` is `null` on some rows and `[]` on
 * others for the same meaning. Each of those used to be a reason for a row to go missing.
 */
class RegionsTest {

    private val sample = """
        [
          {
            "name": "Anguilla", "code": "AI",
            "currency": {"code": "XCD", "symbol": "${'$'}", "decimal_digits": 2},
            "flag": "🇦🇮", "domain": null, "supported_languages": null
          },
          {
            "name": "United Arab Emirates", "code": "AE",
            "currency": {"code": "AED", "symbol": "AED", "decimal_digits": 2},
            "flag": "🇦🇪",
            "domain": "https://www.kayak.ae/", "supported_languages": ["en"]
          },
          {
            "name": "American Samoa", "code": "AS",
            "currency": {"code": "USD", "symbol": "${'$'}", "decimal_digits": 2},
            "flag": "", "domain": null, "supported_languages": null
          },
          {
            "name": "Antarctica", "code": "AQ",
            "currency": {"code": "USD", "symbol": "${'$'}", "decimal_digits": 2},
            "flag": "🇦🇶", "domain": null, "supported_languages": []
          },
          {
            "name": null, "code": "ZZ",
            "currency": {"code": "ZZZ", "symbol": "?"}, "flag": "", "supported_languages": null
          }
        ]
    """.trimIndent()

    @Test
    fun a_row_that_cannot_name_a_country_is_dropped() {
        // No name means nothing to show in a picker and nothing to send — the row is no use.
        val (countries, _) = Regions.parse(sample)
        assertEquals(4, countries.size)
        assertTrue(countries.none { it.code == "ZZ" })
    }

    @Test
    fun countries_come_back_alphabetically() {
        val (countries, _) = Regions.parse(sample)
        assertEquals(
            listOf("American Samoa", "Anguilla", "Antarctica", "United Arab Emirates"),
            countries.map { it.name },
        )
    }

    @Test
    fun a_row_with_no_flag_is_kept() {
        // About a sixth of the file has none; dropping them would lose American Samoa, Türkiye,
        // South Korea and Russia among others.
        val (countries, _) = Regions.parse(sample)
        val samoa = countries.single { it.code == "AS" }
        assertEquals("", samoa.flag)
        assertEquals("USD", samoa.currencyCode)
    }

    @Test
    fun null_and_empty_supported_languages_mean_the_same_thing() {
        val (countries, _) = Regions.parse(sample)
        assertTrue(countries.single { it.code == "AI" }.supportedLanguages.isEmpty())
        assertTrue(countries.single { it.code == "AQ" }.supportedLanguages.isEmpty())
        assertEquals(listOf("en"), countries.single { it.code == "AE" }.supportedLanguages)
    }

    @Test
    fun currencies_are_the_distinct_ones_the_countries_quote_in() {
        // Two rows price in USD; it appears once.
        val (_, currencies) = Regions.parse(sample)
        assertEquals(listOf("AED", "USD", "XCD"), currencies.map { it.code })
    }

    @Test
    fun a_currency_whose_symbol_is_its_own_code_falls_back_to_a_known_one() {
        // The catalogue gives "AED" as AED's symbol. Where this app knows a real one, it wins.
        val (_, currencies) = Regions.parse(sample)
        assertEquals("د.إ", currencies.single { it.code == "AED" }.symbol)
    }

    @Test
    fun a_currency_with_no_confirmed_name_is_titled_by_its_code() {
        val (_, currencies) = Regions.parse(sample)
        val xcd = currencies.single { it.code == "XCD" }
        assertEquals("XCD", xcd.name)
        assertEquals("$", xcd.symbol)
        // And one that does have a confirmed name keeps it.
        assertEquals("UAE dirham", currencies.single { it.code == "AED" }.name)
    }

    @Test
    fun the_language_list_is_the_reference_apps_with_modern_codes() {
        assertEquals(29, Regions.languages.size)
        // Java's legacy "in"/"iw" are stored as the modern codes but still sent as the same locale.
        assertTrue(Regions.languages.none { it.code == "in" || it.code == "iw" })
        assertEquals("id-ID", Regions.language("id")?.apiCode)
        assertEquals("he-IL", Regions.language("he")?.apiCode)
        assertEquals("en-US", Regions.language("en")?.apiCode)
    }
}
