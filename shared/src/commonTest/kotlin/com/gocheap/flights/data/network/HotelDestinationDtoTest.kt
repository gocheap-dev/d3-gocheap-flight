package com.gocheap.flights.data.network

import com.gocheap.flights.data.model.hotel.HotelDestinationDto
import com.gocheap.flights.data.model.hotel.HotelDestinationType
import com.gocheap.flights.data.model.hotel.toDestination
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pinned against a real response from
 * `staging.hotel.lascade.com/api/v4/autocomplete/?search=san fr&country=US&language=en&app=D1AF`,
 * trimmed to the rows that each prove something. Two of them share `id` 61403 — a city and a hotel —
 * which is the whole reason the picker does not key its list on that field.
 */
class HotelDestinationDtoTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val response = """
        [
          {"id":"61403","name":"San Francisco","location":"California, United States",
           "type":"city","parent_id":null,"id_type":"place_id","city_id":"13852",
           "hotel_id":null,"country_code":"US","image":"https://example.test/a.jpg"},
          {"id":"175748","name":"San Francisco International Airport",
           "location":"San Francisco, California, United States","type":"airport",
           "parent_id":null,"id_type":"place_id","city_id":"13852","hotel_id":null,
           "country_code":"US","image":null},
          {"id":"483412","name":"San Francisco Bay Area","location":"California, United States",
           "type":"touristregion","parent_id":null,"id_type":"place_id","city_id":null,
           "hotel_id":null,"country_code":"US","image":null},
          {"id":"61403","name":"San Francisco Marriott Union Square",
           "location":"San Francisco, California, United States","type":"hotel",
           "parent_id":null,"id_type":"place_id","city_id":"13852","hotel_id":"185687",
           "country_code":"US","image":null}
        ]
    """.trimIndent()

    private fun parse() = json.decodeFromString<List<HotelDestinationDto>>(response)

    @Test
    fun fields_the_app_does_not_model_do_not_fail_the_response() {
        // country_code and image are real fields nothing here reads; parsing must survive them.
        assertEquals(4, parse().size)
    }

    @Test
    fun every_supplier_type_maps_to_something_the_picker_can_draw() {
        val types = parse().mapNotNull { it.toDestination()?.type }
        assertEquals(
            listOf(
                HotelDestinationType.City,
                HotelDestinationType.Airport,
                // "touristregion" is not one of the three the icons special-case.
                HotelDestinationType.Other,
                HotelDestinationType.Hotel,
            ),
            types,
        )
    }

    @Test
    fun rows_sharing_a_supplier_id_still_get_distinct_list_keys() {
        val destinations = parse().mapNotNull { it.toDestination() }
        val city = destinations.first { it.name == "San Francisco" }
        val hotel = destinations.first { it.name == "San Francisco Marriott Union Square" }

        assertEquals("61403", parse().first().id, "guard: the fixture really does collide")
        assertTrue(city.id != hotel.id, "ids collided: ${city.id}")
        assertEquals(destinations.size, destinations.map { it.id }.toSet().size)
    }

    @Test
    fun lookup_keys_carry_every_id_a_child_could_point_at() {
        val hotel = parse().mapNotNull { it.toDestination() }
            .first { it.type == HotelDestinationType.Hotel }
        // id, hotel_id, city_id — in that order, blanks dropped.
        assertEquals(listOf("61403", "185687", "13852"), hotel.lookupKeys)
    }

    @Test
    fun a_row_with_no_name_is_dropped_rather_than_listed_blank() {
        val nameless = """[{"id":"1","name":null,"type":"city"},{"id":"2","name":"  ","type":"city"}]"""
        val parsed = json.decodeFromString<List<HotelDestinationDto>>(nameless)
        parsed.forEach { assertNull(it.toDestination()) }
    }
}
