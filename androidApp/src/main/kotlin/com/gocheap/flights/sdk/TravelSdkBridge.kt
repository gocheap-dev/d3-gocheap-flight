package com.gocheap.flights.sdk

import android.content.Context
import android.util.Log
import com.gocheap.flights.data.model.Airport
import com.gocheap.flights.data.model.CabinClass
import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.data.model.TripSearch
import com.travelapp.sdk.internal.domain.flights.FlightType
import com.travelapp.sdk.internal.domain.flights.LocationInfo
import com.travelapp.sdk.internal.domain.flights.PassengersInfo
import com.travelapp.sdk.internal.domain.flights.Type
import com.travelapp.sdk.internal.domain.flights.isNotSameLocation
import com.travelapp.sdk.unlimited.config.TravelSdkUnlimited
import java.time.LocalDate

/**
 * Translates this app's search card into the Travelpayouts SDK's own search, and opens its results.
 *
 * This is the whole of the integration's surface. The SDK also ships its own screens for picking
 * airports, dates and passengers — this app keeps its own for all three and only borrows the
 * results, which is what [TravelSdkUnlimited.showFlightsSearchResultScreen] is for.
 */
object TravelSdkBridge : FlightResultsHandoff.Handler {

    private const val LogTag = "TravelSdk"

    /**
     * Registered only once the SDK has initialised, so "is there a handler" and "can the SDK take a
     * search" are the same question. With no credentials configured nothing is registered here and
     * every search falls back to the app's own results page.
     */
    fun register() {
        FlightResultsHandoff.handler = this
    }

    /**
     * Returns false when the SDK cannot take this search — a date that will not parse, or both ends
     * naming the same place — leaving the caller to fall back to the app's own results page rather
     * than opening a screen that would show an error.
     */
    override fun show(context: Context, search: TripSearch): Boolean {
        val departure = CalendarDate.parse(search.departureDate)?.toLocalDate() ?: return false
        // A one-way search carries no return date, and the SDK reads null as exactly that.
        val returning = search.returnDate
            ?.takeIf { search.isRoundTrip }
            ?.let { CalendarDate.parse(it)?.toLocalDate() }
            ?.takeIf { it >= departure }

        val from = search.from.toLocationInfo()
        val to = search.to.toLocationInfo()
        // The SDK ships this check for the case it cannot search: a route from a place to itself.
        if (!from.isNotSameLocation(to)) return false

        return try {
            TravelSdkUnlimited.showFlightsSearchResultScreen(
                context = context,
                fromLocationInfo = from,
                toLocationInfo = to,
                datesFlightsInfo = TravelSdkModels.dates(departure, returning),
                passengersInfo = search.toPassengersInfo(),
            )
            true
        } catch (error: Throwable) {
            // Whatever went wrong inside the SDK, the user still asked for results — so say so and
            // let the caller fall back rather than leaving the search button doing nothing.
            Log.e(LogTag, "Could not open the SDK's flight results.", error)
            false
        }
    }

    /**
     * One end of the route, in the two forms the SDK wants it in.
     *
     * It searches on the code and the type, and then reads its own response back by city code —
     * the results come grouped by city, and a city it cannot find in them throws from inside its
     * results view model, on a coroutine nothing here can catch, taking the app down with it. So
     * an airport that sits inside a metropolitan area has to go out under that area's code:
     * Heathrow searched as LHR, but looked up as LON.
     *
     * Which is what the picker's rows already say — the supplier sends an airport's city as
     * `parent_iata` — and what [Airport.cityCode] carries over. A row that names a city, or an
     * airport the supplier gave no parent for, keeps going out exactly as it did before: its own
     * code, as a city.
     */
    private fun Airport.toLocationInfo(): LocationInfo {
        val cityCode = searchCityCode
        val type = if (cityCode == code) Type.CITY else Type.AIRPORT
        return TravelSdkModels.location(type, code, name, cityCode, city)
    }

    /**
     * The party goes out split, as the card now collects it: children sit in their own seats and
     * infants on a lap, and the SDK fares all three differently.
     *
     * The SDK knows two cabins where the card offers four, so the premium cabins fold into the one
     * they are sold beside — premium economy searches economy, first searches business — which is
     * the closest fare class it can actually search rather than silently dropping the choice.
     */
    private fun TripSearch.toPassengersInfo(): PassengersInfo {
        val flightType = when (cabinClass) {
            CabinClass.Business, CabinClass.First -> FlightType.BUSINESS
            else -> FlightType.ECONOMY
        }
        return TravelSdkModels.passengers(
            adults.coerceAtLeast(1),
            children,
            infants,
            flightType,
        )
    }

    /** The SDK dates in java.time; minSdk is 26, so this needs no desugaring. */
    private fun CalendarDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)
}
