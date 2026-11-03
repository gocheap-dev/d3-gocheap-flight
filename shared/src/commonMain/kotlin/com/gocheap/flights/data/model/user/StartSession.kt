package com.gocheap.flights.data.model.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The bodies `POST users/session/` is sent — one per vertical, as the reference app sends them.
 *
 * Every vertical repeats the same envelope and differs only in `params`, which is how the endpoint
 * is specified: the envelope says who left and under what attribution, `params` says what they were
 * looking for, and the two are read by different parts of the backend. Three classes rather than
 * one with a union of every field, because JSON has no way to flatten a shared envelope into a
 * nested object and a hotel body carrying `"origin": null` would be claiming a flight search it
 * never made.
 *
 * Nulls are serialised rather than omitted, as in [AddUserRequest] and for the same reason: the
 * reference sends them and the endpoint is specified against that shape.
 */
@Serializable
internal data class FlightSessionRequest(
    @SerialName("user_id") val userId: Int? = null,
    /**
     * How the search left: "sdk" for the Travelpayouts results screen, "deeplink" for a link out
     * to a supplier's page, "api" for results fetched and rendered in-app. A flight can leave all
     * three ways — see `SessionTracking.FlightExit`, which defaults to "sdk".
     */
    @SerialName("type") val type: String? = null,
    /** Free-text label for the placement that produced the exit. Unused here; the reference too. */
    @SerialName("tag") val tag: String? = null,
    /** The install's acquisition route, repeated from `users/add` so a session can stand alone. */
    @SerialName("route") val route: String? = null,
    @SerialName("vertical") val vertical: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("gclid") val gclid: String? = null,
    @SerialName("fbclid") val fbclid: String? = null,
    @SerialName("msclkid") val msclkid: String? = null,
    /** Echoed back exactly as `users/add` resolved it — see [Attribution]. */
    @SerialName("attribution") val attribution: Attribution? = null,
    @SerialName("params") val params: FlightSessionParams? = null,
)

@Serializable
internal data class FlightSessionParams(
    /** Three-letter codes, as the supplier names them. */
    @SerialName("origin") val origin: String? = null,
    @SerialName("destination") val destination: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    /** Absent on a one-way, which is how the backend tells the two apart. */
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("cabin_class") val cabinClass: String? = null,
    @SerialName("adults") val adults: Int? = null,
    @SerialName("children") val children: Int? = null,
    @SerialName("infants") val infants: Int? = null,
)

@Serializable
internal data class HotelSessionRequest(
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("tag") val tag: String? = null,
    @SerialName("route") val route: String? = null,
    @SerialName("vertical") val vertical: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("gclid") val gclid: String? = null,
    @SerialName("fbclid") val fbclid: String? = null,
    @SerialName("msclkid") val msclkid: String? = null,
    @SerialName("attribution") val attribution: Attribution? = null,
    @SerialName("params") val params: HotelSessionParams? = null,
)

@Serializable
internal data class HotelSessionParams(
    /** The destination as the user saw it named, not as the deeplink ids it. */
    @SerialName("location") val location: String? = null,
    @SerialName("check_in_date") val checkInDate: String? = null,
    @SerialName("check_out_date") val checkOutDate: String? = null,
    @SerialName("adults") val adults: Int? = null,
    @SerialName("children") val children: Int? = null,
    @SerialName("rooms") val rooms: Int? = null,
)

@Serializable
internal data class CarSessionRequest(
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("tag") val tag: String? = null,
    @SerialName("route") val route: String? = null,
    @SerialName("vertical") val vertical: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("gclid") val gclid: String? = null,
    @SerialName("fbclid") val fbclid: String? = null,
    @SerialName("msclkid") val msclkid: String? = null,
    @SerialName("attribution") val attribution: Attribution? = null,
    @SerialName("params") val params: CarSessionParams? = null,
)

@Serializable
internal data class CarSessionParams(
    @SerialName("pickup_location") val pickupLocation: String? = null,
    /** Null when the car goes back where it was collected, which is how "same" is encoded. */
    @SerialName("dropoff_location") val dropoffLocation: String? = null,
    @SerialName("pickup_date") val pickupDate: String? = null,
    @SerialName("dropoff_date") val dropoffDate: String? = null,
    /** 24-hour "HH:mm": a rental is booked against the desk time as much as the day. */
    @SerialName("pickup_time") val pickupTime: String? = null,
    @SerialName("dropoff_time") val dropoffTime: String? = null,
)

/**
 * What the endpoint answers with.
 *
 * [userSessionId] is the row the backend created. Nothing in this app reads it back — the session
 * is recorded, not resumed — so it is logged and dropped, which is what the reference does too.
 */
@Serializable
data class StartSessionResponse(
    @SerialName("msg") val msg: String? = null,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("user_session_id") val userSessionId: Int? = null,
)
