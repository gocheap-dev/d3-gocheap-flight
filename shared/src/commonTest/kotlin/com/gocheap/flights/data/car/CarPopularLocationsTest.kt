package com.gocheap.flights.data.car

import com.gocheap.flights.data.location.CarPopularLocations
import com.gocheap.flights.data.model.car.CarLocationType
import com.gocheap.flights.data.network.car.CarDeeplink
import com.gocheap.flights.data.model.CarSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the parse of the shipped `files/car/CarPopularLocation.json`, against rows copied out of
 * it verbatim — the resource loader itself needs a running app, the mapping does not.
 *
 * The rows below are the awkward ones: a city that carries no location line, an airport that
 * carries one, and the Dubai row the supplier labels a city while filling its location with an
 * airport name.
 */
class CarPopularLocationsTest {

    private val contents = """
        [
          {
            "id": "51112",
            "name": "Bogotá, Colombia",
            "location": "",
            "type": "city",
            "parent_id": null,
            "id_type": "place_id",
            "city_id": "52059",
            "iata": "BOG",
            "deeplink_name": "Bogotá,Colombia",
            "image": "https://content.r9cdn.net/rimg/dimg/a5/4f/80b8c5cf-city-52059.jpg",
            "country_code": "CO"
          },
          {
            "id": "175706",
            "name": "Los Angeles International Airport",
            "location": "Los Angeles, California, United States",
            "type": "airport",
            "parent_id": null,
            "id_type": "place_id",
            "city_id": "16078",
            "iata": "LAX",
            "deeplink_name": "Los-Angeles,California,United-States",
            "image": "https://content.r9cdn.net/rimg/dimg/a6/ae/d1a4a522-city-16078.jpg",
            "country_code": "US"
          },
          {
            "id": "177772",
            "name": "Dubai, United Arab Emirates",
            "location": "Dubai Intl",
            "type": "city",
            "parent_id": null,
            "id_type": "place_id",
            "city_id": "6080",
            "iata": "DXB",
            "deeplink_name": "Dubai,United-Arab-Emirates",
            "image": "https://content.r9cdn.net/rimg/dimg/9b/f7/7ab79f27-city-6080.jpg",
            "country_code": "AE"
          }
        ]
    """.trimIndent()

    private val parsed = CarPopularLocations.parse(contents)

    @Test
    fun the_rows_are_the_same_shape_the_autocomplete_returns() {
        assertEquals(3, parsed.size)
        assertEquals(listOf("51112", "175706", "177772"), parsed.map { it.supplierId })
        assertEquals(listOf("52059", "16078", "6080"), parsed.map { it.cityId })
        assertEquals(listOf("BOG", "LAX", "DXB"), parsed.map { it.iata })
    }

    @Test
    fun the_list_is_not_all_cities() {
        assertEquals(
            listOf(CarLocationType.City, CarLocationType.Airport, CarLocationType.City),
            parsed.map { it.type },
        )
    }

    @Test
    fun a_card_reads_the_town_and_its_country_whichever_field_names_them() {
        // The city rows qualify their name and leave location empty; the airport row is the reverse.
        assertEquals(listOf("Bogotá", "Los Angeles", "Dubai"), parsed.map { it.cityLabel })
        assertEquals(
            listOf("Colombia", "United States", "United Arab Emirates"),
            parsed.map { it.countryLabel },
        )
    }

    @Test
    fun picking_one_searches_by_id_rather_than_by_name() {
        val losAngeles = parsed[1]
        val url = CarDeeplink.forSearch(
            CarSearch(
                city = losAngeles.cityLabel,
                pickupPoint = losAngeles.name,
                pickupDate = "19, dec 2022",
                dropoffDate = "22, dec 2022",
                days = 3,
                pickupLocation = losAngeles,
            ),
        )
        assertTrue("pick_up=LAX" in url, url)
        assertTrue("pick_up_city_id=16078" in url, url)
        assertTrue("pick_up_type=airport" in url, url)
    }
}
