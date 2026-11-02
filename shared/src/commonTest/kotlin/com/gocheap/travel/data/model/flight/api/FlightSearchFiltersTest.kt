package com.gocheap.travel.data.model.flight.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The filter model, pinned on the two things about it that are decisions rather than mechanics:
 * what counts as "not filtered", and how the time windows become one query parameter.
 *
 * Both are silent when wrong. A window encoded into the wrong slot filters the return leg by the
 * outbound's hours and still returns a plausible-looking list of flights.
 */
class FlightSearchFiltersTest {

    @Test
    fun a_fresh_filter_set_narrows_nothing() {
        assertTrue(FlightSearchFilters.Unfiltered.isUnfiltered)
        assertEquals(0, FlightSearchFilters.Unfiltered.activeCount)
    }

    @Test
    fun sorting_is_not_filtering() {
        // A result set sorted by price has not been narrowed, and badging the bar would claim a
        // filter the user never set.
        val sorted = FlightSearchFilters(sort = FlightSort.Cheapest)
        assertTrue(sorted.isUnfiltered)
        assertEquals(0, sorted.activeCount)
    }

    @Test
    fun the_active_count_counts_groups_rather_than_values() {
        val filters = FlightSearchFilters(
            airlinesInclude = setOf("BA", "AF", "LH", "KL"),
            stopCounts = setOf(0),
        )
        // Four airlines and one stop count is two active filters, not five.
        assertEquals(2, filters.activeCount)
        assertFalse(filters.isUnfiltered)
    }

    @Test
    fun a_full_day_window_counts_as_no_filter_at_all() {
        val filters = FlightSearchFilters(timeRanges = listOf(LegTimeWindow(), LegTimeWindow()))
        assertTrue(filters.isUnfiltered)
    }

    @Test
    fun an_inverted_window_is_treated_as_unrestricted_rather_than_as_matching_nothing() {
        // Only reachable from a slider dragged past itself. Sending it would empty the results
        // with no filter visibly set, which reads as a broken search rather than a narrow one.
        val inverted = TimeWindow(min = 60_000, max = 1_000)
        assertTrue(inverted.isFullDay)
        assertNull(listOf(LegTimeWindow(departure = inverted)).departureRangesQuery())
    }

    @Test
    fun a_restricted_window_encodes_as_a_second_range() {
        val morning = TimeWindow(min = 21_600, max = 43_200)
        val query = listOf(LegTimeWindow(departure = morning)).departureRangesQuery()
        assertEquals("21600-43200", query)
    }

    @Test
    fun an_unrestricted_leg_before_a_restricted_one_keeps_its_slot() {
        // The positions are what tie a window to a leg. Dropping the outbound's empty slot would
        // silently apply the return's hours to the outbound.
        val evening = TimeWindow(min = 64_800, max = 82_800)
        val query = listOf(
            LegTimeWindow(),
            LegTimeWindow(departure = evening),
        ).departureRangesQuery()
        assertEquals(",64800-82800", query)
    }

    @Test
    fun trailing_unrestricted_legs_are_dropped() {
        val morning = TimeWindow(min = 21_600, max = 43_200)
        val query = listOf(
            LegTimeWindow(departure = morning),
            LegTimeWindow(),
        ).departureRangesQuery()
        assertEquals("21600-43200", query)
    }

    @Test
    fun departures_and_arrivals_are_encoded_independently() {
        val morning = TimeWindow(min = 21_600, max = 43_200)
        val evening = TimeWindow(min = 64_800, max = 82_800)
        val windows = listOf(LegTimeWindow(departure = morning, arrival = evening))

        assertEquals("21600-43200", windows.departureRangesQuery())
        assertEquals("64800-82800", windows.arrivalRangesQuery())
    }

    @Test
    fun a_window_is_clamped_to_the_day_it_describes() {
        assertEquals("0-86400", TimeWindow(min = -500, max = 90_000).asQueryRange())
    }

    @Test
    fun the_six_groups_are_the_reference_apps_six() {
        // STOPS, TIME, DURATION, PRICE, AIRLINES, AGENCIES — no more, no fewer.
        assertEquals(6, FlightFilterType.entries.size)
    }

    @Test
    fun the_three_sorts_are_the_reference_apps_three() {
        assertEquals(3, FlightSort.entries.size)
        // Best sends no sort at all, which is the order the endpoint returns anyway.
        assertNull(FlightSort.Best.sortBy)
        assertNull(FlightSort.Best.sortOrder)
        assertEquals("price", FlightSort.Cheapest.sortBy)
        assertEquals("duration", FlightSort.Fastest.sortBy)
        // Always ascending: nobody sorts a fare list most-expensive-first.
        assertEquals("asc", FlightSort.Cheapest.sortOrder)
        assertEquals("asc", FlightSort.Fastest.sortOrder)
    }

    @Test
    fun each_group_reports_its_own_chip_independently() {
        val filters = FlightSearchFilters(stopCounts = setOf(0), priceMax = 400.0)
        assertTrue(filters.isActive(FlightFilterType.Stops))
        assertTrue(filters.isActive(FlightFilterType.Price))
        assertFalse(filters.isActive(FlightFilterType.Airlines))
        assertFalse(filters.isActive(FlightFilterType.Duration))
    }

    @Test
    fun clearing_one_chip_leaves_the_other_five_alone() {
        val filters = FlightSearchFilters(
            stopCounts = setOf(0),
            airlinesInclude = setOf("BA"),
            priceMin = 100.0,
            priceMax = 400.0,
        )
        val cleared = filters.cleared(FlightFilterType.Price)

        assertFalse(cleared.isActive(FlightFilterType.Price))
        assertTrue(cleared.isActive(FlightFilterType.Stops))
        assertTrue(cleared.isActive(FlightFilterType.Airlines))
    }

    @Test
    fun clearing_a_chip_does_not_reorder_the_list() {
        // The sort is not one of the six groups, and a filter being removed should not also
        // reshuffle the results under the user's thumb.
        val filters = FlightSearchFilters(sort = FlightSort.Cheapest, stopCounts = setOf(0))
        assertEquals(FlightSort.Cheapest, filters.cleared(FlightFilterType.Stops).sort)
    }

    @Test
    fun the_active_count_and_the_chips_cannot_disagree() {
        val filters = FlightSearchFilters(
            stopCounts = setOf(0, 1),
            durationMax = 600,
            agenciesInclude = setOf("EXP"),
        )
        assertEquals(
            FlightFilterType.entries.count(filters::isActive),
            filters.activeCount,
        )
        assertEquals(3, filters.activeCount)
    }
}
