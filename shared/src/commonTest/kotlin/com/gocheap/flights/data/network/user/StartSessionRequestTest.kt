package com.gocheap.flights.data.network.user

import com.gocheap.flights.data.model.user.Attribution
import com.gocheap.flights.data.model.user.CarSessionParams
import com.gocheap.flights.data.model.user.CarSessionRequest
import com.gocheap.flights.data.model.user.FlightSessionParams
import com.gocheap.flights.data.model.user.FlightSessionRequest
import com.gocheap.flights.data.model.user.HotelSessionParams
import com.gocheap.flights.data.model.user.HotelSessionRequest
import com.gocheap.flights.data.model.user.StartSessionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The wire format of `users/session/`, pinned for the same reason as [AddUserRequestTest]: the body
 * is built in one place, fired at the moment the user leaves the app, and a renamed key fails at
 * the backend rather than at compile time — on a call that deliberately swallows its own failures.
 *
 * Three bodies, three param shapes, one envelope. The envelope is what the tests below are really
 * about: the three verticals have to agree on it, and nothing but a test keeps them agreeing.
 */
class StartSessionRequestTest {

    private val json = UserHttpClient.UserJson

    private val envelopeKeys = setOf(
        "user_id", "type", "tag", "route", "vertical", "country_code",
        "gclid", "fbclid", "msclkid", "attribution", "params",
    )

    @Test
    fun every_vertical_sends_the_same_envelope() {
        assertEquals(envelopeKeys, keysOf(FlightSessionRequest.serializer(), FlightSessionRequest()))
        assertEquals(envelopeKeys, keysOf(HotelSessionRequest.serializer(), HotelSessionRequest()))
        assertEquals(envelopeKeys, keysOf(CarSessionRequest.serializer(), CarSessionRequest()))
    }

    @Test
    fun a_flight_body_names_its_own_search_parameters() {
        val params = paramsOf(
            FlightSessionRequest.serializer(),
            FlightSessionRequest(
                params = FlightSessionParams(
                    origin = "LON",
                    destination = "NYC",
                    startDate = "2026-11-03",
                    endDate = "2026-11-10",
                    cabinClass = "economy",
                    adults = 2,
                    children = 1,
                    infants = 0,
                ),
            ),
        )

        assertEquals(
            setOf(
                "origin", "destination", "start_date", "end_date",
                "cabin_class", "adults", "children", "infants",
            ),
            params.keys,
        )
        assertEquals("LON", params["origin"]?.jsonPrimitive?.content)
        assertEquals("2026-11-10", params["end_date"]?.jsonPrimitive?.content)
    }

    @Test
    fun a_one_way_flight_sends_no_end_date_value() {
        val params = paramsOf(
            FlightSessionRequest.serializer(),
            FlightSessionRequest(params = FlightSessionParams(origin = "LON", endDate = null)),
        )

        // Present but null, not dropped: the backend reads a one-way off this being empty, and
        // flipping explicitNulls off would take the key away entirely.
        assertEquals(JsonNull, params["end_date"])
    }

    @Test
    fun a_hotel_body_names_its_own_search_parameters() {
        val params = paramsOf(
            HotelSessionRequest.serializer(),
            HotelSessionRequest(
                params = HotelSessionParams(
                    location = "San Francisco",
                    checkInDate = "2026-12-19",
                    checkOutDate = "2026-12-22",
                    adults = 2,
                    children = 1,
                    rooms = 1,
                ),
            ),
        )

        assertEquals(
            setOf("location", "check_in_date", "check_out_date", "adults", "children", "rooms"),
            params.keys,
        )
        assertEquals("2026-12-19", params["check_in_date"]?.jsonPrimitive?.content)
    }

    @Test
    fun a_car_body_names_its_own_search_parameters() {
        val params = paramsOf(
            CarSessionRequest.serializer(),
            CarSessionRequest(
                params = CarSessionParams(
                    pickupLocation = "San Francisco International Airport",
                    dropoffLocation = null,
                    pickupDate = "2026-12-19",
                    dropoffDate = "2026-12-22",
                    pickupTime = "10:00",
                    dropoffTime = "10:00",
                ),
            ),
        )

        assertEquals(
            setOf(
                "pickup_location", "dropoff_location", "pickup_date",
                "dropoff_date", "pickup_time", "dropoff_time",
            ),
            params.keys,
        )
        // A same-location return is the absence of a drop-off, as it is in the deeplink.
        assertEquals(JsonNull, params["dropoff_location"])
        assertEquals("10:00", params["pickup_time"]?.jsonPrimitive?.content)
    }

    @Test
    fun the_attribution_goes_back_out_in_the_shape_it_arrived_in() {
        val encoded = json.encodeToString(
            HotelSessionRequest.serializer(),
            HotelSessionRequest(attribution = Attribution(campaignId = "120210000000000000")),
        ).let(Json::parseToJsonElement).jsonObject

        val attribution = encoded["attribution"]!!.jsonObject
        assertEquals("120210000000000000", attribution["campaign_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun the_response_is_read_off_the_backends_own_key_names() {
        val decoded = json.decodeFromString(
            StartSessionResponse.serializer(),
            """{"msg":"success","user_id":48213,"user_session_id":99001,"extra":"ignored"}""",
        )

        assertEquals(48213, decoded.userId)
        assertEquals(99001, decoded.userSessionId)
    }

    private fun <T> keysOf(serializer: kotlinx.serialization.KSerializer<T>, value: T) =
        json.encodeToString(serializer, value).let(Json::parseToJsonElement).jsonObject.keys

    private fun <T> paramsOf(serializer: kotlinx.serialization.KSerializer<T>, value: T) =
        json.encodeToString(serializer, value)
            .let(Json::parseToJsonElement).jsonObject["params"]!!.jsonObject
}
