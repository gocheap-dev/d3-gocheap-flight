package com.gocheap.flights.data.flight

import com.gocheap.flights.data.model.Airport
import com.gocheap.flights.data.model.CalendarDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The card opens on the trip the user last searched for, or on nothing at all — never on a route
 * this app picked for them.
 */
class FlightDefaultSearchTest {

    private val repository = FlightRepository()

    @Test
    fun it_opens_with_neither_end_filled_in() = runTest {
        val search = repository.defaultSearch()
        assertEquals(Airport.Unset, search.from)
        assertEquals(Airport.Unset, search.to)
        assertFalse(search.from.isChosen)
        assertFalse(search.to.isChosen)
    }

    @Test
    fun the_dates_are_still_offsets_from_today_rather_than_literals() = runTest {
        val search = repository.defaultSearch()
        val departure = assertNotNull(CalendarDate.parse(search.departureDate))
        val returning = assertNotNull(CalendarDate.parse(search.returnDate))
        assertTrue(departure > CalendarDate.today(), search.departureDate)
        assertTrue(returning > departure, "${search.departureDate} -> ${search.returnDate}")
    }

    @Test
    fun a_picked_airport_is_chosen_and_a_blank_one_is_not() {
        assertTrue(Airport(code = "LON", city = "London", name = "All airports").isChosen)
        assertFalse(Airport(code = "", city = "London", name = "").isChosen)
    }
}
