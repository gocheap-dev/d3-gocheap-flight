package com.gocheap.flights.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Airport(
    val code: String,
    val city: String,
    val name: String,
    /**
     * The code of the city this place sits in, where the supplier names one that differs from
     * [code] — "LON" for Heathrow, "NYC" for JFK. Blank on a row that is a city itself, and on an
     * airport the supplier gives no parent for.
     *
     * Carried because a results supplier groups what it finds by city and reads a search back
     * under that code, so an airport inside a metropolitan area is not found under its own.
     */
    val cityCode: String = "",
) {
    /** The code is what a search actually runs on, so an empty one is an unfilled field. */
    val isChosen: Boolean get() = code.isNotBlank()

    /** [cityCode] where the supplier gave one, and the place's own code where it did not. */
    val searchCityCode: String get() = cityCode.ifBlank { code }

    companion object {
        /**
         * Neither end of a trip, which is how the card opens before anything has been searched.
         *
         * Dates and a party size are safe to guess at; where someone is flying from is not, and a
         * prefilled route is one the user never chose and may not notice — the same reasoning the
         * stay and rental cards already open on.
         */
        val Unset = Airport(code = "", city = "", name = "")
    }
}

data class Flight(
    val id: String,
    val airline: String,
    val flightNumber: String,
    val from: Airport,
    val to: Airport,
    val date: String,
    val departureTime: String,
    val arrivalTime: String,
    val durationLabel: String,
    val stops: Int,
    val cabinClass: String,
    val priceUsd: Int,
    val terminal: String,
    val gate: String,
    val baggageKg: Int,
) {
    val stopsLabel: String
        get() = when (stops) {
            0 -> "Direct"
            1 -> "1 stop"
            else -> "$stops stops"
        }
}

enum class SeatStatus { Available, Taken, Selected }

data class Seat(
    val row: Int,
    val column: Char,
    val status: SeatStatus,
    val surchargeUsd: Int,
) {
    val label: String get() = "$row$column"
}

data class Passenger(
    val fullName: String,
    val email: String,
    val phone: String,
    val passportNumber: String,
)

enum class BookingStatus { Upcoming, Completed, Cancelled }

data class Booking(
    val id: String,
    val reference: String,
    val flight: Flight,
    val seatLabels: List<String>,
    val passenger: Passenger,
    val status: BookingStatus,
    val totalUsd: Int,
)

/**
 * The cabins a flight can be searched in.
 *
 * Serialized under the words the old `cabinClass` string carried, so a search saved by an earlier
 * build still reads back as the cabin it was run in rather than falling back to Economy.
 */
@Serializable
enum class CabinClass(val label: String) {
    @SerialName("Economy")
    Economy("Economy"),

    @SerialName("Premium Economy")
    PremiumEconomy("Premium economy"),

    @SerialName("Business")
    Business("Business"),

    @SerialName("First")
    First("First class"),
}

/** The trip parameters the home search form collects. */
@Serializable
data class TripSearch(
    val from: Airport,
    val to: Airport,
    val departureDate: String,
    val returnDate: String?,
    val isRoundTrip: Boolean,
    /**
     * The party, split the way an airline fares it: an infant flies on a lap and a child in a seat,
     * and neither is priced as an adult. This replaces the single passenger count the card used to
     * carry, which could only ever be sent as adults.
     */
    val adults: Int = 1,
    val children: Int = 0,
    val infants: Int = 0,
    val cabinClass: CabinClass = CabinClass.Economy,
) {
    /** Everyone on the booking, which is what the card and the recent cards count. */
    val passengers: Int get() = adults + children + infants
}
