package com.gocheap.flights.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarDateTest {

    @Test
    fun label_matches_the_format_the_app_already_stores() {
        assertEquals("19, dec 2022", CalendarDate(2022, 12, 19).label)
        assertEquals("1, jan 2023", CalendarDate(2023, 1, 1).label)
    }

    @Test
    fun iso_date_is_zero_padded_for_query_strings() {
        assertEquals("2022-12-19", CalendarDate(2022, 12, 19).isoDate)
        assertEquals("2023-01-01", CalendarDate(2023, 1, 1).isoDate)
        assertEquals("2026-09-05", CalendarDate(2026, 9, 5).isoDate)
    }

    @Test
    fun today_is_a_real_date_from_the_platform_clock() {
        val today = CalendarDate.today()
        // Loose on purpose: the point is that a clock is wired up at all and converts sanely. A
        // tight assertion here would just be re-implementing the conversion in the test.
        assertTrue(today.year >= 2025, "suspicious year: ${today.year}")
        assertTrue(today.month in 1..12, "bad month: ${today.month}")
        assertTrue(today.day in 1..31, "bad day: ${today.day}")
        // Round-tripping through the epoch proves the fields are internally consistent.
        assertEquals(today, CalendarDate.fromEpochDay(today.toEpochDay()))
    }

    @Test
    fun parse_round_trips_its_own_label() {
        val dates = listOf(
            CalendarDate(2022, 12, 19),
            CalendarDate(2023, 1, 1),
            CalendarDate(2024, 2, 29),
        )
        dates.forEach { assertEquals(it, CalendarDate.parse(it.label)) }
    }

    @Test
    fun parse_rejects_anything_that_is_not_that_shape() {
        listOf(null, "", "   ", "dec 2022", "19 dec 2022", "19, xxx 2022", "32, jan 2022", "29, feb 2023")
            .forEach { assertNull(CalendarDate.parse(it), "expected null for $it") }
    }

    @Test
    fun day_of_week_is_correct() {
        // 1 Dec 2022 was a Thursday, 19 Dec 2022 a Monday, 1 Jan 2023 a Sunday.
        assertEquals(4, CalendarDate(2022, 12, 1).dayOfWeek)
        assertEquals(1, CalendarDate(2022, 12, 19).dayOfWeek)
        assertEquals(0, CalendarDate(2023, 1, 1).dayOfWeek)
        // Leap day 2024 was a Thursday.
        assertEquals(4, CalendarDate(2024, 2, 29).dayOfWeek)
    }

    @Test
    fun plus_days_crosses_month_and_year_boundaries() {
        assertEquals(CalendarDate(2023, 1, 1), CalendarDate(2022, 12, 31).plusDays(1))
        assertEquals(CalendarDate(2022, 12, 26), CalendarDate(2022, 12, 19).plusDays(7))
        assertEquals(CalendarDate(2024, 3, 1), CalendarDate(2024, 2, 28).plusDays(2))
        assertEquals(CalendarDate(2023, 3, 1), CalendarDate(2023, 2, 28).plusDays(1))
        assertEquals(CalendarDate(2022, 11, 30), CalendarDate(2022, 12, 1).plusDays(-1))
    }

    @Test
    fun plus_months_clamps_into_shorter_months() {
        assertEquals(CalendarDate(2023, 2, 28), CalendarDate(2023, 1, 31).plusMonths(1))
        assertEquals(CalendarDate(2024, 2, 29), CalendarDate(2024, 1, 31).plusMonths(1))
        assertEquals(CalendarDate(2023, 1, 15), CalendarDate(2022, 12, 15).plusMonths(1))
        assertEquals(CalendarDate(2022, 11, 15), CalendarDate(2022, 12, 15).plusMonths(-1))
        assertEquals(CalendarDate(2021, 12, 15), CalendarDate(2022, 12, 15).plusMonths(-12))
    }

    @Test
    fun days_until_counts_whole_days_and_signs_them() {
        assertEquals(3, CalendarDate(2022, 12, 19).daysUntil(CalendarDate(2022, 12, 22)))
        assertEquals(-3, CalendarDate(2022, 12, 22).daysUntil(CalendarDate(2022, 12, 19)))
        assertEquals(0, CalendarDate(2022, 12, 19).daysUntil(CalendarDate(2022, 12, 19)))
        // Across a leap year: 2024 has 366 days.
        assertEquals(366, CalendarDate(2024, 1, 1).daysUntil(CalendarDate(2025, 1, 1)))
        assertEquals(365, CalendarDate(2023, 1, 1).daysUntil(CalendarDate(2024, 1, 1)))
    }

    @Test
    fun epoch_day_round_trips_over_a_long_span() {
        // Every day across four years, covering a leap year in both directions.
        var date = CalendarDate(2022, 1, 1)
        repeat(4 * 366) {
            assertEquals(date, CalendarDate.fromEpochDay(date.toEpochDay()))
            val next = date.plusDays(1)
            assertEquals(1, date.daysUntil(next))
            assertTrue(next > date, "$next should sort after $date")
            date = next
        }
    }

    @Test
    fun comparison_orders_by_year_then_month_then_day() {
        assertTrue(CalendarDate(2022, 12, 19) < CalendarDate(2022, 12, 20))
        assertTrue(CalendarDate(2022, 12, 31) < CalendarDate(2023, 1, 1))
        assertTrue(CalendarDate(2023, 2, 1) > CalendarDate(2023, 1, 28))
    }

    @Test
    fun leap_years_follow_the_gregorian_rule() {
        assertTrue(CalendarDate.isLeapYear(2024))
        assertTrue(CalendarDate.isLeapYear(2000))
        assertTrue(!CalendarDate.isLeapYear(1900))
        assertTrue(!CalendarDate.isLeapYear(2023))
        assertEquals(29, CalendarDate.lengthOfMonth(2024, 2))
        assertEquals(28, CalendarDate.lengthOfMonth(2023, 2))
        assertEquals(31, CalendarDate.lengthOfMonth(2023, 12))
        assertEquals(30, CalendarDate.lengthOfMonth(2023, 11))
    }
}
