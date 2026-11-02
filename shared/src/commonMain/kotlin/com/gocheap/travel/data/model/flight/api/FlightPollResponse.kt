package com.gocheap.travel.data.model.flight.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One answer from `GET api/v3/poll/` — a page of priced itineraries, plus everything the results
 * screen needs to draw and filter them.
 *
 * A search does not return its results in one go. The backend fans out to suppliers and fills a
 * cache as they answer, so the same page is polled repeatedly and comes back fuller each time;
 * [cache] is how it says whether it is still filling. Three states, and they are three different
 * things:
 *
 * - `false` — still collecting. Draw what is here, poll again in a second.
 * - `true` — done. What is here is all there is for this page.
 * - `null` — **the search has expired.** Not "unknown": the backend drops a search's cache after
 *   a few minutes, and a poll against a dropped search answers with no cache field at all. The
 *   prices on screen are stale and the search has to be re-run from scratch. Reproduced from the
 *   reference app, where this is the only signal that a results page has gone cold.
 *
 * Every field is nullable because a poll answered before the cache has anything in it omits most
 * of them, and a partially-filled page is the normal case rather than a malformed one.
 */
@Serializable
internal data class FlightPollResponse(
    /** Total itineraries matching the current filters, across all pages. */
    @SerialName("count") val count: Int? = null,
    /** The next page's URL, or null when this is the last page. Only its presence is read. */
    @SerialName("next") val next: String? = null,
    @SerialName("previous") val previous: String? = null,
    /** Whether the backend has finished collecting. See the class comment — `null` means expired. */
    @SerialName("cache") val cache: Boolean? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("currency") val currency: CurrencyInfo? = null,
    @SerialName("priceMode") val priceMode: String? = null,
    @SerialName("passenger_count") val passengerCount: Int? = null,

    /** The bounds the duration and price filters open their sliders across. Durations in minutes. */
    @SerialName("min_duration") val minDuration: Int? = null,
    @SerialName("max_duration") val maxDuration: Int? = null,
    @SerialName("min_price") val minPrice: Double? = null,
    @SerialName("max_price") val maxPrice: Double? = null,

    /** Everything flown by, and everything bookable through, in this result set — the filter lists. */
    @SerialName("airlines") val airlines: List<FlightAirline>? = null,
    @SerialName("agencies") val agencies: List<FlightAgency>? = null,

    /** Lookups the result rows reference by code rather than repeat. */
    @SerialName("airline_details") val airlineDetails: Map<String, AirlineDetail>? = null,
    @SerialName("airport_details") val airportDetails: Map<String, AirportDetail>? = null,
    @SerialName("provider_details") val providerDetails: Map<String, ProviderDetail>? = null,

    /** The three headline itineraries, by price, by overall score and by duration. */
    @SerialName("cheapest_flight") val cheapestFlight: FlightSummary? = null,
    @SerialName("best_flight") val bestFlight: FlightSummary? = null,
    @SerialName("fastest_flight") val fastestFlight: FlightSummary? = null,

    @SerialName("results") val results: List<FlightOption>? = null,
) {
    /** True when the backend has dropped this search's cache and the prices on screen are stale. */
    val hasExpired: Boolean get() = cache == null

    /** True when the backend has finished collecting for this page. */
    val isComplete: Boolean get() = cache == true

    /** True when this is the last page: the backend named no page after it. */
    val isLastPage: Boolean get() = next == null
}

/** An airline in the filter list, as the backend names it. Keyed on [iata] by the filter state. */
@Serializable
internal data class FlightAirline(
    @SerialName("airlineName") val name: String? = null,
    @SerialName("airlineIata") val iata: String? = null,
    @SerialName("airlineLogo") val logoUrl: String? = null,
)

/** A booking site in the filter list. Keyed on [code]. */
@Serializable
internal data class FlightAgency(
    @SerialName("code") val code: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("image") val imageUrl: String? = null,
)

@Serializable
internal data class AirlineDetail(
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("logoUrl") val logoUrl: String? = null,
)

@Serializable
internal data class AirportDetail(
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("cityName") val cityName: String? = null,
)

@Serializable
internal data class ProviderDetail(
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("logoUrls") val logoUrls: ProviderLogoUrls? = null,
)

@Serializable
internal data class ProviderLogoUrls(
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("horizontalImageUrl") val horizontalImageUrl: String? = null,
    @SerialName("wideHorizontalImageUrl") val wideHorizontalImageUrl: String? = null,
)

/** The price and duration of one of the three headline itineraries. */
@Serializable
internal data class FlightSummary(
    @SerialName("price") val price: Double? = null,
    /** Minutes. */
    @SerialName("duration") val duration: Int? = null,
)

/**
 * One itinerary: every leg of the trip, and every way to book it.
 *
 * A return trip is one of these with two [legs], not two rows — the backend prices the journey as
 * a whole, and the cheapest outbound paired with the cheapest return is frequently not bookable.
 */
@Serializable
internal data class FlightOption(
    @SerialName("id") val id: String? = null,
    /** Minutes, across every leg. */
    @SerialName("total_duration") val totalDuration: Int? = null,
    @SerialName("min_price") val minPrice: Double? = null,
    @SerialName("max_price") val maxPrice: Double? = null,
    @SerialName("legs") val legs: List<FlightLeg>? = null,
    /** Who will sell it, cheapest first as the backend orders them. */
    @SerialName("providers") val providers: List<FlightProvider>? = null,
    @SerialName("is_best") val isBest: Boolean? = null,
    @SerialName("is_cheapest") val isCheapest: Boolean? = null,
    @SerialName("is_fastest") val isFastest: Boolean? = null,
) {
    /**
     * What the row shows as its price: the lowest any provider will sell it for.
     *
     * [minPrice] where the backend gave one, and the cheapest provider otherwise — the two agree
     * on a complete response, and a page still filling can carry providers before it carries the
     * summary.
     */
    val displayPrice: Double?
        get() = minPrice ?: providers?.mapNotNull { it.price }?.minOrNull()
}

/**
 * One hop of the trip — the outbound, or the return.
 *
 * [stopCount] is the backend's own count rather than `segments.size - 1`: the two agree today, but
 * the count is what the stops filter was applied against, so deriving it would let the row
 * disagree with the filter that selected it.
 */
@Serializable
internal data class FlightLeg(
    /** Epoch seconds, in the *airport's* local time — which is why the two ends are not comparable. */
    @SerialName("departureTimeAirport") val departureTimeAirport: Long? = null,
    @SerialName("arriveTimeAirport") val arriveTimeAirport: Long? = null,
    /**
     * Minutes. The honest elapsed time; subtracting the two timestamps above would not give it,
     * because each is local to its own airport and the two airports need not share a clock.
     */
    @SerialName("duration") val duration: Int? = null,
    @SerialName("origin") val origin: String? = null,
    @SerialName("originCode") val originCode: String? = null,
    @SerialName("destination") val destination: String? = null,
    @SerialName("destinationCode") val destinationCode: String? = null,
    @SerialName("stopCount") val stopCount: Int? = null,
    @SerialName("segments") val segments: List<FlightSegment>? = null,
) {
    /** The airlines actually flying this leg, in order, without repeats. */
    val operatingAirlines: List<String>
        get() = segments.orEmpty().mapNotNull { it.airlineName?.takeIf(String::isNotBlank) }.distinct()
}

/** One aircraft, between two airports. A direct leg has one; a leg with a stop has two. */
@Serializable
internal data class FlightSegment(
    @SerialName("id") val id: String? = null,
    @SerialName("departureTimeAirport") val departureTimeAirport: Long? = null,
    @SerialName("arriveTimeAirport") val arriveTimeAirport: Long? = null,
    /** Minutes. */
    @SerialName("duration") val duration: Int? = null,
    @SerialName("flightNumber") val flightNumber: String? = null,
    @SerialName("airlineName") val airlineName: String? = null,
    @SerialName("airlineIata") val airlineIata: String? = null,
    @SerialName("airlineLogo") val airlineLogo: String? = null,
    @SerialName("origin") val origin: String? = null,
    @SerialName("originCode") val originCode: String? = null,
    @SerialName("destination") val destination: String? = null,
    @SerialName("destinationCode") val destinationCode: String? = null,
    /** +1 when this segment lands the day after it took off. Drawn as the "+1" beside the time. */
    @SerialName("arrival_day_difference") val arrivalDayDifference: Int? = null,
    @SerialName("wifi") val wifi: Boolean? = null,
    @SerialName("cabinClass") val cabinClass: String? = null,
    @SerialName("aircraft") val aircraft: String? = null,
)

/**
 * One way to book an itinerary, at one price.
 *
 * [isSplit] marks a price assembled from two separate tickets — cheaper, but a missed connection
 * is the traveller's problem rather than the airline's, so the row says so. The individual
 * bookings are then in [deals], one per ticket; an unsplit provider has exactly one.
 */
@Serializable
internal data class FlightProvider(
    @SerialName("isSplit") val isSplit: Boolean? = null,
    @SerialName("transferType") val transferType: String? = null,
    @SerialName("price") val price: Double? = null,
    @SerialName("splitProviders") val deals: List<FlightDeal>? = null,
)

/**
 * A bookable offer: who sells it, for how much, and the link that opens their checkout.
 *
 * [deeplink] is what the provider screen loads. A deal without one cannot be booked and the UI
 * does not offer it.
 */
@Serializable
internal data class FlightDeal(
    @SerialName("name") val name: String? = null,
    @SerialName("imageURL") val imageUrl: String? = null,
    @SerialName("price") val price: Double? = null,
    @SerialName("deeplink") val deeplink: String? = null,
    @SerialName("rating") val rating: Double? = null,
    @SerialName("ratingCount") val ratingCount: Int? = null,
    @SerialName("fareFamily") val fareFamily: FareFamily? = null,
)

/** What the fare includes — bags, seat selection, whether it can be changed. */
@Serializable
internal data class FareFamily(
    @SerialName("code") val code: String? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("features") val features: List<FareFeature>? = null,
)

@Serializable
internal data class FareFeature(
    @SerialName("type") val type: String? = null,
    @SerialName("description") val description: String? = null,
)
