package com.gocheap.flights.ui.hotel

import com.gocheap.flights.data.model.StayRoom
import com.gocheap.flights.ui.hotel.guests.GuestSelection
import com.gocheap.flights.ui.hotel.guests.GuestSelectionViewModel
import com.gocheap.flights.ui.hotel.guests.decodeStayRooms
import com.gocheap.flights.ui.hotel.guests.encodeRooms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The party is what the stay is priced on — rooms, adults, and each child's age — so the rules the
 * page enforces are the supplier's, not decoration.
 */
class GuestSelectionViewModelTest {

    @Test
    fun it_opens_on_the_rooms_the_search_was_already_for() {
        val viewModel = GuestSelectionViewModel(
            listOf(StayRoom(adults = 2), StayRoom(adults = 1, childAges = listOf(6))),
        )
        val state = viewModel.state.value
        assertEquals(2, state.roomCount)
        assertEquals(listOf<Int?>(6), state.rooms[1].childAges)
        assertEquals(4, state.guestCount)
        assertEquals(2, state.roomCount)
    }

    @Test
    fun a_room_takes_four_guests_and_says_so_on_the_tap_that_would_be_the_fifth() {
        val viewModel = GuestSelectionViewModel(listOf(StayRoom(adults = 4)))
        viewModel.onIncreaseAdults(0)
        assertEquals(4, viewModel.state.value.rooms[0].adults)
        assertNotNull(viewModel.state.value.snackbarMessage)
    }

    @Test
    fun the_last_adult_cannot_be_removed_because_a_room_needs_one() {
        val viewModel = GuestSelectionViewModel(listOf(StayRoom(adults = 1)))
        viewModel.onDecreaseAdults(0)
        assertEquals(1, viewModel.state.value.rooms[0].adults)
        assertNotNull(viewModel.state.value.snackbarMessage)
    }

    @Test
    fun a_search_covers_at_most_eight_rooms() {
        val viewModel = GuestSelectionViewModel()
        repeat(GuestSelection.MaxRooms + 2) { viewModel.onAddRoom() }
        assertEquals(GuestSelection.MaxRooms, viewModel.state.value.roomCount)
    }

    @Test
    fun the_first_room_is_the_stay_itself_and_stays_put() {
        val viewModel = GuestSelectionViewModel()
        viewModel.onAddRoom()
        viewModel.onRemoveRoom(1)
        assertEquals(1, viewModel.state.value.roomCount)
        viewModel.onRemoveRoom(0)
        assertEquals(1, viewModel.state.value.roomCount)
    }

    @Test
    fun a_child_arrives_without_an_age_and_nothing_is_handed_back_until_it_has_one() {
        val viewModel = GuestSelectionViewModel()
        viewModel.onIncreaseChildren(0)
        assertEquals(listOf<Int?>(null), viewModel.state.value.rooms[0].childAges)

        var picked: GuestSelection? = null
        viewModel.onConfirm { picked = it }
        assertNull(picked)
        assertNotNull(viewModel.state.value.snackbarMessage)

        viewModel.onChildAgePicked(roomIndex = 0, childIndex = 0, age = 9)
        assertTrue(viewModel.state.value.isComplete)
        viewModel.onConfirm { picked = it }
        assertEquals(listOf(StayRoom(adults = 1, childAges = listOf(9))), picked?.rooms)
    }

    @Test
    fun removing_a_child_takes_the_age_picked_for_them_with_it() {
        val viewModel = GuestSelectionViewModel()
        viewModel.onIncreaseChildren(0)
        viewModel.onChildAgePicked(roomIndex = 0, childIndex = 0, age = 4)
        viewModel.onDecreaseChildren(0)
        assertEquals(emptyList<Int?>(), viewModel.state.value.rooms[0].childAges)
        assertTrue(viewModel.state.value.isComplete)
    }

    @Test
    fun rooms_survive_the_trip_through_a_route_argument() {
        val rooms = listOf(
            StayRoom(adults = 2),
            StayRoom(adults = 1, childAges = listOf(0, 17)),
        )
        val encoded = rooms.encodeRooms()
        // Nothing a route path would have to escape.
        assertEquals("2_1-0-17", encoded)
        assertEquals(rooms, decodeStayRooms(encoded))
    }

    @Test
    fun an_unreadable_argument_opens_the_page_on_a_single_default_room() {
        assertEquals(listOf(StayRoom()), decodeStayRooms(null))
        assertEquals(listOf(StayRoom()), decodeStayRooms(""))
        assertEquals(listOf(StayRoom()), decodeStayRooms("two-adults"))
    }

    @Test
    fun a_party_from_outside_is_pulled_inside_the_limits_rather_than_trusted() {
        // More than a room holds, an age no child has, and more rooms than a search covers.
        val wild = List(12) { StayRoom(adults = 9, childAges = listOf(40, 3)) }
        val clamped = GuestSelection.of(wild)
        assertEquals(GuestSelection.MaxRooms, clamped.roomCount)
        assertTrue(clamped.rooms.all { it.guests <= GuestSelection.MaxGuestsPerRoom })
        assertTrue(
            clamped.rooms.flatMap { it.childAges }
                .all { it in GuestSelection.MinChildAge..GuestSelection.MaxChildAge },
        )
    }
}
