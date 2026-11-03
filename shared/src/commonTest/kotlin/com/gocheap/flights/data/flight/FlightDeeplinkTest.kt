package com.gocheap.flights.data.flight

import com.gocheap.flights.data.model.Airport
import com.gocheap.flights.data.model.CabinClass
import com.gocheap.flights.data.model.TripSearch
import com.gocheap.flights.data.network.flight.FlightDeeplink
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The flight handoff URL, pinned against the reference app's `generateFlightUrl`.
 *
 * Worth its own test for the same reason the other two builders have one, plus a reason they do
 * not: the legs ride in a JSON array inside a query parameter, so a mistake here is a malformed
 * document rather than a wrong value, and the supplier's page answers it with an empty search.
 */
class FlightDeeplinkTest {

    private fun search(
        from: String = "LON",
        to: String = "NYC",
        departure: String = "19, dec 2022",
        returning: String? = "22, dec 2022",
        roundTrip: Boolean = true,
        adults: Int = 1,
        children: Int = 0,
        infants: Int = 0,
        cabin: CabinClass = CabinClass.Economy,
    ) = TripSearch(
        from = Airport(code = from, city = from, name = from),
        to = Airport(code = to, city = to, name = to),
        departureDate = departure,
        returnDate = returning,
        isRoundTrip = roundTrip,
        adults = adults,
        children = children,
        infants = infants,
        cabinClass = cabin,
    )

    private fun params(url: String): Map<String, String> =
        Url(url).parameters.entries().associate { it.key to it.value.first() }

    private fun legs(url: String) =
        Json.parseToJsonElement(params(url).getValue("legs")).jsonArray

    @Test
    fun it_points_at_the_flight_suppliers_deeplink_page() {
        assertTrue(
            FlightDeeplink.forSearch(search())
                .startsWith("https://staging.plane.lascade.com/deeplink-v2/new/?"),
            FlightDeeplink.forSearch(search()),
        )
    }

    @Test
    fun a_round_trip_sends_the_return_as_a_second_leg() {
        val sent = legs(FlightDeeplink.forSearch(search()))

        assertEquals(2, sent.size)
        assertEquals("LON", sent[0].jsonObject["origin"]?.jsonPrimitive?.content)
        assertEquals("NYC", sent[0].jsonObject["destination"]?.jsonPrimitive?.content)
        assertEquals("2022-12-19", sent[0].jsonObject["date"]?.jsonPrimitive?.content)
        // The way back is the same two airports in the other order, not a return-date field.
        assertEquals("NYC", sent[1].jsonObject["origin"]?.jsonPrimitive?.content)
        assertEquals("LON", sent[1].jsonObject["destination"]?.jsonPrimitive?.content)
        assertEquals("2022-12-22", sent[1].jsonObject["date"]?.jsonPrimitive?.content)
    }

    @Test
    fun a_one_way_sends_a_single_leg() {
        // A return date left on the search but the toggle switched off is still a one-way: the
        // card keeps the date so turning the toggle back on does not lose it.
        val sent = legs(FlightDeeplink.forSearch(search(roundTrip = false)))

        assertEquals(1, sent.size)
    }

    @Test
    fun a_return_on_or_before_the_outbound_is_not_sent_as_a_leg() {
        val sent = legs(FlightDeeplink.forSearch(search(returning = "19, dec 2022")))

        assertEquals(1, sent.size)
    }

    @Test
    fun children_and_infants_are_sent_as_ages_rather_than_counted_as_adults() {
        val sent = params(FlightDeeplink.forSearch(search(children = 2, infants = 1)))

        // The reference's placeholder ages: 9 per child, 1 per infant, in that order.
        assertEquals("9,9,1", sent["children_ages"])
    }

    @Test
    fun a_party_of_adults_leaves_the_child_ages_off_entirely() {
        assertFalse("children_ages" in params(FlightDeeplink.forSearch(search())))
    }

    @Test
    fun the_cabin_is_named_the_way_the_page_reads_it() {
        val sent = params(FlightDeeplink.forSearch(search(cabin = CabinClass.PremiumEconomy)))

        // Lowercase and unseparated here, unlike the session body's "premiumEconomy".
        assertEquals("premiumeconomy", sent["cabin_class"])
    }

    @Test
    fun the_fixed_identifying_parameters_are_always_present() {
        val sent = params(FlightDeeplink.forSearch(search()))

        assertEquals("D3AT", sent["app_code"])
        assertEquals("organic", sent["route"])
        assertEquals("US", sent["country"])
        // Lowercase currency against an uppercase country, as the reference sends them.
        assertEquals("usd", sent["currency"])
        assertEquals("en", sent["language"])
        assertEquals("fallback_d3at", sent["user_id"])
        assertEquals("1", sent["adults"])
    }
}
