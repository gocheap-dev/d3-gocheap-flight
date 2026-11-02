package com.gocheap.travel.ui.home.results.api

import com.gocheap.travel.data.model.flight.api.CurrencyInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The results screen's formatting, pinned because it is all arithmetic done by hand.
 *
 * Common code has no locale-aware formatter and the backend has already said how this search's
 * prices are punctuated, so [formatPrice] and friends implement grouping, rounding and the civil
 * calendar from scratch. None of that fails loudly — a wrong separator or a day lost to an
 * off-by-one renders perfectly happily — which is exactly why it is tested.
 */
class FlightFormattingTest {

    private val usd = CurrencyInfo(
        code = "USD",
        symbol = "$",
        thousandsSeparator = ",",
        decimalSeparator = ".",
        symbolOnLeft = true,
        spaceBetweenAmountAndSymbol = false,
        decimalDigits = 2,
    )

    /** The other arrangement the backend sends: symbol trailing, separators swapped. */
    private val eur = CurrencyInfo(
        code = "EUR",
        symbol = "€",
        thousandsSeparator = " ",
        decimalSeparator = ",",
        symbolOnLeft = false,
        spaceBetweenAmountAndSymbol = true,
        decimalDigits = 2,
    )

    @Test
    fun a_price_is_written_the_way_the_backend_says_it_is() {
        assertEquals("$1,234.50", formatPrice(1234.5, usd))
        assertEquals("1 234,50 €", formatPrice(1234.5, eur))
    }

    @Test
    fun a_fraction_keeps_its_leading_zero() {
        // The bug this guards: 5 cents scaled to 5, rendered as ".5" rather than ".05".
        assertEquals("$10.05", formatPrice(10.05, usd))
    }

    @Test
    fun grouping_runs_from_the_right_whatever_the_digit_count() {
        assertEquals("$999.00", formatPrice(999.0, usd))
        assertEquals("$1,000.00", formatPrice(1000.0, usd))
        assertEquals("$1,234,567.00", formatPrice(1234567.0, usd))
    }

    @Test
    fun the_list_rows_price_drops_the_decimals_whatever_the_currency_asks_for() {
        // A column of fares is scanned, not read — ".00" forty times down the page is noise.
        assertEquals("$1,235", formatPriceCompact(1234.6, usd))
        assertEquals("1 235 €", formatPriceCompact(1234.6, eur))
    }

    @Test
    fun a_price_with_no_currency_still_reads_as_one() {
        // No symbol is guessed: stating the wrong currency is worse than stating none.
        assertEquals("1,234", formatPrice(1234.4, null))
        assertEquals("—", formatPrice(null, usd))
    }

    @Test
    fun a_date_is_taken_off_the_same_timestamp() {
        // 2024-03-14T08:25:00Z. The month is returned as a number — its name is a translated
        // resource now, so the part worth pinning here is the calendar, not the word.
        assertEquals(3 to 14, dayMonthParts(1_710_404_700L))
        assertEquals(1 to 1, dayMonthParts(0L))
        assertEquals(null, dayMonthParts(null))
    }

    @Test
    fun the_civil_calendar_handles_a_leap_day() {
        // 2024-02-29T00:00:00Z — the date a naive 365-day conversion loses.
        assertEquals(2 to 29, dayMonthParts(1_709_164_800L))
        // 2023-03-01T00:00:00Z, the year before, where there is no 29th to find.
        assertEquals(3 to 1, dayMonthParts(1_677_628_800L))
    }

    @Test
    fun a_time_window_ends_at_24_00_not_at_midnight() {
        // The bug this guards: a full-day window rendering as "00:00 – 00:00", which reads as a
        // window that excludes everything — the opposite of an untouched slider.
        assertEquals("00:00", formatTimeOfDay(0L))
        assertEquals("24:00", formatTimeOfDay(86_400L))
        assertEquals("08:00", formatTimeOfDay(28_800L))
        assertEquals("23:59", formatTimeOfDay(86_340L))
    }

    @Test
    fun a_window_bound_is_clamped_to_the_day() {
        assertEquals("00:00", formatTimeOfDay(-1L))
        assertEquals("24:00", formatTimeOfDay(90_000L))
    }

    @Test
    fun a_moment_in_time_still_wraps_at_midnight() {
        // formatClockTime renders an instant, where 86 400 really is the next day's 00:00 — the
        // two formatters differ on purpose and must not be collapsed into one.
        assertEquals("00:00", formatClockTime(86_400L))
    }
}
