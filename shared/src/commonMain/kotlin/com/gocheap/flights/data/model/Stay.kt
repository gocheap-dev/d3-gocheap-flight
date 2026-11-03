package com.gocheap.flights.data.model

import com.gocheap.flights.data.model.car.CarLocation
import com.gocheap.flights.data.model.hotel.HotelDestination
import kotlinx.serialization.Serializable

data class Hotel(
    val id: String,
    val name: String,
    val city: String,
    val address: String,
    val starRating: Int,
    val reviewScore: String,
    val reviewCount: Int,
    val pricePerNightUsd: Int,
    val roomType: String,
    val distanceFromCentreKm: String,
    val freeCancellation: Boolean,
    val amenities: List<String>,
)

/**
 * One room of a stay and the party in it.
 *
 * Children are kept as their ages rather than as a count: the supplier prices a stay off how old
 * each child is and takes the ages as a list, so a count alone would leave nothing to send.
 */
@Serializable
data class StayRoom(
    val adults: Int = 1,
    /** One age per child, each 0..17. Empty for a room of adults. */
    val childAges: List<Int> = emptyList(),
) {
    val children: Int get() = childAges.size
    val guests: Int get() = adults + childAges.size
}

@Serializable
data class HotelSearch(
    val city: String,
    val checkInDate: String,
    val checkOutDate: String,
    val nights: Int,
    /**
     * One entry per room, each with its own party.
     *
     * Replaces the guest and room counts this used to carry: those two could disagree, and neither
     * could say which room a child was in or how old they were — which is exactly what the supplier
     * prices on. The counts are still read off this list below, so nothing above had to change.
     */
    val rooms: List<StayRoom> = listOf(StayRoom()),
    /**
     * The row picked in the destination sheet, when that is where the city came from.
     *
     * Carried on the search rather than looked up again because the results deeplink needs the
     * supplier's own ids, and nothing in this app can resolve a city name back into them. Null when
     * the destination came from the popular-locations row, which only knows a name — the deeplink
     * then falls back to searching by that name.
     */
    val destination: HotelDestination? = null,
) {
    /** At least one of each, whatever was handed in: a stay for nobody is not a search. */
    val roomCount: Int get() = rooms.size.coerceAtLeast(1)
    val guests: Int get() = rooms.sumOf { it.guests }.coerceAtLeast(1)
    val adults: Int get() = rooms.sumOf { it.adults }.coerceAtLeast(1)

    /** Flattened across the rooms, which is the order the results page reads them in. */
    val childAges: List<Int> get() = rooms.flatMap { it.childAges }
}

data class Car(
    val id: String,
    val model: String,
    val category: String,
    val supplier: String,
    val seats: Int,
    val bags: Int,
    val transmission: String,
    val hasAirConditioning: Boolean,
    val unlimitedMileage: Boolean,
    val pricePerDayUsd: Int,
    val reviewScore: String,
    val pickupPoint: String,
)

@Serializable
data class CarSearch(
    val city: String,
    val pickupPoint: String,
    val pickupDate: String,
    val dropoffDate: String,
    val days: Int,
    /** Desk times, which a rental is booked against as much as the dates. */
    val pickupTime: String = "10:00",
    val dropoffTime: String = "10:00",
    /**
     * Where the car goes back, when that is not where it was collected. Null means it is returned
     * to the pick-up location — encoding "same" as absence rather than as a separate flag, so the
     * two can never disagree.
     */
    val dropoffCity: String? = null,
    val dropoffPoint: String? = null,
    /**
     * The rows picked in the location sheet, when that is where the two ends came from.
     *
     * Carried on the search for the same reason as [HotelSearch.destination]: the results deeplink
     * needs the supplier's own ids and an airport's code, and nothing in this app can resolve a city
     * name back into them. Null when the pick came from the popular-locations row, which only knows
     * a name — the deeplink then falls back to searching by that name.
     */
    val pickupLocation: CarLocation? = null,
    val dropoffLocation: CarLocation? = null,
) {
    val isSameDropoff: Boolean get() = dropoffCity == null
}
