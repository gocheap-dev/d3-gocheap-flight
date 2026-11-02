package com.gocheap.travel.analytics.ga4

/**
 * Every name GA4 ever sees from this app, in one place.
 *
 * Constants rather than literals at the call sites because a reporting name is a contract with
 * whoever reads the dashboard: a typo in one of these does not fail a build or a test, it quietly
 * splits one funnel into two and nobody notices for a month. Grouped the way GA4 itself separates
 * them — the event, the parameter keys it carries, the values those keys are allowed to take, and
 * the screen vocabulary — so a new event can be checked against what already exists before it is
 * added.
 *
 * Nothing here reaches a platform SDK; [FlightAnalytics] does that. This file is only the words.
 */
object AnalyticsEvent {

    /* ----------------------------------- Event names ----------------------------------- */

    /**
     * A screen coming up, sent for every destination in the graph and for the sheets that are not
     * destinations. The one event GA4 also collects by itself — logged by hand as well because the
     * automatic one is named after the Activity or the UIViewController, and this app has exactly
     * one of each with its entire UI inside it.
     */
    const val SCREEN_VIEW = "screen_view"

    /** The launch. Not `app_open`, which Firebase reserves for its own automatic event. */
    const val APP_LAUNCH = "app_launch"

    /** The app going to the background, with whichever screen the user left behind. */
    const val APP_CLOSE = "app_close"

    /** The intro pages being finished or skipped; [TYPE] says which. */
    const val ONBOARDING_COMPLETE = "onboarding_complete"

    /** A search card's button, for all three verticals; [TYPE] says which one. */
    const val SEARCH_BUTTON_CLICKED = "search_button_clicked"

    /** A search that produced no results, or that the supplier refused outright. */
    const val SEARCH_FAILED = "search_failed"

    /** A named booking site being opened off the in-app results list. */
    const val PROVIDER_BUTTON = "provider_button"

    /** The user leaving for a supplier — the moment the app stops being where the money is made. */
    const val EXIT_CLICK = "exit_click"

    /** A region, currency, language or price-display choice on the Profile tab. */
    const val SETTING_CHANGED = "setting_changed"

    /**
     * An inline ad reaching the screen — the event the ad host is paid on, reported once per ad
     * actually seen. Carries [TYPE] to say which placement it was: [POPUP_AD] or [NATIVE_AD].
     *
     * Fired beside the ad host's own impression URL, never instead of it: the two count different
     * things for different people, and only the host's call is billable. See `AdsRepository`.
     */
    const val AD_IMPRESSION_NATIVE = "ad_impression_native"

    /** An inline ad being tapped. Carries the same [TYPE] its impression did. */
    const val AD_CLICK_NATIVE = "ad_click_native"

    /* --------------------------------- Parameter keys ---------------------------------- */

    const val TEXT = "text"
    const val TYPE = "type"
    /**
     * Also the key GA4 builds its own screen reports on — it arrives in the console as
     * `ga_screen`. So this is not just one of this app's parameters: renaming it would take
     * every screen report with it.
     */
    const val SCREEN_NAME = "screen_name"
    const val REGION = "region"
    const val CURRENCY_CODE = "currency_code"
    const val LANGUAGE_CODE = "language_code"
    const val PROVIDER_NAME = "provider_name"
    /**
     * Which supplier an *ad* is for, as opposed to [PROVIDER_NAME], which is the booking site
     * behind a deal in the results list.
     *
     * Two keys for what looks like one question, and deliberately so: this is the reference app's
     * name for it on the ad events, and the ad reports are read across the publisher's apps
     * together. Folding ads onto [PROVIDER_NAME] here would make this build's ad revenue the one
     * that does not line up with the others.
     */
    const val PROVIDER = "provider"
    const val LOCATION = "location"
    const val ARRIVAL_LOCATION = "arrival_location"
    const val DROP_OFF_LOCATION = "dropoff_location"
    const val DATE = "date"
    const val RETURN_DATE = "return_date"
    const val CHECKOUT_DATE = "checkout_date"
    const val DROP_OFF_DATE = "dropoff_date"
    const val TIME = "time"
    const val DROP_OFF_TIME = "dropoff_time"
    const val COUNT = "count"
    const val CHILDREN_COUNT = "children_count"
    const val ROOM_COUNT = "room_count"
    const val TRAVEL_CLASS = "travel_class"
    const val INDEX = "index"
    const val PRICE = "price"
    const val REASON = "reason"
    const val SETTING = "setting"
    const val VALUE = "value"
    const val PLATFORM = "platform"

    /* -------------------------------- Parameter values --------------------------------- */

    /** Which vertical a search, an exit or a failure belongs to. */
    const val FLIGHT = "flight"
    const val STAY = "stay"
    const val RENTAL = "rental"

    /** How a flight search left the app, which is the backend's choice and not this build's. */
    const val EXIT_SDK = "sdk"
    const val EXIT_DEEPLINK = "deeplink"
    const val EXIT_API = "api"

    /**
     * Where an ad was shown, which is the only thing separating the two placements in a report.
     *
     * [POPUP_AD] is the card on the Home tab and [NATIVE_AD] the one inside the flight results
     * list. The spelling of the first is the reference app's — `pop_up_ad`, with the underscore —
     * and is kept rather than tidied, for the same reason [PROVIDER] is.
     */
    const val POPUP_AD = "pop_up_ad"
    const val NATIVE_AD = "native_ad"

    /** Where a search was started from, which separates a deliberate search from a one-tap one. */
    const val SEARCH_FORM = "search_form"
    const val POPULAR_LOCATION = "popular_location"
    const val RECENT_SEARCH = "recent_search"

    /** How the intro pages ended. */
    const val FINISHED = "finished"
    const val SKIPPED = "skipped"

    /** Which trip a flight search is for. */
    const val ROUND_TRIP = "round_trip"
    const val ONE_WAY = "one_way"

    /** Whether a rental is returned where it was collected. */
    const val SAME_DROP_OFF = "same drop-off"
    const val DIFFERENT_DROP_OFF = "different drop-off"

    /** What [SETTING_CHANGED] changed. */
    const val SETTING_COUNTRY = "country"
    const val SETTING_CURRENCY = "currency"
    const val SETTING_LANGUAGE = "language"
    const val SETTING_FLIGHT_PRICES = "flight_prices"
    const val SETTING_STAY_PRICES = "stay_prices"

    /** Why a search produced nothing. */
    const val REASON_EMPTY = "empty"
    const val REASON_UNAVAILABLE = "unavailable"
    const val REASON_NETWORK = "network"

    /* ----------------------------------- Screen names ---------------------------------- */

    const val SPLASH_SCREEN = "splash_screen"
    const val ONBOARDING_SCREEN = "onboarding_screen"
    const val HOME_SCREEN = "home_screen"
    const val HOTEL_SCREEN = "hotel_screen"
    const val RENTAL_SCREEN = "rental_screen"
    const val PROFILE_SCREEN = "profile_screen"
    const val FAVORITES_SCREEN = "favorites_screen"
    const val MY_TICKETS_SCREEN = "my_tickets_screen"
    const val TICKET_SCREEN = "ticket_screen"
    const val PRICE_DISPLAY_SCREEN = "price_display_screen"
    const val REGION_PICKER_SCREEN = "region_picker_screen"
    const val ABOUT_APP_SCREEN = "about_app_screen"
    const val CALENDAR_SCREEN = "calendar_screen"
    const val LOCATION_PICKER_SCREEN = "location_picker_screen"
    const val HOTEL_DESTINATION_SCREEN = "hotel_destination_screen"
    const val CAR_LOCATION_SCREEN = "car_location_screen"
    const val GUEST_PICKER_SCREEN = "guest_picker_screen"
    const val TRAVELLER_PICKER_SCREEN = "traveller_picker_screen"

    /** Flight results drawn in-app, from the search API this app polls itself. */
    const val RESULT_SCREEN = "result_screen"

    /** Results that are a supplier's own page inside a web view — flights, stays and rentals. */
    const val CUSTOM_WEB_SCREEN = "custom_web_screen"

    /** The booking site's checkout, opened for one deal off [RESULT_SCREEN]. */
    const val PROVIDER_SCREEN = "provider_screen"

    /* ------------------------- Screen names for the sheets ----------------------------- */

    /** The filter and sort sheets over the results list, which are not navigation destinations. */
    const val FILTER_SCREEN = "filter_screen"

    /** The sheet that re-opens the search form over the results list. */
    const val MODIFY_SEARCH_SCREEN = "modify_search_screen"

    /** The sheet listing every booking site selling one itinerary. */
    const val DEALS_SCREEN = "deals_screen"
}

/**
 * The reporting name for a navigation route.
 *
 * Routes carry their arguments — `calendar/range/Travel+dates/...` — and GA4 would treat each
 * distinct argument string as its own screen, which turns one page into thousands of rows. So the
 * arguments are cut off first and the bare route is translated.
 *
 * The three results routes deliberately collapse onto two names rather than four: what a report
 * asks about is whether the user saw results this app drew or a supplier's own page, and
 * `hotel_results` and `car_results` are the same page as `flight_results` in that respect. The
 * vertical travels as a parameter on the events fired from them instead.
 *
 * An unrecognised route is returned as it stands rather than dropped: a destination added later
 * then shows up in the dashboard under its own route name, which is a prompt to name it here —
 * where a silent empty string would not be.
 */
fun screenNameForRoute(route: String?): String? {
    val bare = route?.substringBefore("?")?.substringBefore("/")?.takeIf { it.isNotBlank() }
        ?: return null
    return when (bare) {
        "splash" -> AnalyticsEvent.SPLASH_SCREEN
        "onboarding" -> AnalyticsEvent.ONBOARDING_SCREEN
        "home" -> AnalyticsEvent.HOME_SCREEN
        "hotels" -> AnalyticsEvent.HOTEL_SCREEN
        "cars" -> AnalyticsEvent.RENTAL_SCREEN
        "profile" -> AnalyticsEvent.PROFILE_SCREEN
        "favorites" -> AnalyticsEvent.FAVORITES_SCREEN
        "my_tickets" -> AnalyticsEvent.MY_TICKETS_SCREEN
        "ticket" -> AnalyticsEvent.TICKET_SCREEN
        "price_display" -> AnalyticsEvent.PRICE_DISPLAY_SCREEN
        "region_picker" -> AnalyticsEvent.REGION_PICKER_SCREEN
        "about_app" -> AnalyticsEvent.ABOUT_APP_SCREEN
        "calendar" -> AnalyticsEvent.CALENDAR_SCREEN
        "location_picker" -> AnalyticsEvent.LOCATION_PICKER_SCREEN
        "hotel_destination" -> AnalyticsEvent.HOTEL_DESTINATION_SCREEN
        "car_location" -> AnalyticsEvent.CAR_LOCATION_SCREEN
        "guest_picker" -> AnalyticsEvent.GUEST_PICKER_SCREEN
        "traveller_picker" -> AnalyticsEvent.TRAVELLER_PICKER_SCREEN
        "flight_api_results" -> AnalyticsEvent.RESULT_SCREEN
        "flight_results", "hotel_results", "car_results", "search_results" ->
            AnalyticsEvent.CUSTOM_WEB_SCREEN
        "flight_deal" -> AnalyticsEvent.PROVIDER_SCREEN
        else -> bare
    }
}
