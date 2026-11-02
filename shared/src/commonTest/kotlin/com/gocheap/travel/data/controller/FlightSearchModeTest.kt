package com.gocheap.travel.data.controller

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which of the three ways a flight search is answered, as the backend's `new_tab` decides it.
 *
 * Pinned because the whole of the in-app results flow hangs off this one string match, and
 * because getting it wrong is invisible: an unrecognised value falls back to the SDK and the app
 * carries on working, just not the way the backend asked it to.
 */
class FlightSearchModeTest {

    @Test
    fun the_backends_own_values_each_route_somewhere() {
        // The three the backend is known to send — `KYK_API` is the reference app's own default.
        assertEquals(
            FlightSearchMode.Api,
            flightSearchModeFor("KYK_API"),
        )
        assertEquals(
            FlightSearchMode.Deeplink,
            flightSearchModeFor("KYK_DL"),
        )
        assertEquals(
            FlightSearchMode.Sdk,
            flightSearchModeFor("TPT_SDK"),
        )
    }

    @Test
    fun the_match_survives_a_prefix_or_a_version() {
        // Matched on the marker rather than the whole string, so a value the backend extends still
        // routes instead of silently falling back.
        assertEquals(
            FlightSearchMode.Api,
            flightSearchModeFor("KYK_API_V2"),
        )
        assertEquals(
            FlightSearchMode.Deeplink,
            flightSearchModeFor("NEW_KYK_DL"),
        )
    }

    @Test
    fun the_match_ignores_case() {
        assertEquals(
            FlightSearchMode.Api,
            flightSearchModeFor("kyk_api"),
        )
    }

    @Test
    fun api_is_not_shadowed_by_the_other_two_markers() {
        // The ordering guard: "KYK_API" carries neither "DL" nor "SDK" today, so this would pass
        // whatever the order — it is here so that a future value carrying both is noticed.
        val mode = flightSearchModeFor("KYK_API")
        assertEquals(FlightSearchMode.Api, mode)
    }

    @Test
    fun nothing_said_means_the_sdk() {
        // The controller call is allowed to fail and lands after the first frame either way. Every
        // unknown answer has to leave the search button working, and the SDK is what it did before
        // there was a choice at all.
        assertEquals(FlightSearchMode.Sdk, flightSearchModeFor(null))
        assertEquals(FlightSearchMode.Sdk, flightSearchModeFor(""))
        assertEquals(FlightSearchMode.Sdk, flightSearchModeFor("hotel"))
    }
}
