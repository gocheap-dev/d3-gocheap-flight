package com.gocheap.travel.ui.home

import com.gocheap.travel.data.model.CabinClass
import com.gocheap.travel.ui.home.travellers.TravellerSelection
import com.gocheap.travel.ui.home.travellers.TravellerSelectionViewModel
import com.gocheap.travel.ui.home.travellers.decodeTravellerSelection
import com.gocheap.travel.ui.home.travellers.encodeTravellers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The party is faired three ways and seated in one of four cabins, and the limits are the
 * airline's: eight adults, fifteen travellers, seven children and infants between them, and one
 * lap per infant.
 */
class TravellerSelectionViewModelTest {

    @Test
    fun it_opens_on_the_party_the_search_was_already_for() {
        val viewModel = TravellerSelectionViewModel(
            TravellerSelection(adults = 2, children = 1, infants = 1, cabinClass = CabinClass.Business),
        )
        val selection = viewModel.state.value.selection
        assertEquals(4, selection.count)
        assertEquals(CabinClass.Business, selection.cabinClass)
        assertEquals(4, viewModel.state.value.selection.count)
        assertEquals(CabinClass.Business, viewModel.state.value.selection.cabinClass)
    }

    @Test
    fun a_booking_carries_eight_adults_and_says_so_on_the_ninth() {
        val viewModel = TravellerSelectionViewModel()
        repeat(TravellerSelection.MaxAdults + 2) { viewModel.onIncreaseAdults() }
        assertEquals(TravellerSelection.MaxAdults, viewModel.state.value.selection.adults)
        assertNotNull(viewModel.state.value.snackbarMessage)
    }

    @Test
    fun the_last_adult_cannot_be_removed_because_a_booking_needs_one() {
        val viewModel = TravellerSelectionViewModel()
        viewModel.onDecreaseAdults()
        assertEquals(1, viewModel.state.value.selection.adults)
        assertNotNull(viewModel.state.value.snackbarMessage)
    }

    @Test
    fun children_and_infants_share_one_ceiling_between_them() {
        val viewModel = TravellerSelectionViewModel(TravellerSelection(adults = 8))
        repeat(TravellerSelection.MaxChildrenAndInfants + 3) { viewModel.onIncreaseChildren() }
        val selection = viewModel.state.value.selection
        assertEquals(TravellerSelection.MaxChildrenAndInfants, selection.children)
        // And with the ceiling reached by children alone, no infant fits beside them.
        viewModel.onIncreaseInfants()
        assertEquals(0, viewModel.state.value.selection.infants)
    }

    @Test
    fun an_infant_needs_a_lap_so_there_is_never_more_than_one_per_adult() {
        val viewModel = TravellerSelectionViewModel()
        viewModel.onIncreaseInfants()
        assertEquals(1, viewModel.state.value.selection.infants)

        viewModel.onIncreaseInfants()
        assertEquals(1, viewModel.state.value.selection.infants)
        assertNotNull(viewModel.state.value.snackbarMessage)
    }

    @Test
    fun taking_an_adult_away_takes_the_infant_on_their_lap_with_them() {
        val viewModel = TravellerSelectionViewModel(TravellerSelection(adults = 2, infants = 2))
        viewModel.onDecreaseAdults()
        val selection = viewModel.state.value.selection
        assertEquals(1, selection.adults)
        assertEquals(1, selection.infants)
    }

    @Test
    fun a_booking_carries_fifteen_travellers_in_total() {
        val viewModel = TravellerSelectionViewModel()
        repeat(20) { viewModel.onIncreaseAdults() }
        repeat(20) { viewModel.onIncreaseChildren() }
        repeat(20) { viewModel.onIncreaseInfants() }
        val selection = viewModel.state.value.selection
        assertTrue(selection.count <= TravellerSelection.MaxTravellers, "${selection.count}")
        assertTrue(selection.infants <= selection.adults)
    }

    @Test
    fun the_cabin_is_part_of_what_is_handed_back() {
        val viewModel = TravellerSelectionViewModel()
        viewModel.onCabinClassPicked(CabinClass.First)
        var picked: TravellerSelection? = null
        viewModel.onConfirm { picked = it }
        assertEquals(CabinClass.First, picked?.cabinClass)
        assertEquals(1, picked?.count)
    }

    @Test
    fun the_party_survives_the_trip_through_a_route_argument() {
        val party = TravellerSelection(
            adults = 2,
            children = 1,
            infants = 1,
            cabinClass = CabinClass.PremiumEconomy,
        )
        val encoded = party.encodeTravellers()
        // Nothing a route path would have to escape.
        assertEquals("2-1-1-PremiumEconomy", encoded)
        assertEquals(party, decodeTravellerSelection(encoded))
    }

    @Test
    fun an_unreadable_argument_opens_the_page_on_one_adult_in_economy() {
        val fallback = TravellerSelection()
        assertEquals(fallback, decodeTravellerSelection(null))
        assertEquals(fallback, decodeTravellerSelection(""))
        assertEquals(fallback, decodeTravellerSelection("two-adults"))
        assertEquals(fallback, decodeTravellerSelection("1-0-0"))
    }

    @Test
    fun a_party_from_outside_is_pulled_inside_the_limits_rather_than_trusted() {
        val clamped = TravellerSelection.of(adults = 40, children = 40, infants = 40)
        assertEquals(TravellerSelection.MaxAdults, clamped.adults)
        assertEquals(TravellerSelection.MaxChildrenAndInfants, clamped.children)
        // Children already fill the shared ceiling, so no infant fits beside them.
        assertEquals(0, clamped.infants)
    }
}
