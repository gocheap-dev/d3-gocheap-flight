package com.gocheap.flights.data.flight

import com.gocheap.flights.data.model.Airport
import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.data.model.Booking
import com.gocheap.flights.data.model.BookingStatus
import com.gocheap.flights.data.model.Flight
import com.gocheap.flights.data.model.LocationSuggestion
import com.gocheap.flights.data.model.Passenger
import com.gocheap.flights.data.model.Seat
import com.gocheap.flights.data.model.SeatStatus
import com.gocheap.flights.data.model.TripSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory stand-in for a booking backend. Every call is suspending and returns the same shapes a
 * network layer would, so swapping in a real client later does not touch the UI.
 */
class FlightRepository {

    suspend fun airports(): List<Airport> {
        return Airports
    }

    /**
     * Dates are offsets from today, never literals: a fixed default goes stale the day after it is
     * written, and this one used to open the search card on December 2022.
     */
    suspend fun defaultSearch(): TripSearch {
        val departure = CalendarDate.today().plusDays(DefaultDaysUntilDeparture)
        return TripSearch(
            // Neither end is guessed at — see [Airport.Unset]. The card opens asking where the
            // trip goes rather than offering someone else's.
            from = Airport.Unset,
            to = Airport.Unset,
            departureDate = departure.label,
            returnDate = departure.plusDays(DefaultTripNights).label,
            isRoundTrip = true,
        )
    }

    suspend fun searchFlights(search: TripSearch): List<Flight> {
        delay(NetworkDelayMillis)
        return Flights.map { flight ->
            flight.copy(
                from = search.from,
                to = search.to,
                date = search.departureDate,
                cabinClass = search.cabinClass.label,
            )
        }
    }

    suspend fun flightById(flightId: String): Flight? {
        return Flights.firstOrNull { it.id == flightId }
    }

    suspend fun bookings(): List<Booking> {
        delay(NetworkDelayMillis)
        return bookingsState.value
    }

    suspend fun bookingById(bookingId: String): Booking? {
        return bookingsState.value.firstOrNull { it.id == bookingId }
    }

    /** Deterministic seat map so a given flight always renders the same cabin. */
    suspend fun seatMap(flightId: String): List<Seat> {
        delay(NetworkDelayMillis)
        val takenSeed = flightId.sumOf { it.code }
        return (1..RowCount).flatMap { row ->
            SeatColumns.mapIndexed { columnIndex, column ->
                val slot = row * SeatColumns.size + columnIndex
                Seat(
                    row = row,
                    column = column,
                    status = if ((slot * 7 + takenSeed) % 5 == 0) SeatStatus.Taken else SeatStatus.Available,
                    surchargeUsd = if (row <= 2) 25 else if (column == 'A' || column == 'F') 12 else 0,
                )
            }
        }
    }

    suspend fun confirmBooking(
        flight: Flight,
        seatLabels: List<String>,
        passenger: Passenger,
        totalUsd: Int,
    ): Booking {
        delay(NetworkDelayMillis)
        val booking = Booking(
            id = "bk-${bookingsState.value.size + 1}",
            reference = buildReference(flight, seatLabels),
            flight = flight,
            seatLabels = seatLabels,
            passenger = passenger,
            status = BookingStatus.Upcoming,
            totalUsd = totalUsd,
        )
        bookingsState.update { listOf(booking) + it }
        return booking
    }

    fun favoriteFlightIds(): StateFlow<Set<String>> = favoritesState.asStateFlow()

    fun toggleFavorite(flightId: String) {
        favoritesState.update { current ->
            if (flightId in current) current - flightId else current + flightId
        }
    }

    suspend fun favoriteFlights(): List<Flight> {
        return Flights.filter { it.id in favoritesState.value }
    }

    suspend fun currentPassenger(): Passenger = DefaultPassenger

    private fun buildReference(flight: Flight, seatLabels: List<String>): String {
        val seatPart = seatLabels.firstOrNull().orEmpty().filter { it.isLetterOrDigit() }
        return "${flight.flightNumber}$seatPart".uppercase().take(8)
    }

    private companion object {
        const val NetworkDelayMillis = 450L

        /** A week out, returning a week later — the shape the card has always opened on. */
        const val DefaultDaysUntilDeparture = 7
        const val DefaultTripNights = 7

        /** How far back the one completed booking in the sample history sits. */
        const val DaysSincePastTrip = 45
        const val RowCount = 10
        val SeatColumns = listOf('A', 'B', 'C', 'D', 'E', 'F')

        val Airports = listOf(
            Airport("NYC", "New York City", "NYC International Airport"),
            Airport("SFO", "San Fransisco", "SFO International Airport"),
            Airport("LAX", "Los Angeles", "LAX International Airport"),
            // The one code here that is an airport rather than a city; its city is LON.
            Airport("LHR", "London", "Heathrow Airport", cityCode = "LON"),
            Airport("DXB", "Dubai", "Dubai International Airport"),
            Airport("SIN", "Singapore", "Changi Airport"),
            Airport("COK", "Kochi", "Cochin International Airport"),
            Airport("BLR", "Bengaluru", "Kempegowda International Airport"),
        )

        val Nyc = Airports[0]
        val Sfo = Airports[1]

        /**
         * The sample flights are reached by id as well as by search, and [flightById] — unlike
         * [searchFlights] — hands them back with their own date. So these have to be dated from
         * today, or the detail and ticket screens show whatever year this list was written in.
         *
         * Computed once per process, which is near enough: a device that stays awake across
         * midnight has bigger inconsistencies than a sample flight dated yesterday.
         */
        val SampleDepartureDate = CalendarDate.today().plusDays(DefaultDaysUntilDeparture).label

        /** A trip already taken, for the completed booking in the history list. */
        val PastDepartureDate = CalendarDate.today().plusDays(-DaysSincePastTrip).label

        val Flights = listOf(
            Flight(
                id = "fl-1",
                airline = "Delta Airlines",
                flightNumber = "DL 1267",
                from = Nyc,
                to = Sfo,
                date = SampleDepartureDate,
                departureTime = "12:00 AM",
                arrivalTime = "13:30 AM",
                durationLabel = "1h45m",
                stops = 0,
                cabinClass = "Economy",
                priceUsd = 248,
                terminal = "T4",
                gate = "A12",
                baggageKg = 25,
            ),
            Flight(
                id = "fl-2",
                airline = "American Airlines",
                flightNumber = "AA 884",
                from = Nyc,
                to = Sfo,
                date = SampleDepartureDate,
                departureTime = "06:40 AM",
                arrivalTime = "10:05 AM",
                durationLabel = "3h25m",
                stops = 1,
                cabinClass = "Economy",
                priceUsd = 196,
                terminal = "T8",
                gate = "C3",
                baggageKg = 20,
            ),
            Flight(
                id = "fl-3",
                airline = "United Airlines",
                flightNumber = "UA 521",
                from = Nyc,
                to = Sfo,
                date = SampleDepartureDate,
                departureTime = "09:15 AM",
                arrivalTime = "12:30 PM",
                durationLabel = "3h15m",
                stops = 0,
                cabinClass = "Business",
                priceUsd = 412,
                terminal = "T7",
                gate = "B7",
                baggageKg = 32,
            ),
            Flight(
                id = "fl-4",
                airline = "JetBlue",
                flightNumber = "B6 1142",
                from = Nyc,
                to = Sfo,
                date = SampleDepartureDate,
                departureTime = "17:50 PM",
                arrivalTime = "21:20 PM",
                durationLabel = "3h30m",
                stops = 0,
                cabinClass = "Economy",
                priceUsd = 223,
                terminal = "T5",
                gate = "D2",
                baggageKg = 23,
            ),
        )

        val DefaultPassenger = Passenger(
            fullName = "Ajin Jenin",
            email = "jenin@lascade.com",
            phone = "+1 415 555 0132",
            passportNumber = "P8821746",
        )

        /** Shared across repository instances so a booking made in checkout shows up in My Tickets. */
        val bookingsState = MutableStateFlow(
            listOf(
                Booking(
                    id = "bk-0",
                    reference = "DL12671A",
                    flight = Flights[0],
                    seatLabels = listOf("1A"),
                    passenger = DefaultPassenger,
                    status = BookingStatus.Upcoming,
                    totalUsd = 273,
                ),
                Booking(
                    id = "bk-past-1",
                    reference = "UA5213C",
                    flight = Flights[2].copy(date = PastDepartureDate),
                    seatLabels = listOf("3C"),
                    passenger = DefaultPassenger,
                    status = BookingStatus.Completed,
                    totalUsd = 412,
                ),
            ),
        )

        val favoritesState = MutableStateFlow(setOf("fl-3"))
    }
}
