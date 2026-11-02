package com.gocheap.travel.data.car

import com.gocheap.travel.data.model.CarSearch
import com.gocheap.travel.data.model.car.CarLocation
import com.gocheap.travel.data.model.car.CarLocationType
import com.gocheap.travel.data.network.car.CarDeeplink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The deeplink *is* the search — there is no results API behind it — so a wrong parameter name
 * silently returns the wrong cars rather than failing. These pin it to the reference app's car
 * `generateUri`, which is a different parameter set from the stay page's despite the same path.
 */
class CarDeeplinkTest {

    private val sfoAirport = CarLocation(
        id = "175748-13852-SFO-San Francisco International Airport",
        name = "San Francisco International Airport",
        location = "San Francisco, California, United States",
        type = CarLocationType.Airport,
        supplierId = "175748",
        idType = "place_id",
        cityId = "13852",
        iata = "SFO",
        deeplinkName = "San-Francisco,California,United-States",
        apiType = "airport",
    )

    private val sanFranciscoCity = CarLocation(
        id = "61403-13852-SFO-San Francisco, California, United States",
        name = "San Francisco, California, United States",
        location = "",
        type = CarLocationType.City,
        supplierId = "61403",
        idType = "place_id",
        cityId = "13852",
        iata = "SFO",
        deeplinkName = "San-Francisco,California,United-States",
        apiType = "city",
    )

    private fun search(
        pickup: CarLocation? = sfoAirport,
        dropoff: CarLocation? = null,
        pickupDate: String = "19, dec 2022",
        dropoffDate: String = "22, dec 2022",
        pickupTime: String = "10:00",
        dropoffTime: String = "16:30",
    ) = CarSearch(
        city = pickup?.cityLabel ?: "Paris",
        pickupPoint = pickup?.name ?: "Paris downtown",
        pickupDate = pickupDate,
        dropoffDate = dropoffDate,
        days = 3,
        pickupTime = pickupTime,
        dropoffTime = dropoffTime,
        dropoffCity = dropoff?.cityLabel,
        dropoffPoint = dropoff?.name,
        pickupLocation = pickup,
        dropoffLocation = dropoff,
    )

    /** Parses the query string back out, so assertions name parameters rather than offsets. */
    private fun params(url: String): Map<String, String> =
        url.substringAfter("?").split("&").filter(String::isNotEmpty).associate { pair ->
            val (name, value) = pair.split("=", limit = 2)
            name to value
        }

    @Test
    fun it_points_at_the_rental_suppliers_deeplink_page() {
        assertTrue(
            CarDeeplink.forSearch(search())
                .startsWith("https://staging.car.lascade.com/deeplink/new/?"),
            CarDeeplink.forSearch(search()),
        )
    }

    @Test
    fun an_airport_is_named_by_its_code() {
        val sent = params(CarDeeplink.forSearch(search()))
        assertEquals("SFO", sent["pick_up"])
        assertEquals("13852", sent["pick_up_city_id"])
        assertEquals("airport", sent["pick_up_type"])
    }

    @Test
    fun a_place_id_city_is_named_by_its_slug_not_its_id() {
        val sent = params(CarDeeplink.forSearch(search(pickup = sanFranciscoCity)))
        assertEquals("San-Francisco%2CCalifornia%2CUnited-States", sent["pick_up"])
        assertEquals("city", sent["pick_up_type"])
    }

    @Test
    fun an_entity_id_row_is_named_by_its_raw_id() {
        val entity = sanFranciscoCity.copy(idType = "entity_id", supplierId = "99")
        assertEquals("99", params(CarDeeplink.forSearch(search(pickup = entity)))["pick_up"])
    }

    @Test
    fun a_pick_with_no_supplier_ids_still_searches_by_name() {
        // The popular-locations row carries a name and nothing else.
        val sent = params(CarDeeplink.forSearch(search(pickup = null)))
        assertEquals("Paris", sent["pick_up"])
        assertFalse("pick_up_city_id" in sent)
    }

    @Test
    fun dates_carry_the_desk_time_and_go_out_as_iso() {
        val sent = params(CarDeeplink.forSearch(search()))
        // ":" survives as %3A — the reference's Uri.Builder encodes it the same way.
        assertEquals("2022-12-19T10%3A00", sent["pick_up_date"])
        assertEquals("2022-12-22T16%3A30", sent["drop_off_date"])
    }

    @Test
    fun an_unrecognised_time_falls_back_to_midnight_rather_than_dropping_the_date() {
        val sent = params(CarDeeplink.forSearch(search(pickupTime = "noon")))
        assertEquals("2022-12-19T00%3A00", sent["pick_up_date"])
    }

    @Test
    fun a_same_location_return_sends_no_drop_off_half_at_all() {
        val sent = params(CarDeeplink.forSearch(search(dropoff = null)))
        assertFalse("drop_off" in sent)
        assertFalse("drop_off_city_id" in sent)
        assertFalse("drop_off_type" in sent)
        // The return date is still sent: the rental ends on a day either way.
        assertTrue("drop_off_date" in sent)
    }

    @Test
    fun a_one_way_toggle_with_nothing_picked_sends_no_drop_off() {
        // The card can be switched to a one-way return before a drop-off is chosen; an empty
        // drop_off would resolve to nowhere, so the half is left off entirely.
        val blank = search().copy(dropoffCity = "", dropoffPoint = "", dropoffLocation = null)
        val sent = params(CarDeeplink.forSearch(blank))
        assertFalse("drop_off" in sent)
        assertTrue("drop_off_date" in sent)
    }

    @Test
    fun a_one_way_rental_names_the_other_end_too() {
        val oakland = sfoAirport.copy(
            name = "Oakland International Airport",
            location = "Oakland, California, United States",
            iata = "OAK",
            cityId = "10219",
        )
        val sent = params(CarDeeplink.forSearch(search(dropoff = oakland)))
        assertEquals("OAK", sent["drop_off"])
        assertEquals("10219", sent["drop_off_city_id"])
        assertEquals("airport", sent["drop_off_type"])
    }

    @Test
    fun a_same_day_return_is_pushed_to_the_next_day() {
        // The supplier rejects a zero-day rental, so the range is repaired rather than sent as-is.
        val sent = params(
            CarDeeplink.forSearch(
                search(pickupDate = "19, dec 2022", dropoffDate = "19, dec 2022"),
            ),
        )
        assertEquals("2022-12-19T10%3A00", sent["pick_up_date"])
        assertEquals("2022-12-20T16%3A30", sent["drop_off_date"])
    }

    @Test
    fun the_fixed_identifying_parameters_are_always_present() {
        val sent = params(CarDeeplink.forSearch(search()))
        // "app", not the stay page's "app_code" — the two pages read different names.
        assertEquals("D3AT", sent["app"])
        assertFalse("app_code" in sent)
        assertEquals("organic", sent["route"])
        assertEquals("US", sent["country"])
        assertEquals("USD", sent["currency"])
        assertEquals("en", sent["language"])
        assertEquals("fallback_d3at", sent["user_id"])
    }
}
