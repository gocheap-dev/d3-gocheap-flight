package com.gocheap.flights.data.flight

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How a failed flight API call is sorted into "retrying may help" and "retrying cannot".
 *
 * Pinned against the statuses the staging backend actually returns, because both ways of getting
 * this wrong are silent. A 403 read as transport puts the user on a "Try again" button that will
 * fail identically every time; a 503 read as unavailable bounces them off a search that would
 * have worked on the next attempt.
 */
class FlightSearchFailureTest {

    @Test
    fun a_disabled_provider_key_is_not_retryable() {
        // 403 {"error":"Provided API key is disabled"} — the app is known, its provider is off.
        assertEquals(FlightSearchFailure.Unavailable, flightSearchFailureFor(403))
    }

    @Test
    fun an_unconfigured_app_and_country_is_not_retryable() {
        // 423 {"error":"No API provider configured for this app/country"}.
        assertEquals(FlightSearchFailure.Unavailable, flightSearchFailureFor(423))
    }

    @Test
    fun server_and_gateway_failures_stay_retryable() {
        assertEquals(FlightSearchFailure.Transport, flightSearchFailureFor(500))
        assertEquals(FlightSearchFailure.Transport, flightSearchFailureFor(502))
        assertEquals(FlightSearchFailure.Transport, flightSearchFailureFor(503))
        assertEquals(FlightSearchFailure.Transport, flightSearchFailureFor(504))
    }

    @Test
    fun rate_limiting_stays_retryable() {
        // The one status that most obviously means "later", and the one it would be worst to
        // treat as permanent.
        assertEquals(FlightSearchFailure.Transport, flightSearchFailureFor(429))
    }

    @Test
    fun a_rejected_request_is_not_mistaken_for_an_unprovisioned_app() {
        // 400 is a malformed body — a bug here, not a backend switch. It stays in the retryable
        // bucket rather than silently redirecting the user away from a fixable fault.
        assertEquals(FlightSearchFailure.Transport, flightSearchFailureFor(400))
        assertEquals(FlightSearchFailure.Transport, flightSearchFailureFor(401))
        assertEquals(FlightSearchFailure.Transport, flightSearchFailureFor(404))
    }
}
