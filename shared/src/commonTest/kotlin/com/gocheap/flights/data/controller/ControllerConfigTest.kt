package com.gocheap.flights.data.controller

import com.gocheap.flights.data.model.controller.ControllerConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How `GET /controller/` is read, pinned because the defaults are a behavioural decision rather
 * than a formality: this call is allowed to fail, and what the app does when it has no answer is
 * decided entirely by the values below.
 */
class ControllerConfigTest {

    /** The flight client's own settings, which is what actually decodes the response. */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun the_switches_are_read_off_the_backends_own_key_names() {
        val decoded = json.decodeFromString(
            ControllerConfig.serializer(),
            """
            {
              "app": "D3AT",
              "country": "US",
              "route": "organic",
              "inverse_ad_logic": true,
              "current_tab": "flight",
              "new_tab": "hotel",
              "is_popup_ads_enabled": false,
              "is_ads_enabled": false,
              "is_app_open_popup_ad_enabled": false,
              "is_loader_ad_enabled": false
            }
            """.trimIndent(),
        )

        assertEquals("D3AT", decoded.app)
        assertEquals("hotel", decoded.newTab)
        assertEquals(true, decoded.inverseAdLogic)
        assertEquals(false, decoded.isAdsEnabled)
        assertEquals(false, decoded.isLoaderAdEnabled)
    }

    @Test
    fun a_switch_the_backend_leaves_out_defaults_to_on() {
        val decoded = json.decodeFromString(ControllerConfig.serializer(), """{"country":"GB"}""")

        // The whole point of the defaults: a response that mentions none of the ad switches, and a
        // failed call that stores ControllerConfig(), both have to mean "behave as though there
        // were no controller" — never "everything is off".
        assertTrue(decoded.isAdsEnabled == true)
        assertTrue(decoded.isPopupAdsEnabled == true)
        assertTrue(decoded.isAppOpenPopupAdEnabled == true)
        assertTrue(decoded.isLoaderAdEnabled == true)
    }

    @Test
    fun the_stored_default_is_the_same_answer_as_an_empty_response() {
        val fallback = ControllerConfig()

        assertEquals(true, fallback.isAdsEnabled)
        assertEquals(true, fallback.isPopupAdsEnabled)
        // Not a switch, so it has no sensible "on": nothing has told this build which tab to open.
        assertNull(fallback.currentTab)
        assertNull(fallback.inverseAdLogic)
    }

    @Test
    fun a_key_this_build_does_not_know_about_does_not_break_the_response() {
        // The backend adds switches without notice, and this call failing would take the stored
        // config back to defaults for the whole launch.
        val decoded = json.decodeFromString(
            ControllerConfig.serializer(),
            """{"country":"US","is_ads_enabled":false,"is_some_future_switch":true}""",
        )

        assertEquals(false, decoded.isAdsEnabled)
    }
}
