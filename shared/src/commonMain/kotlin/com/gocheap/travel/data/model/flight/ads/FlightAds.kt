package com.gocheap.travel.data.model.flight.ads

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What `GET ads/session` answers with.
 *
 * One field, and it is nullable: the host answers 200 with an empty object when it has no session
 * to give, so "the call succeeded" and "there is an id" are two different questions — see
 * [com.gocheap.travel.data.ads.AdsRepository].
 */
@Serializable
internal data class AdSessionResponse(
    @SerialName("sid") val sid: String? = null,
)

/**
 * The body `POST ads/flight/list` takes: the trip to find ads for.
 *
 * The same trip `api/v3/search/` is sent, in the ad host's own vocabulary — which is a different
 * one. Three differences, all deliberate, none worth reconciling here because the two endpoints
 * belong to different services:
 *
 * - **Airports, not cities.** [FlightAdsLeg.originAirport] is sent the code the user picked.
 *   [com.gocheap.travel.data.model.flight.api.FlightSearchRequest] sends the *city* instead,
 *   because the search host groups by city and finds nothing under an airport inside one.
 * - **Words, not ages.** [passengers] is `["adult", "child"]` where the search request sends
 *   `[34, 11]`.
 * - **Lap infants are in the list.** They are left out of a search request, which prices seats;
 *   an ad is not priced, so the party is the whole party.
 *
 * Built by [com.gocheap.travel.data.ads.AdsRepository] out of the app's own
 * [com.gocheap.travel.data.model.TripSearch]; nothing constructs it by hand.
 */
@Serializable
internal data class FlightAdsRequest(
    @SerialName("cabinClass") val cabinClass: String,
    /** One entry per traveller — see [com.gocheap.travel.utils.Constants.Ads.Adult]. */
    @SerialName("passengers") val passengers: List<String>,
    /** Outbound first; a return trip sends two. */
    @SerialName("legs") val legs: List<FlightAdsLeg>,
)

/** One hop of the trip, by airport code and date. */
@Serializable
internal data class FlightAdsLeg(
    @SerialName("originAirport") val originAirport: String,
    @SerialName("destinationAirport") val destinationAirport: String,
    /** `yyyy-MM-dd`, as the search endpoints take. */
    @SerialName("date") val date: String,
)

/**
 * What `POST ads/flight/list` answers with.
 *
 * Nullable all the way down because the host says so: a trip with nothing to show for it answers
 * 200 with `inlineItems` absent rather than with an empty array, and an item that is present may
 * still be missing any field on it.
 */
@Serializable
internal data class FlightAdsResponse(
    @SerialName("inlineItems") val inlineItems: List<InlineAdItem>? = null,
)

/**
 * One ad, as the host describes it.
 *
 * Carried whole rather than mapped into a model of this app's own: every field is a string the ad
 * is required to be rendered with, the host adds to the set without notice, and there is no
 * decision to make about any of them on the way in.
 *
 * Three of them are URLs and they are not alike:
 *
 * - [deepLink] and [impressionUrl] are **relative to the supplier's site for the user's country**,
 *   not to the ad host. Absolutising them is [com.gocheap.travel.data.ads.AdsRepository]'s job —
 *   requesting one against the wrong host is a 404, and against no host at all is a crash.
 * - [logoUrl] and [backgroundImageUrl] are absolute and load as they are.
 */
@Serializable
internal data class InlineAdItem(
    /** The supplier being advertised, e.g. "KAYAK". Reported as the provider on an exit. */
    @SerialName("site") val site: String? = null,
    /** Where tapping the ad goes. Relative — see the class comment. */
    @SerialName("deepLink") val deepLink: String? = null,
    /** Reported once the ad has been on screen. Relative — see the class comment. */
    @SerialName("impressionUrl") val impressionUrl: String? = null,
    @SerialName("trackUrl") val trackUrl: String? = null,
    @SerialName("companyName") val companyName: String? = null,
    @SerialName("headline") val headline: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("bookingButtonText") val bookingButtonText: String? = null,
    @SerialName("logoUrl") val logoUrl: String? = null,
    @SerialName("backgroundImageUrl") val backgroundImageUrl: String? = null,
    @SerialName("productType") val productType: String? = null,
    /** The host's own ordering. Lower is better; the first item is the one to show. */
    @SerialName("rank") val rank: Int? = null,
)
