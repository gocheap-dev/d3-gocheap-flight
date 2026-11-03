package com.gocheap.flights.data.hotel

import com.gocheap.flights.data.location.HotelPopularLocations
import com.gocheap.flights.data.model.HotelSearch
import com.gocheap.flights.data.model.hotel.HotelDestinationType
import com.gocheap.flights.data.network.hotel.StayDeeplink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the parse of the shipped `files/hotel/HotelPopularLocation.json`, against rows copied out
 * of it verbatim — the resource loader itself needs a running app, the mapping does not.
 *
 * The rows below are the three qualifier shapes the file contains: a country on its own, a region
 * and a country, and a nation inside a country.
 */
class HotelPopularLocationsTest {

    private val contents = """
        [
          {
            "id": "37687",
            "name": "Paris",
            "location": "France",
            "type": "city",
            "parent_id": null,
            "id_type": "place_id",
            "city_id": "36014",
            "hotel_id": null,
            "destination_images": {
              "image_jpeg": "https://content.r9cdn.net/rimg/dimg/bd/d1/2f268866-city-36014.jpg?outputtype=JPEG",
              "image_webp": "https://content.r9cdn.net/rimg/dimg/bd/d1/2f268866-city-36014.jpg?outputtype=WEBP"
            }
          },
          {
            "id": "42002",
            "name": "Barcelona",
            "location": "Catalonia, Spain",
            "type": "city",
            "parent_id": null,
            "id_type": "place_id",
            "city_id": "22567",
            "hotel_id": null,
            "destination_images": {
              "image_jpeg": "https://content.r9cdn.net/rimg/dimg/bf/3b/3dfae4b9-lm-4502.jpg?outputtype=JPEG",
              "image_webp": "https://content.r9cdn.net/rimg/dimg/bf/3b/3dfae4b9-lm-4502.jpg?outputtype=WEBP"
            }
          },
          {
            "id": "29230",
            "name": "London",
            "location": "England, United Kingdom",
            "type": "city",
            "parent_id": null,
            "id_type": "place_id",
            "city_id": "28501",
            "hotel_id": null,
            "destination_images": {
              "image_jpeg": "https://content.r9cdn.net/rimg/dimg/8a/d8/29dcb2f0-city-28501.jpg?outputtype=JPEG",
              "image_webp": "https://content.r9cdn.net/rimg/dimg/8a/d8/29dcb2f0-city-28501.jpg?outputtype=WEBP"
            }
          }
        ]
    """.trimIndent()

    private val parsed = HotelPopularLocations.parse(contents)

    @Test
    fun the_rows_are_the_same_shape_the_autocomplete_returns() {
        assertEquals(3, parsed.size)
        assertEquals(listOf("37687", "42002", "29230"), parsed.map { it.supplierId })
        assertEquals(listOf("36014", "22567", "28501"), parsed.map { it.cityId })
        assertEquals(listOf("place_id", "place_id", "place_id"), parsed.map { it.idType })
    }

    @Test
    fun the_list_is_all_cities_and_no_properties() {
        assertTrue(parsed.all { it.type == HotelDestinationType.City })
        assertTrue(parsed.all { it.hotelId == null })
    }

    @Test
    fun the_photograph_is_taken_in_webp_where_the_supplier_offers_both() {
        assertTrue(parsed.all { it.imageUrl?.endsWith("outputtype=WEBP") == true }, "${parsed.map { it.imageUrl }}")
    }

    @Test
    fun an_autocomplete_row_carries_no_photograph() {
        // Nothing typed into the destination sheet comes back with `destination_images`.
        val typed = HotelPopularLocations.parse(
            """[{"id":"1","name":"Lisbon","location":"Portugal","type":"city","id_type":"place_id"}]""",
        )
        assertNull(typed.single().imageUrl)
    }

    @Test
    fun picking_one_searches_by_id_rather_than_by_name() {
        val barcelona = parsed[1]
        val url = StayDeeplink.forSearch(
            HotelSearch(
                city = barcelona.name,
                checkInDate = "19, dec 2022",
                checkOutDate = "22, dec 2022",
                nights = 3,
                destination = barcelona,
            ),
        )
        assertTrue("place_id=42002" in url, url)
        assertTrue("city_id=22567" in url, url)
    }
}
