package com.gocheap.flights.data.model.flight.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The body `POST api/v3/search/` takes: the legs to price, the cabin to price them in, and the
 * party as a list of ages.
 *
 * The party is a list rather than counts because that is what v3 asks for — one entry per seated
 * traveller, each the traveller's age — so an adult and a twelve-year-old are two numbers in the
 * same list rather than two fields. Lap infants are counted separately in [infantsInSeat]'s
 * sibling sense: the reference app sends `infants_in_seat = 0` and folds lap infants nowhere,
 * which is reproduced here rather than corrected, because the backend prices off what it is sent.
 *
 * Built by [com.gocheap.flights.data.flight.FlightSearchRepository] out of the app's own
 * [com.gocheap.flights.data.model.TripSearch]; nothing constructs it by hand.
 */
@Serializable
internal data class FlightSearchRequest(
    @SerialName("legs") val legs: List<FlightSearchLeg>,
    @SerialName("cabin_class") val cabinClass: String,
    /** One age per seated traveller. [AdultAge] stands in for every adult, as the reference does. */
    @SerialName("passengers") val passengers: List<Int>,
    @SerialName("infants_in_seat") val infantsInSeat: Int = 0,
) {
    internal companion object {
        /**
         * The age every adult is sent as.
         *
         * 34 is the reference app's constant, not a real traveller's age: the endpoint prices on
         * age bands and any number inside the adult band gives the same fare, so one value stands
         * in for all of them.
         */
        const val AdultAge = 34

        /** What the backend calls an economy cabin when nothing else was chosen. */
        const val DefaultCabinClass = "economy"
    }
}

/**
 * One hop of the trip. A one-way search sends one of these, a return sends two — outbound first.
 *
 * [flex] is "exact" on every leg this app builds: the search card collects a date, not a range,
 * so there is nothing to flex around. It is a field rather than a constant because the endpoint
 * accepts other values and a later date-range search would set them here.
 */
@Serializable
internal data class FlightSearchLeg(
    @SerialName("origin") val origin: String,
    @SerialName("destination") val destination: String,
    /** `yyyy-MM-dd`. */
    @SerialName("date") val date: String,
    @SerialName("flex") val flex: String = "exact",
)
