package com.gocheap.travel.data.flight

import com.gocheap.travel.data.location.FlightPopularLocations
import com.gocheap.travel.data.model.flight.FlightDestinationType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the parse of the shipped `files/flight/FlightPopularLocation.json`, against rows copied
 * out of it verbatim — the resource loader itself needs a running app, the mapping does not.
 *
 * The rows below are the two shapes the file mixes: a metropolitan area, whose name carries the
 * place and whose location says only "All airports", and one actual terminal, which is the other
 * way round.
 */
class FlightPopularLocationsTest {

    private val contents = """
        [
          {
            "name": "London, England, United Kingdom",
            "location": "All airports",
            "type": "city",
            "iata": "LON",
            "parent_iata": null,
            "images": [
              "https://content.r9cdn.net/rimg/dimg/8a/d8/29dcb2f0-city-28501.jpg?outputtype=JPEG",
              "https://content.r9cdn.net/rimg/dimg/8a/d8/29dcb2f0-city-28501.jpg?outputtype=WEBP"
            ]
          },
          {
            "name": "Indira Gandhi International Airport",
            "location": "New Delhi, National Capital Territory of India, India",
            "type": "airport",
            "iata": "DEL",
            "parent_iata": null,
            "images": [
              "https://content.r9cdn.net/rimg/dimg/d0/25/36bc0f48-ap-DEL.jpg?outputtype=JPEG",
              "https://content.r9cdn.net/rimg/dimg/d0/25/36bc0f48-ap-DEL.jpg?outputtype=WEBP"
            ]
          }
        ]
    """.trimIndent()

    private val parsed = FlightPopularLocations.parse(contents)

    @Test
    fun the_rows_are_the_same_shape_the_autocomplete_returns() {
        assertEquals(2, parsed.size)
        assertEquals(listOf("LON", "DEL"), parsed.map { it.iata })
    }

    @Test
    fun the_list_is_not_all_cities() {
        assertEquals(
            listOf(FlightDestinationType.City, FlightDestinationType.Airport),
            parsed.map { it.type },
        )
    }

    @Test
    fun a_card_reads_the_town_and_its_country_whichever_field_names_them() {
        // The city row qualifies its name and fills location with a label; the airport is the reverse.
        assertEquals(listOf("London", "New Delhi"), parsed.map { it.cityName })
        assertEquals(listOf("United Kingdom", "India"), parsed.map { it.countryName })
    }

    @Test
    fun a_card_draws_the_webp_of_the_pair() {
        assertEquals(
            listOf(
                "https://content.r9cdn.net/rimg/dimg/8a/d8/29dcb2f0-city-28501.jpg?outputtype=WEBP",
                "https://content.r9cdn.net/rimg/dimg/d0/25/36bc0f48-ap-DEL.jpg?outputtype=WEBP",
            ),
            parsed.map { it.imageUrl },
        )
    }
}
