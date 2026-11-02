package com.gocheap.travel.analytics.ga4

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The GA4 vocabulary, pinned.
 *
 * Nothing here is checked at runtime: Firebase answers an over-long event name or a reserved one by
 * dropping the event, silently, and the app carries on exactly as if it had been sent. So the
 * failure mode this guards against is an event that simply never appears in the dashboard and whose
 * absence nobody notices for a month — which is why these limits are asserted here rather than
 * trusted to review.
 */
class AnalyticsEventTest {

    /**
     * Firebase refuses to log these; a custom event that collides with one is dropped on arrival.
     *
     * Not the full reserved list — only the names something in this app might plausibly reach for.
     * `screen_view` is deliberately absent: it is automatically collected *and* explicitly allowed
     * to be logged by hand, which is what [AnalyticsEvent.SCREEN_VIEW] does.
     */
    private val reserved = setOf(
        "ad_activeview", "ad_click", "ad_exposure", "ad_impression", "ad_query",
        "app_background", "app_clear_data", "app_exception", "app_open", "app_remove",
        "app_update", "error", "first_open", "first_visit", "in_app_purchase",
        "notification_dismiss", "notification_foreground", "notification_open",
        "notification_receive", "os_update", "session_start", "user_engagement",
    )

    /** Every event name this app sends. Listed by hand — common Kotlin has no reflection. */
    private val eventNames = listOf(
        AnalyticsEvent.SCREEN_VIEW,
        AnalyticsEvent.APP_LAUNCH,
        AnalyticsEvent.APP_CLOSE,
        AnalyticsEvent.ONBOARDING_COMPLETE,
        AnalyticsEvent.SEARCH_BUTTON_CLICKED,
        AnalyticsEvent.SEARCH_FAILED,
        AnalyticsEvent.PROVIDER_BUTTON,
        AnalyticsEvent.EXIT_CLICK,
        AnalyticsEvent.SETTING_CHANGED,
    )

    /** Every parameter key this app sends. Listed by hand, for the same reason. */
    private val parameterKeys = listOf(
        AnalyticsEvent.TEXT, AnalyticsEvent.TYPE, AnalyticsEvent.SCREEN_NAME,
        AnalyticsEvent.REGION, AnalyticsEvent.CURRENCY_CODE, AnalyticsEvent.LANGUAGE_CODE,
        AnalyticsEvent.PROVIDER_NAME, AnalyticsEvent.LOCATION, AnalyticsEvent.ARRIVAL_LOCATION,
        AnalyticsEvent.DROP_OFF_LOCATION, AnalyticsEvent.DATE, AnalyticsEvent.RETURN_DATE,
        AnalyticsEvent.CHECKOUT_DATE, AnalyticsEvent.DROP_OFF_DATE, AnalyticsEvent.TIME,
        AnalyticsEvent.DROP_OFF_TIME, AnalyticsEvent.COUNT, AnalyticsEvent.CHILDREN_COUNT,
        AnalyticsEvent.ROOM_COUNT, AnalyticsEvent.TRAVEL_CLASS, AnalyticsEvent.INDEX,
        AnalyticsEvent.PRICE, AnalyticsEvent.REASON, AnalyticsEvent.SETTING,
        AnalyticsEvent.VALUE, AnalyticsEvent.PLATFORM,
    )

    @Test
    fun no_event_name_collides_with_one_firebase_reserves() {
        eventNames.filter { it != AnalyticsEvent.SCREEN_VIEW }.forEach { name ->
            assertTrue(name !in reserved, "\"$name\" is reserved by Firebase and would be dropped")
        }
    }

    @Test
    fun every_name_fits_what_ga4_accepts() {
        // 40 characters, letters digits and underscores, starting with a letter. The SDKs reject
        // anything else outright.
        val allowed = Regex("[a-z][a-z0-9_]*")
        (eventNames + parameterKeys).forEach { name ->
            assertTrue(name.length <= 40, "\"$name\" is ${name.length} characters; the limit is 40")
            assertTrue(allowed.matches(name), "\"$name\" is not a legal GA4 name")
        }
    }

    @Test
    fun no_parameter_key_uses_a_prefix_google_reserves() {
        parameterKeys.forEach { key ->
            listOf("firebase_", "google_", "ga_").forEach { prefix ->
                assertTrue(!key.startsWith(prefix), "\"$key\" starts with the reserved \"$prefix\"")
            }
        }
    }

    @Test
    fun names_are_unique() {
        // A duplicate would not fail anything — it would merge two events into one, which reads in
        // the dashboard as a real number rather than as a mistake.
        assertEquals(eventNames.size, eventNames.toSet().size, "two events share a name")
        assertEquals(parameterKeys.size, parameterKeys.toSet().size, "two parameters share a key")
    }
}

/**
 * Turning a navigation route into a reporting name.
 *
 * The arguments are the point: `calendar/range/Travel+dates/2026-01-01/...` differs per search, and
 * a name built from the whole route would give GA4 a new screen for every date anyone ever picked.
 */
class ScreenNameForRouteTest {

    @Test
    fun arguments_are_stripped_before_the_route_is_named() {
        assertEquals(
            AnalyticsEvent.CALENDAR_SCREEN,
            screenNameForRoute("calendar/range/Travel+dates/2026-01-01/2026-01-08/false/none/none/days"),
        )
        assertEquals(
            AnalyticsEvent.REGION_PICKER_SCREEN,
            screenNameForRoute("region_picker/Currency"),
        )
        assertEquals(
            AnalyticsEvent.LOCATION_PICKER_SCREEN,
            screenNameForRoute("location_picker/Airport/Flying+from/origin/none"),
        )
    }

    @Test
    fun a_query_string_is_stripped_too() {
        assertEquals(AnalyticsEvent.HOME_SCREEN, screenNameForRoute("home?tab=flights"))
    }

    @Test
    fun the_three_web_results_pages_share_one_name() {
        // What a report asks is whether the user saw results this app drew or a supplier's own
        // page. Which vertical it was travels as a parameter on the events fired from them.
        val webResults = listOf("flight_results", "hotel_results", "car_results", "search_results")
        webResults.forEach { route ->
            assertEquals(AnalyticsEvent.CUSTOM_WEB_SCREEN, screenNameForRoute(route), route)
        }
    }

    @Test
    fun in_app_flight_results_are_named_apart_from_the_web_ones() {
        assertEquals(AnalyticsEvent.RESULT_SCREEN, screenNameForRoute("flight_api_results"))
    }

    @Test
    fun every_tab_has_a_name_of_its_own() {
        assertEquals(AnalyticsEvent.HOME_SCREEN, screenNameForRoute("home"))
        assertEquals(AnalyticsEvent.HOTEL_SCREEN, screenNameForRoute("hotels"))
        assertEquals(AnalyticsEvent.RENTAL_SCREEN, screenNameForRoute("cars"))
        assertEquals(AnalyticsEvent.PROFILE_SCREEN, screenNameForRoute("profile"))
    }

    @Test
    fun an_unnamed_route_falls_back_to_the_route_itself() {
        // Deliberate: a destination added later shows up in the dashboard under its route, which
        // is a prompt to name it. A silent empty string would not be.
        assertEquals("some_new_screen", screenNameForRoute("some_new_screen/42"))
    }

    @Test
    fun nothing_is_reported_for_no_route_at_all() {
        // `currentBackStackEntryAsState` is null before the graph has settled, and a screen_view
        // with an empty name is worse than none.
        assertNull(screenNameForRoute(null))
        assertNull(screenNameForRoute(""))
        assertNull(screenNameForRoute("   "))
    }
}
