package com.gocheap.flights.sdk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.gocheap.flights.data.model.CabinClass
import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.data.model.TripSearch

/**
 * Hands the search to the Travelpayouts SDK through the handler the iOS app registered.
 *
 * Nothing composition-scoped is needed the way Android needs a Context — the SDK presents from the
 * app's own root controller — so this only normalises the search and asks the handler to take it.
 * A search the SDK could not run (a date that will not parse, or both ends naming one place) is
 * refused here rather than handed over to fail inside the SDK.
 */
@Composable
actual fun rememberFlightResultsLauncher(): (TripSearch) -> Boolean = remember {
    { search ->
        val handler = FlightResultsHandoff.handler
        val request = search.toSdkSearch()
        if (handler == null || request == null) false else handler.show(request)
    }
}

private fun TripSearch.toSdkSearch(): SdkFlightSearch? {
    val departure = CalendarDate.parse(departureDate) ?: return null
    // A one-way trip carries no return date, and a return before departure is not one either.
    val returning = returnDate
        ?.takeIf { isRoundTrip }
        ?.let { CalendarDate.parse(it) }
        ?.takeIf { it >= departure }

    if (from.code.isBlank() || to.code.isBlank()) return null
    if (from.code.equals(to.code, ignoreCase = true)) return null

    return SdkFlightSearch(
        originIata = from.code,
        destinationIata = to.code,
        departureDate = departure.isoDate,
        returnDate = returning?.isoDate,
        adults = adults.coerceAtLeast(1),
        children = children,
        infants = infants,
        tripClass = cabinClass.tripClassCode,
    )
}

/**
 * The fare classes the search runs on, which are the airline codes rather than the SDK's own names:
 * economy, premium economy, business, first. The SDK's results screen reads these directly.
 */
private val CabinClass.tripClassCode: String
    get() = when (this) {
        CabinClass.Economy -> "Y"
        CabinClass.PremiumEconomy -> "W"
        CabinClass.Business -> "C"
        CabinClass.First -> "F"
    }
