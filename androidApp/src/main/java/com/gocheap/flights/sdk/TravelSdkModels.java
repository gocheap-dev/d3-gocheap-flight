package com.gocheap.flights.sdk;

import com.travelapp.sdk.internal.domain.common.calendar.SelectedDates;
import com.travelapp.sdk.internal.domain.flights.FlightType;
import com.travelapp.sdk.internal.domain.flights.LocationInfo;
import com.travelapp.sdk.internal.domain.flights.PassengersInfo;
import com.travelapp.sdk.internal.domain.flights.Type;

import java.time.LocalDate;

/**
 * Builds the three models the Travelpayouts SDK takes to start a flight search.
 *
 * In Java, and only for that reason: the SDK marks these constructors {@code internal}, because it
 * expects the values to come back from its own airport, calendar and passenger screens. This app
 * has those screens already and the brief was to keep them, so the models are built here instead.
 * {@code internal} is a Kotlin-compiler rule rather than a bytecode one — the constructors are
 * plain public to the JVM — so a Java caller may do what a Kotlin one may not.
 *
 * The cost of that is this file: if a future SDK release changes these signatures it will break
 * here, loudly, at compile time rather than silently at runtime. Everything else in the
 * integration — initialising the SDK, opening its results — uses its public API.
 */
final class TravelSdkModels {

    private TravelSdkModels() {
    }

    /**
     * One end of the route, as the SDK's two uses of it need:
     *
     * <ul>
     *   <li>{@code iata} with {@code type} is what it searches on — an airport type searches that
     *       one airport, a city type every airport serving the city.</li>
     *   <li>{@code cityCode} is what it looks the result up under. The search response groups
     *       flights by city, so this has to be Travelpayouts' code for the city the place belongs
     *       to — LON for Heathrow, not LHR — or the SDK finds nothing to show and throws.</li>
     * </ul>
     *
     * The names are what the SDK prints in its own header. Country and coordinates are optional to
     * the search and this app does not hold them.
     */
    static LocationInfo location(Type type, String iata, String name, String cityCode, String cityName) {
        return new LocationInfo(
                type,
                iata,
                name,
                cityCode,
                cityName,
                "",
                "",
                null,
                null
        );
    }

    /** A null {@code returnDate} is how the SDK reads a one-way search. */
    static SelectedDates.FlightDates dates(LocalDate departDate, LocalDate returnDate) {
        return new SelectedDates.FlightDates(departDate, returnDate);
    }

    /**
     * The fourth count is the total the SDK prints on its own header, which it does not derive —
     * so it is summed here rather than left at the adult count.
     */
    static PassengersInfo passengers(int adults, int children, int infants, FlightType flightType) {
        return new PassengersInfo(adults, children, infants, adults + children + infants, flightType);
    }
}
