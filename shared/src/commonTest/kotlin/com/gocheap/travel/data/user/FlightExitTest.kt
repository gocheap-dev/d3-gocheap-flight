package com.gocheap.travel.data.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What `users/session/` is told a flight search did.
 *
 * Pinned because the endpoint validates this field against a closed set and answers an unknown
 * value with `400 {"msg":"Invalid type","type":"invalid_type"}` — which the app does not surface,
 * so a wrong word here means exits silently vanishing from the backend's numbers rather than
 * anything visibly breaking.
 *
 * The accepted set was established against the staging backend: `api` and `deeplink` are taken,
 * and `sdk`, `SDK`, `tpt_sdk`, `tp`, `travelpayouts`, `web`, `app` and `widget` are all refused.
 *
 * **`ads` was never among the words tried**, and is the one exit below that is not covered by
 * that finding — see [an_ad_exit_sends_the_reference_apps_word]. The carve-out is explicit rather
 * than folded into [accepted] so that widening the verified set stays a deliberate act: the whole
 * value of this test is that the set was measured and not guessed at.
 */
class FlightExitTest {

    /** The two words the endpoint was measured to accept. */
    private val accepted = setOf("api", "deeplink")

    @Test
    fun every_search_exit_sends_a_word_the_endpoint_accepts() {
        SessionTracking.FlightExit.entries
            .filter { it != SessionTracking.FlightExit.Ads }
            .forEach { exit ->
                assertTrue(
                    exit.wireValue in accepted,
                    "${exit.name} sends \"${exit.wireValue}\", which users/session/ rejects with 400",
                )
            }
    }

    /**
     * An ad exit sends `ads`, which is what the reference app sends on this exact path against
     * this exact backend (`AdResultViewModel`, `type = "ads"`, same `staging.data.lascade.com`).
     *
     * Not verified here, and it cannot be from a test: the endpoint validates `user_id` before it
     * looks at `type`, so establishing the answer needs a real registered id and writes a real
     * session row. Until someone does that, this pins the intent — and if the word turns out to
     * be refused, the symptom is a 400 in the `StartSession` log and ad exits missing from the
     * backend's numbers, with nothing else visibly wrong.
     */
    @Test
    fun an_ad_exit_sends_the_reference_apps_word() {
        assertEquals("ads", SessionTracking.FlightExit.Ads.wireValue)
    }

    @Test
    fun an_sdk_handoff_is_reported_as_a_deeplink() {
        // It is a search leaving the app, which is what `deeplink` means here. Sending "sdk" —
        // what this used to do — had the row rejected and the exit recorded nowhere.
        assertEquals("deeplink", SessionTracking.FlightExit.Sdk.wireValue)
    }

    @Test
    fun in_app_results_are_reported_as_api() {
        assertEquals("api", SessionTracking.FlightExit.Api.wireValue)
    }

    @Test
    fun the_sdk_and_deeplink_cases_stay_distinct_in_code() {
        // They share a wire value but not an identity: which one a search takes is the
        // controller's decision and is worth reading in logs.
        assertTrue(SessionTracking.FlightExit.Sdk != SessionTracking.FlightExit.Deeplink)
    }
}
