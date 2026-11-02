package com.gocheap.travel.data.hotel

import com.gocheap.travel.data.model.HotelSearch
import com.gocheap.travel.data.model.StayRoom
import com.gocheap.travel.data.model.hotel.HotelDestination
import com.gocheap.travel.data.model.hotel.HotelDestinationType
import com.gocheap.travel.data.network.hotel.StayDeeplink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The deeplink *is* the search — there is no results API behind it — so a wrong parameter name
 * silently returns the wrong place rather than failing. These pin it to the reference app's
 * `generateUri`.
 */
class StayDeeplinkTest {

    private val sanFrancisco = HotelDestination(
        id = "61403-13852-San Francisco",
        name = "San Francisco",
        location = "California, United States",
        type = HotelDestinationType.City,
        supplierId = "61403",
        cityId = "13852",
        idType = "place_id",
    )

    private fun search(
        destination: HotelDestination? = sanFrancisco,
        checkIn: String = "19, dec 2022",
        checkOut: String = "22, dec 2022",
        rooms: List<StayRoom> = listOf(StayRoom(adults = 2)),
    ) = HotelSearch(
        city = destination?.name ?: "Paris",
        checkInDate = checkIn,
        checkOutDate = checkOut,
        nights = 3,
        rooms = rooms,
        destination = destination,
    )

    /** Parses the query string back out, so assertions name parameters rather than offsets. */
    private fun params(url: String): Map<String, String> =
        url.substringAfter("?").split("&").filter(String::isNotEmpty).associate { pair ->
            val (name, value) = pair.split("=", limit = 2)
            name to value
        }

    @Test
    fun it_points_at_the_suppliers_deeplink_page() {
        assertTrue(
            StayDeeplink.forSearch(search())
                .startsWith("https://staging.hotel.lascade.com/deeplink/new/?"),
            StayDeeplink.forSearch(search()),
        )
    }

    @Test
    fun dates_are_sent_as_iso_not_as_the_label_the_card_shows() {
        val sent = params(StayDeeplink.forSearch(search()))
        assertEquals("2022-12-19", sent["checkin"])
        assertEquals("2022-12-22", sent["checkout"])
    }

    @Test
    fun a_place_id_row_is_sent_as_place_id() {
        val sent = params(StayDeeplink.forSearch(search()))
        assertEquals("61403", sent["place_id"])
        assertEquals("13852", sent["city_id"])
        assertFalse("entity_id" in sent)
    }

    @Test
    fun any_other_id_type_is_sent_as_entity_id() {
        val entity = sanFrancisco.copy(idType = "entity", supplierId = "99")
        val sent = params(StayDeeplink.forSearch(search(destination = entity)))
        assertEquals("99", sent["entity_id"])
        assertFalse("place_id" in sent)
    }

    @Test
    fun a_hotel_row_also_sends_its_hotel_id() {
        val hotel = sanFrancisco.copy(
            name = "San Francisco Marriott Union Square",
            type = HotelDestinationType.Hotel,
            hotelId = "185687",
        )
        assertEquals("185687", params(StayDeeplink.forSearch(search(destination = hotel)))["hotel_id"])
    }

    @Test
    fun city_and_country_names_are_split_out_of_the_location_line() {
        val sent = params(StayDeeplink.forSearch(search()))
        // "California, United States" -> city "California", country "United States".
        assertEquals("California", sent["city_name"])
        assertEquals("United%20States", sent["country_name"])
    }

    @Test
    fun a_pick_with_no_supplier_ids_still_searches_by_name() {
        // The popular-locations row carries a name and nothing else.
        val sent = params(StayDeeplink.forSearch(search(destination = null)))
        assertEquals("Paris", sent["city_name"])
        assertFalse("place_id" in sent)
        assertFalse("entity_id" in sent)
        assertFalse("hotel_id" in sent)
    }

    @Test
    fun guests_and_rooms_go_out_as_adults_and_rooms() {
        val sent = params(
            StayDeeplink.forSearch(
                search(rooms = listOf(StayRoom(adults = 2), StayRoom(adults = 2))),
            ),
        )
        assertEquals("4", sent["adults"])
        assertEquals("2", sent["rooms"])
    }

    @Test
    fun children_are_sent_as_their_ages_rather_than_counted_as_adults() {
        val sent = params(
            StayDeeplink.forSearch(
                search(
                    rooms = listOf(
                        StayRoom(adults = 2, childAges = listOf(5)),
                        StayRoom(adults = 1, childAges = listOf(11, 0)),
                    ),
                ),
            ),
        )
        assertEquals("3", sent["adults"])
        assertEquals("2", sent["rooms"])
        // Flattened across the rooms, in the order they were entered.
        assertEquals("5%2C11%2C0", sent["children_ages"])
    }

    @Test
    fun a_party_of_adults_leaves_the_child_ages_off_entirely() {
        assertFalse("children_ages" in params(StayDeeplink.forSearch(search())))
    }

    @Test
    fun a_same_day_checkout_is_pushed_to_the_next_day() {
        // The supplier rejects a zero-night stay, so the range is repaired rather than sent as-is.
        val sent = params(
            StayDeeplink.forSearch(search(checkIn = "19, dec 2022", checkOut = "19, dec 2022")),
        )
        assertEquals("2022-12-19", sent["checkin"])
        assertEquals("2022-12-20", sent["checkout"])
    }

    @Test
    fun the_fixed_identifying_parameters_are_always_present() {
        val sent = params(StayDeeplink.forSearch(search()))
        assertEquals("D3AT", sent["app_code"])
        assertEquals("organic", sent["route"])
        assertEquals("US", sent["country"])
        assertEquals("USD", sent["currency"])
        assertEquals("en", sent["language"])
        assertEquals("fallback_d3at", sent["user_id"])
    }
}
