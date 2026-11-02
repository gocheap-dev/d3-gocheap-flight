package com.gocheap.travel.data.ads

import com.gocheap.travel.data.model.Airport
import com.gocheap.travel.data.model.CabinClass
import com.gocheap.travel.data.model.TripSearch
import com.gocheap.travel.data.model.flight.ads.FlightAdsRequest
import com.gocheap.travel.data.model.flight.ads.FlightAdsResponse
import com.gocheap.travel.ui.home.results.api.adLogoBackground
import com.gocheap.travel.ui.home.results.api.adLogoUrl
import com.gocheap.travel.ui.home.results.api.adSiteName
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ad path's three unforgiving pieces, pinned: the body `ads/flight/list` takes, what comes
 * back from it, and how an ad's relative URLs are resolved.
 *
 * All three fail silently in production and that is deliberate — see [AdsRepository]. The cost of
 * that choice is that nothing on screen reports a renamed key, a 404'd impression, or a trip
 * described in the wrong vocabulary; the ad simply does not appear, or appears and bills nobody.
 * A test is the only thing that notices.
 */
class FlightAdsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val london = Airport(code = "LHR", city = "London", name = "Heathrow", cityCode = "LON")
    private val newYork = Airport(code = "JFK", city = "New York", name = "JFK", cityCode = "NYC")

    private fun trip(
        returnDate: String? = null,
        isRoundTrip: Boolean = returnDate != null,
        adults: Int = 1,
        children: Int = 0,
        infants: Int = 0,
        cabinClass: CabinClass = CabinClass.Economy,
        departureDate: String = "14, mar 2026",
    ) = TripSearch(
        from = london,
        to = newYork,
        departureDate = departureDate,
        returnDate = returnDate,
        isRoundTrip = isRoundTrip,
        adults = adults,
        children = children,
        infants = infants,
        cabinClass = cabinClass,
    )

    @Test
    fun the_body_sends_the_keys_the_host_reads() {
        val body = json.encodeToString(FlightAdsRequest.serializer(), trip().toAdsRequest()!!)
        // Camel case, and `originAirport` rather than the search endpoint's `origin`: two
        // services, two vocabularies for the same trip.
        assertTrue("\"cabinClass\"" in body, body)
        assertTrue("\"passengers\"" in body, body)
        assertTrue("\"legs\"" in body, body)
        assertTrue("\"originAirport\"" in body, body)
        assertTrue("\"destinationAirport\"" in body, body)
        assertTrue("\"date\"" in body, body)
    }

    /**
     * The difference that is easiest to introduce by copying the search request builder, and
     * invisible once introduced: a search is sent "LON" and an ad request is sent "LHR".
     */
    @Test
    fun legs_carry_airports_not_the_cities_they_sit_in() {
        val request = trip().toAdsRequest()!!
        assertEquals("LHR", request.legs.single().originAirport)
        assertEquals("JFK", request.legs.single().destinationAirport)
    }

    @Test
    fun a_return_trip_is_a_second_leg_back() {
        val request = trip(returnDate = "21, mar 2026").toAdsRequest()!!
        assertEquals(2, request.legs.size)
        assertEquals("2026-03-14", request.legs[0].date)
        assertEquals("JFK", request.legs[1].originAirport)
        assertEquals("LHR", request.legs[1].destinationAirport)
        assertEquals("2026-03-21", request.legs[1].date)
    }

    @Test
    fun a_return_date_on_a_one_way_trip_is_not_sent() {
        val request = trip(returnDate = "21, mar 2026", isRoundTrip = false).toAdsRequest()!!
        assertEquals(1, request.legs.size)
    }

    /** Words, not the ages the search endpoint takes — and lap infants are in this list. */
    @Test
    fun the_party_is_one_word_per_traveller() {
        val request = trip(adults = 2, children = 1, infants = 1).toAdsRequest()!!
        assertEquals(listOf("adult", "adult", "child", "seatInfant"), request.passengers)
    }

    @Test
    fun a_party_with_no_adults_still_sends_one() {
        assertEquals(listOf("adult"), trip(adults = 0).toAdsRequest()!!.passengers)
    }

    @Test
    fun cabins_go_out_under_the_hosts_own_names() {
        assertEquals("economy", trip(cabinClass = CabinClass.Economy).toAdsRequest()!!.cabinClass)
        assertEquals(
            "premiumEconomy",
            trip(cabinClass = CabinClass.PremiumEconomy).toAdsRequest()!!.cabinClass,
        )
        assertEquals("business", trip(cabinClass = CabinClass.Business).toAdsRequest()!!.cabinClass)
        assertEquals("first", trip(cabinClass = CabinClass.First).toAdsRequest()!!.cabinClass)
    }

    /** Better no request than one the host rejects as a malformed leg. */
    @Test
    fun a_trip_whose_departure_date_does_not_parse_builds_nothing() {
        assertNull(trip(departureDate = "whenever").toAdsRequest())
    }

    @Test
    fun an_unparseable_return_date_drops_the_leg_rather_than_the_request() {
        val request = trip(returnDate = "whenever", isRoundTrip = true).toAdsRequest()
        assertEquals(1, request?.legs?.size)
    }

    /** The host answers 200 with the key absent when it has nothing, not with an empty array. */
    @Test
    fun a_response_with_no_ads_parses_as_none() {
        val parsed = json.decodeFromString(FlightAdsResponse.serializer(), "{}")
        assertNull(parsed.inlineItems)
    }

    @Test
    fun an_ad_parses_past_fields_this_app_does_not_know() {
        val parsed = json.decodeFromString(
            FlightAdsResponse.serializer(),
            """
            {"inlineItems":[{
              "site":"KAYAK",
              "deepLink":"/in?a=b",
              "impressionUrl":"/i/impression/123",
              "headline":"Cheap flights",
              "rank":1,
              "somethingAddedLastTuesday":{"nested":true}
            }]}
            """.trimIndent(),
        )
        val ad = parsed.inlineItems!!.single()
        assertEquals("KAYAK", ad.site)
        assertEquals("/i/impression/123", ad.impressionUrl)
        assertEquals(1, ad.rank)
    }

    @Test
    fun a_supplier_site_normalises_to_https_with_one_trailing_slash() {
        assertEquals("https://www.kayak.co.uk/", supplierDomain("https://www.kayak.co.uk/"))
        assertEquals("https://www.kayak.co.uk/", supplierDomain("https://www.kayak.co.uk"))
        assertEquals("https://www.kayak.co.uk/", supplierDomain("  www.kayak.co.uk  "))
        // An ad's URLs are reported over the open internet; http is upgraded rather than honoured.
        assertEquals("https://www.kayak.co.uk/", supplierDomain("http://www.kayak.co.uk"))
    }

    /** Three quarters of the region catalogue carries no domain at all. */
    @Test
    fun a_country_with_no_supplier_site_has_no_domain() {
        assertNull(supplierDomain(null))
        assertNull(supplierDomain(""))
        assertNull(supplierDomain("   "))
        assertNull(supplierDomain("https://"))
    }

    @Test
    fun an_ads_relative_url_resolves_against_the_country_site() {
        val domain = supplierDomain("https://www.kayak.co.uk/")
        assertEquals(
            "https://www.kayak.co.uk/i/impression/123",
            absoluteAdUrl("/i/impression/123", domain),
        )
        // Exactly one slash in the join, whichever side brought it.
        assertEquals(
            "https://www.kayak.co.uk/i/impression/123",
            absoluteAdUrl("i/impression/123", domain),
        )
    }

    @Test
    fun an_absolute_url_is_left_alone() {
        val domain = supplierDomain("https://www.kayak.co.uk/")
        assertEquals(
            "https://ads.example.com/i/1",
            absoluteAdUrl("https://ads.example.com/i/1", domain),
        )
    }

    /**
     * The case the null matters for: returning the bare path here would have the ads client
     * resolve it against the *ad host*, reporting the impression to a server that never served it.
     */
    @Test
    fun a_relative_url_with_no_country_site_resolves_to_nothing() {
        assertNull(absoluteAdUrl("/i/impression/123", null))
        assertNull(absoluteAdUrl(null, supplierDomain("https://www.kayak.co.uk/")))
        assertNull(absoluteAdUrl("   ", supplierDomain("https://www.kayak.co.uk/")))
    }
}

/**
 * The ad card's own string rules, pinned separately from the request and URL ones above.
 *
 * Each exists because the host sends something awkward and the card has to render anyway: a logo
 * URL with the brand's background baked into a query parameter, padding the card does not want,
 * and creatives that name no company at all.
 */
class FlightAdCardTest {

    @Test
    fun a_logo_resolves_against_the_country_site_with_the_hosts_padding_stripped() {
        assertEquals(
            "https://www.kayak.co.uk/i/logo.svg?bgColor=%23FF6600",
            adLogoUrl("/i/logo.svg?bgColor=%23FF6600&padX=5&padY=5", "https://www.kayak.co.uk/"),
        )
    }

    /**
     * The reference app strips the single literal `&padX=5&padY=5`. These are the shapes that
     * literal misses — different values, the parameter arriving first, only one of the pair —
     * each of which would otherwise reach the image service and come back padded.
     */
    @Test
    fun padding_is_dropped_whatever_its_values_or_position() {
        assertEquals(
            "https://www.kayak.co.uk/i/logo.svg?bgColor=%23FF6600",
            adLogoUrl("/i/logo.svg?padX=12&bgColor=%23FF6600&padY=12", "https://www.kayak.co.uk/"),
        )
        assertEquals(
            "https://www.kayak.co.uk/i/logo.svg?bgColor=%23FF6600",
            adLogoUrl("/i/logo.svg?padX=5&bgColor=%23FF6600", "https://www.kayak.co.uk/"),
        )
        // Nothing but padding: the `?` goes too, rather than leaving a dangling one.
        assertEquals(
            "https://www.kayak.co.uk/i/logo.svg",
            adLogoUrl("/i/logo.svg?padX=5&padY=5", "https://www.kayak.co.uk/"),
        )
    }

    /**
     * The one parameter the card reads back out of the URL, so its escaping has to survive the
     * strip — `%23` is the `#` of the brand's colour, and a re-encoded `%2523` reads as nothing.
     */
    @Test
    fun stripping_padding_leaves_the_brand_colour_readable() {
        val url = adLogoUrl("/i/logo.svg?bgColor=%23FF6600&padX=5&padY=5", "https://www.kayak.co.uk/")
        assertEquals(Color(0xFFFF6600), adLogoBackground(url))
    }

    @Test
    fun an_absolute_logo_is_left_alone_and_a_missing_one_is_null() {
        assertEquals(
            "https://cdn.example.com/logo.png",
            adLogoUrl("https://cdn.example.com/logo.png", "https://www.kayak.co.uk/"),
        )
        assertNull(adLogoUrl(null, "https://www.kayak.co.uk/"))
        assertNull(adLogoUrl("   ", "https://www.kayak.co.uk/"))
        // Nothing to resolve a relative path against, so the card falls back to the name.
        assertNull(adLogoUrl("/i/logo.svg", ""))
    }

    /** A logo drawn for a coloured background disappears on a white one. */
    @Test
    fun the_logo_chip_takes_the_brand_colour_out_of_the_url() {
        assertEquals(Color(0xFFFF6600), adLogoBackground("/i/logo.svg?bgColor=%23FF6600&padX=5"))
        assertEquals(Color(0xFF0A0A0A), adLogoBackground("/i/logo.svg?bgColor=%230a0a0a"))
    }

    @Test
    fun a_logo_with_no_usable_colour_has_no_chip() {
        assertNull(adLogoBackground(null))
        assertNull(adLogoBackground("/i/logo.svg"))
        assertNull(adLogoBackground("/i/logo.svg?bgColor=%23GGGGGG"))
        assertNull(adLogoBackground("/i/logo.svg?bgColor=%23FF66"))
    }

    @Test
    fun the_advertisers_name_falls_back_to_the_supplier_site() {
        assertEquals("Emirates", adSiteName("Emirates", "https://www.kayak.co.uk/"))
        assertEquals("Kayak", adSiteName(null, "https://www.kayak.co.uk/"))
        assertEquals("Kayak", adSiteName("  ", "https://www.kayak.com.ar/"))
        // Neither a company nor a site: still a word that reads as an ad.
        assertEquals("Sponsored", adSiteName(null, ""))
    }
}
