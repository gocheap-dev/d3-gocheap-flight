package com.gocheap.travel.ui.home.results.api

import com.gocheap.travel.data.model.flight.api.FlightDeal
import com.gocheap.travel.data.model.flight.api.FlightOption
import com.gocheap.travel.data.model.flight.api.FlightProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which offers an itinerary can actually be booked through.
 *
 * Pinned because the results list and the deals sheet both read it and must agree: the list uses
 * the count to decide whether to open the sheet at all, so a disagreement would mean a sheet that
 * opens empty, or — worse — a tap that silently does nothing because the list thought there was
 * exactly one offer and the sheet thought there were none.
 */
class FlightOffersTest {

    private fun deal(name: String, price: Double?, link: String? = "https://book.example/$name") =
        FlightDeal(name = name, price = price, deeplink = link)

    @Test
    fun offers_are_flattened_across_providers_and_sorted_by_price() {
        val option = FlightOption(
            providers = listOf(
                FlightProvider(price = 300.0, deals = listOf(deal("Expensive", 300.0))),
                FlightProvider(price = 100.0, deals = listOf(deal("Cheap", 100.0))),
                FlightProvider(price = 200.0, deals = listOf(deal("Middle", 200.0))),
            ),
        )
        assertEquals(
            listOf("Cheap", "Middle", "Expensive"),
            option.bookableOffers().map { it.deal.name },
        )
    }

    @Test
    fun a_deal_without_a_link_is_not_bookable() {
        // A row that goes nowhere is worse than no row — and counting it would make a
        // one-real-offer itinerary open a sheet instead of the checkout.
        val option = FlightOption(
            providers = listOf(
                FlightProvider(deals = listOf(deal("Linkless", 90.0, link = null))),
                FlightProvider(deals = listOf(deal("Blank", 95.0, link = "  "))),
                FlightProvider(deals = listOf(deal("Real", 100.0))),
            ),
        )
        val offers = option.bookableOffers()
        assertEquals(1, offers.size)
        assertEquals("Real", offers.single().deal.name)
    }

    @Test
    fun exactly_one_bookable_offer_is_what_lets_the_list_skip_the_sheet() {
        val option = FlightOption(
            providers = listOf(
                FlightProvider(deals = listOf(deal("Only", 100.0))),
                FlightProvider(deals = listOf(deal("Unbookable", 80.0, link = null))),
            ),
        )
        // `singleOrNull` is the decision the results screen makes; this is that call.
        assertNotNull(option.bookableOffers().singleOrNull())
    }

    @Test
    fun two_offers_still_need_the_sheet() {
        val option = FlightOption(
            providers = listOf(
                FlightProvider(deals = listOf(deal("One", 100.0))),
                FlightProvider(deals = listOf(deal("Two", 120.0))),
            ),
        )
        assertEquals(2, option.bookableOffers().size)
        assertNull(option.bookableOffers().singleOrNull())
    }

    @Test
    fun one_provider_selling_several_fares_is_several_offers() {
        // A split provider carries one deal per ticket, so the two levels really do flatten.
        val option = FlightOption(
            providers = listOf(
                FlightProvider(
                    isSplit = true,
                    deals = listOf(deal("Outbound", 60.0), deal("Return", 70.0)),
                ),
            ),
        )
        val offers = option.bookableOffers()
        assertEquals(2, offers.size)
        // The split flag lives on the provider, so it has to survive the flattening onto each row.
        assertTrue(offers.all { it.isSplit })
    }

    @Test
    fun the_split_flag_is_per_provider_not_per_itinerary() {
        val option = FlightOption(
            providers = listOf(
                FlightProvider(isSplit = true, deals = listOf(deal("Split", 100.0))),
                FlightProvider(isSplit = false, deals = listOf(deal("Whole", 110.0))),
            ),
        )
        val offers = option.bookableOffers()
        assertTrue(offers.first { it.deal.name == "Split" }.isSplit)
        assertTrue(!offers.first { it.deal.name == "Whole" }.isSplit)
    }

    @Test
    fun an_itinerary_nobody_sells_has_no_offers() {
        // Opens the sheet rather than the checkout, so the "no booking sites" message is reachable.
        assertEquals(0, FlightOption(providers = null).bookableOffers().size)
        assertNull(FlightOption(providers = emptyList()).bookableOffers().singleOrNull())
    }

    @Test
    fun a_priceless_deal_sorts_last_rather_than_first() {
        // Null price must not read as free and jump the cheapest row.
        val option = FlightOption(
            providers = listOf(
                FlightProvider(deals = listOf(deal("Unknown", null))),
                FlightProvider(deals = listOf(deal("Cheapest", 50.0))),
            ),
        )
        assertEquals("Cheapest", option.bookableOffers().first().deal.name)
    }
}
