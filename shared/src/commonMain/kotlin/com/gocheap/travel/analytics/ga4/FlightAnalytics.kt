package com.gocheap.travel.analytics.ga4

import com.gocheap.travel.analytics.crash.CrashReporting
import com.gocheap.travel.data.model.CarSearch
import com.gocheap.travel.data.model.HotelSearch
import com.gocheap.travel.data.model.TripSearch
import com.gocheap.travel.data.settings.AppSettings

/**
 * Everything this app reports to GA4, as one object both platforms share.
 *
 * Written as named calls rather than a `log(name, params)` the screens reach for directly, for the
 * same reason [AnalyticsEvent] exists: a parameter map assembled at the call site is a map that can
 * be assembled differently at the next one, and two spellings of the same event are worse in a
 * dashboard than a missing one — a missing event is obvious, a split one looks like real data.
 * Each function below is the single place its event is built, so every screen that fires it sends
 * the same keys.
 *
 * **Nothing here can affect what the app does.** The platform half swallows anything it throws (see
 * [logGa4Event]), no function returns a value a caller could branch on, and nothing is suspended or
 * awaited — a call site fires and carries straight on. That is deliberate: these calls sit in the
 * middle of search and checkout paths, and analytics that can break a booking is worse than no
 * analytics.
 *
 * Public rather than internal so the native side can report from the screens the shared module
 * does not own — the Travelpayouts SDK's results on either platform.
 */
object FlightAnalytics {

    /**
     * The screen the user is on, kept so the backgrounding hook has something to name.
     *
     * A plain var and not a flow: it is written by the navigation observer on the main thread and
     * read once, by a lifecycle callback that is also on the main thread.
     */
    private var currentScreen: String? = null

    /** True once [start] has run, so a second call cannot register a second lifecycle observer. */
    private var started = false

    /**
     * Call once per launch, before any screen is composed.
     *
     * Reports the launch and subscribes to the platform's "went to the background" signal, which is
     * the only event here that is not fired by a call site in the UI.
     */
    fun start() {
        if (started) return
        started = true
        log(AnalyticsEvent.APP_LAUNCH, mapOf(AnalyticsEvent.PLATFORM to platformAnalyticsName))
        pinRegionToCrashReports()
        observeAppBackgrounded { appClose() }
    }

    /* ------------------------------------- Screens ------------------------------------- */

    /**
     * A screen coming up.
     *
     * One parameter, not two: [AnalyticsEvent.SCREEN_NAME] is already `screen_name`, which is the
     * key GA4 reads for its own screen reports — it arrives in the console as `ga_screen`. Sending
     * it under a second name would not add a second report, it would just be the same string
     * twice.
     */
    fun screenView(screenName: String) {
        if (screenName.isBlank()) return
        currentScreen = screenName
        // The same name, to the other reporter: a crash is worth far more when it says which
        // screen it happened on, and this is already the one place every screen is named.
        CrashReporting.screen(screenName)
        log(AnalyticsEvent.SCREEN_VIEW, mapOf(AnalyticsEvent.SCREEN_NAME to screenName))
    }

    /**
     * A sheet coming up over whatever is behind it.
     *
     * The same event as [screenView] — a sheet is a screen as far as a funnel is concerned — but it
     * does not become [currentScreen]: a sheet is dismissed before the app is backgrounded in every
     * path there is, and recording it would have [appClose] name a sheet the user had already
     * closed.
     */
    fun sheetView(screenName: String) {
        if (screenName.isBlank()) return
        log(AnalyticsEvent.SCREEN_VIEW, mapOf(AnalyticsEvent.SCREEN_NAME to screenName))
    }

    /* -------------------------------------- Launch ------------------------------------- */

    /** The intro pages ending, either by reaching the last one or by skipping them. */
    fun onboardingComplete(skipped: Boolean) {
        log(
            AnalyticsEvent.ONBOARDING_COMPLETE,
            mapOf(
                AnalyticsEvent.TYPE to
                    if (skipped) AnalyticsEvent.SKIPPED else AnalyticsEvent.FINISHED,
            ),
        )
    }

    /**
     * The app going to the background.
     *
     * Named after the screen the user left behind, which is the only thing this event says that
     * the session itself does not.
     */
    private fun appClose() {
        log(
            AnalyticsEvent.APP_CLOSE,
            mapOf(AnalyticsEvent.SCREEN_NAME to currentScreen),
        )
    }

    /* ------------------------------------ Searches ------------------------------------- */

    /**
     * A flight search being run.
     *
     * [source] separates the three ways one starts — the card's own button, a tap on a popular
     * destination, and a recent card re-run — because they are different intents and a single
     * count of searches hides that.
     */
    fun flightSearch(search: TripSearch, source: String = AnalyticsEvent.SEARCH_FORM) {
        log(
            AnalyticsEvent.SEARCH_BUTTON_CLICKED,
            buildMap {
                put(AnalyticsEvent.TYPE, AnalyticsEvent.FLIGHT)
                put(AnalyticsEvent.TEXT, source)
                put(AnalyticsEvent.LOCATION, search.from.code)
                put(AnalyticsEvent.ARRIVAL_LOCATION, search.to.code)
                put(AnalyticsEvent.DATE, search.departureDate)
                put(AnalyticsEvent.RETURN_DATE, search.returnDate)
                put(AnalyticsEvent.COUNT, search.passengers)
                put(AnalyticsEvent.CHILDREN_COUNT, search.children + search.infants)
                put(AnalyticsEvent.TRAVEL_CLASS, search.cabinClass.label)
                put(
                    TripTypeParam,
                    if (search.isRoundTrip) AnalyticsEvent.ROUND_TRIP else AnalyticsEvent.ONE_WAY,
                )
                putAll(regionParams())
            },
        )
    }

    /** A stay search being run; the parameters a stay is priced on, and nothing else. */
    fun staySearch(search: HotelSearch, source: String = AnalyticsEvent.SEARCH_FORM) {
        log(
            AnalyticsEvent.SEARCH_BUTTON_CLICKED,
            buildMap {
                put(AnalyticsEvent.TYPE, AnalyticsEvent.STAY)
                put(AnalyticsEvent.TEXT, source)
                put(AnalyticsEvent.LOCATION, search.city)
                put(AnalyticsEvent.DATE, search.checkInDate)
                put(AnalyticsEvent.CHECKOUT_DATE, search.checkOutDate)
                put(AnalyticsEvent.COUNT, search.guests)
                put(AnalyticsEvent.CHILDREN_COUNT, search.childAges.size)
                put(AnalyticsEvent.ROOM_COUNT, search.roomCount)
                putAll(regionParams())
            },
        )
    }

    /** A rental search being run. The drop-off travels as its own keys, times included. */
    fun rentalSearch(search: CarSearch, source: String = AnalyticsEvent.SEARCH_FORM) {
        log(
            AnalyticsEvent.SEARCH_BUTTON_CLICKED,
            buildMap {
                put(AnalyticsEvent.TYPE, AnalyticsEvent.RENTAL)
                put(AnalyticsEvent.TEXT, source)
                put(AnalyticsEvent.LOCATION, search.pickupPoint)
                put(AnalyticsEvent.DROP_OFF_LOCATION, search.dropoffPoint ?: search.pickupPoint)
                put(AnalyticsEvent.DATE, search.pickupDate)
                put(AnalyticsEvent.DROP_OFF_DATE, search.dropoffDate)
                put(AnalyticsEvent.TIME, search.pickupTime)
                put(AnalyticsEvent.DROP_OFF_TIME, search.dropoffTime)
                put(
                    DropOffParam,
                    if (search.isSameDropoff) {
                        AnalyticsEvent.SAME_DROP_OFF
                    } else {
                        AnalyticsEvent.DIFFERENT_DROP_OFF
                    },
                )
                putAll(regionParams())
            },
        )
    }

    /**
     * A search that came back with nothing to show.
     *
     * Worth its own event rather than an absent one: a vertical whose searches all end here is
     * broken in a way a count of searches alone would report as healthy demand.
     */
    fun searchFailed(vertical: String, reason: String, screenName: String) {
        CrashReporting.breadcrumb("search failed: $vertical ($reason)")
        log(
            AnalyticsEvent.SEARCH_FAILED,
            mapOf(
                AnalyticsEvent.TYPE to vertical,
                AnalyticsEvent.REASON to reason,
                AnalyticsEvent.SCREEN_NAME to screenName,
            ),
        )
    }

    /* -------------------------------------- Exits -------------------------------------- */

    /**
     * The user leaving for a supplier.
     *
     * Fired beside every `SessionTracking` call and nowhere else, so the two agree about what an
     * exit is: GA4 counts them for the dashboard, the backend counts them for the payout, and an
     * exit recorded in one but not the other is a reconciliation problem later.
     *
     * [adClick] fires this event too, for an ad tap, and pairs with `SessionTracking.adClick` in
     * exactly the same way — the invariant holds there as well.
     *
     * [exit] is how the search left — the SDK's screen, a supplier's own page, or results this app
     * drew — which for flights is the backend's decision per search and not this build's.
     */
    fun exitClick(vertical: String, exit: String, screenName: String) {
        log(
            AnalyticsEvent.EXIT_CLICK,
            buildMap {
                put(AnalyticsEvent.TYPE, vertical)
                put(AnalyticsEvent.TEXT, exit)
                put(AnalyticsEvent.SCREEN_NAME, screenName)
                putAll(regionParams())
            },
        )
    }

    /**
     * A named booking site being opened off the in-app results list.
     *
     * [index] is the deal's position in the list, which is what says whether the cheapest option is
     * the one people take. Primitives rather than the deal itself because that model is internal to
     * the results feature and this object is shared by the whole app.
     */
    fun providerButton(providerName: String?, price: Double?, index: Int?) {
        log(
            AnalyticsEvent.PROVIDER_BUTTON,
            buildMap {
                put(AnalyticsEvent.TYPE, AnalyticsEvent.FLIGHT)
                put(AnalyticsEvent.PROVIDER_NAME, providerName)
                put(AnalyticsEvent.PRICE, price)
                put(AnalyticsEvent.INDEX, index)
                put(AnalyticsEvent.SCREEN_NAME, AnalyticsEvent.RESULT_SCREEN)
                putAll(regionParams())
            },
        )
    }

    /* -------------------------------------- Ads ---------------------------------------- */

    /**
     * An inline ad reaching the screen.
     *
     * Fire once per ad actually seen, beside `AdsRepository.trackImpression` and never instead of
     * it. The two are not alternatives: the host's own impression URL is what the publisher is
     * paid on, and this is what says, in the same dashboard as every other event, which placement
     * and which route produced the view. An ad reported to one and not the other is a
     * reconciliation problem at the end of the month.
     *
     * [placement] is [AnalyticsEvent.POPUP_AD] or [AnalyticsEvent.NATIVE_AD] — the two are the
     * same ad from the same call, and where it was shown is the whole of the difference between
     * them in a report.
     *
     * The route travels as origin and destination because an ad is sold against a route: "which
     * routes are worth showing an ad on" is the question this event exists to answer, and a count
     * alone cannot.
     */
    fun adImpression(
        placement: String,
        provider: String?,
        origin: String?,
        destination: String?,
    ) {
        log(
            AnalyticsEvent.AD_IMPRESSION_NATIVE,
            buildMap {
                put(AnalyticsEvent.TYPE, placement)
                put(AnalyticsEvent.PROVIDER, provider)
                put(AnalyticsEvent.LOCATION, origin)
                put(AnalyticsEvent.ARRIVAL_LOCATION, destination)
                putAll(regionParams())
            },
        )
    }

    /**
     * An inline ad being tapped.
     *
     * Fires two events, which is the reference app's arrangement and worth keeping: the ad event
     * for the ad reports, and [AnalyticsEvent.EXIT_CLICK] so that a tap on an ad appears in the
     * same funnel as every other way the user leaves for a supplier. An ad that did not show up
     * there would make the exit count read low by however much the ads earn.
     *
     * **Pair it with `SessionTracking.adClick`,** like every other exit — the reference app sends
     * a session row for an ad tap too, under its own `type = "ads"`, so that ad revenue can be
     * told apart from search revenue at the backend. GA4 counts the tap for the dashboard and the
     * session row counts it for the payout; an ad recorded in one but not the other is the same
     * reconciliation problem [exitClick] describes.
     *
     * [buttonText] is the ad's own call to action, which the host chooses per creative — it is
     * what says whether "View deal" outperforms "Book now".
     */
    fun adClick(
        placement: String,
        provider: String?,
        buttonText: String?,
        screenName: String,
        origin: String?,
        destination: String?,
    ) {
        log(
            AnalyticsEvent.AD_CLICK_NATIVE,
            buildMap {
                put(AnalyticsEvent.TYPE, placement)
                put(AnalyticsEvent.PROVIDER, provider)
                put(AnalyticsEvent.TEXT, buttonText)
                put(AnalyticsEvent.LOCATION, origin)
                put(AnalyticsEvent.ARRIVAL_LOCATION, destination)
                putAll(regionParams())
            },
        )
        log(
            AnalyticsEvent.EXIT_CLICK,
            buildMap {
                // The placement, where a vertical would go on every other exit: an ad is not one
                // of the three verticals, and calling it "flight" would hide it inside them.
                put(AnalyticsEvent.TYPE, placement)
                put(AnalyticsEvent.SCREEN_NAME, screenName)
                putAll(regionParams())
            },
        )
    }

    /* ------------------------------------ Settings ------------------------------------- */

    /**
     * A Profile choice being made.
     *
     * One event with the field as a parameter rather than five events: what is asked of this is
     * "how many people change their currency", and five names would have to be summed by hand every
     * time a sixth setting is added.
     */
    fun settingChanged(setting: String, value: String) {
        // Cheaper than working out whether this particular setting was one of the three, and
        // correct whichever it was.
        pinRegionToCrashReports()
        log(
            AnalyticsEvent.SETTING_CHANGED,
            mapOf(
                AnalyticsEvent.SETTING to setting,
                AnalyticsEvent.VALUE to value,
            ),
        )
    }

    /* ------------------------------------ Internals ------------------------------------ */

    /**
     * The three codes the supplier prices and localises off, on every event where they change what
     * the user saw.
     *
     * Read from [AppSettings] at the moment the event is built rather than passed in, because every
     * call site would otherwise have to read them and one of them would eventually forget.
     */
    private fun regionParams(): Map<String, Any?> {
        val settings = AppSettings.current
        return mapOf(
            AnalyticsEvent.REGION to settings.countryCode.uppercase(),
            AnalyticsEvent.CURRENCY_CODE to settings.currencyCode.uppercase(),
            AnalyticsEvent.LANGUAGE_CODE to settings.languageCode,
        )
    }

    /**
     * The same three codes, as keys on every later crash report.
     *
     * Keys rather than the breadcrumb [regionParams] already puts on each event, because the two
     * are read differently: a parameter answers "what were the searches in Brazil doing", a key
     * answers "is this crash only happening in Brazil". The second question is the one that is
     * impossible to ask without this.
     *
     * Called at launch and again on every Profile change, which between them covers every moment
     * the values can differ from what was last sent.
     */
    private fun pinRegionToCrashReports() {
        val settings = AppSettings.current
        CrashReporting.region(
            countryCode = settings.countryCode,
            currencyCode = settings.currencyCode,
            languageCode = settings.languageCode,
        )
    }

    /**
     * The one door to the platform SDKs.
     *
     * Nulls and blanks are dropped here rather than at the call sites: a search with no return date
     * is the ordinary one-way case, and sending `"null"` for it would put a string that reads like
     * a bug into the dashboard. Everything that survives is then clipped to what GA4 accepts — see
     * [sanitised] — because the SDKs reject an over-long name or value by dropping the whole event,
     * silently, which is the worst way to lose one.
     */
    private fun log(name: String, params: Map<String, Any?>) {
        logGa4Event(name.take(MaxNameChars), params.sanitised())
    }

    /**
     * What GA4 will actually accept.
     *
     * Four rules, all of them the SDKs' own limits: no empties, names of at most 40 characters,
     * string values of at most 100, and at most 25 parameters on an event. The cap is a backstop —
     * nothing above comes close to it — but it is cheaper than discovering the limit from a missing
     * event months later.
     */
    private fun Map<String, Any?>.sanitised(): Map<String, Any> = buildMap {
        for ((key, value) in this@sanitised) {
            if (size >= MaxParams) break
            val name = key.trim().take(MaxNameChars)
            if (name.isEmpty()) continue
            val clean = value.asParameterValue() ?: continue
            put(name, clean)
        }
    }

    /**
     * One parameter value as GA4 takes it, or null for one it should not be sent at all.
     *
     * Numbers go over as numbers rather than as their text, so the dashboard can average a party
     * size or a price instead of grouping by it. A type that is neither — a caller passing a model
     * where a value was expected — is sent as its text, which is more use in a report than a
     * silently absent parameter.
     */
    private fun Any?.asParameterValue(): Any? = when (this) {
        null -> null
        is String -> trim().take(MaxValueChars).takeIf(String::isNotEmpty)
        is Int -> toLong()
        is Long -> this
        is Double -> this
        is Float -> toDouble()
        is Boolean -> if (this) 1L else 0L
        else -> toString().take(MaxValueChars).takeIf(String::isNotEmpty)
    }

    /** Local to the search events, so they do not take up room in the shared vocabulary. */
    private const val TripTypeParam = "trip_type"
    private const val DropOffParam = "dropoff_type"

    private const val MaxNameChars = 40
    private const val MaxValueChars = 100
    private const val MaxParams = 25
}

/**
 * Hands one event to the platform's Firebase SDK.
 *
 * **Must never throw.** Both actuals swallow everything: analytics sits inside the search and
 * checkout paths, and the alternative to a dropped event is a dropped booking.
 */
internal expect fun logGa4Event(name: String, params: Map<String, Any>)

/**
 * Calls [onBackgrounded] each time the app leaves the foreground.
 *
 * An expect rather than something assembled in common code because neither platform exposes it the
 * same way — Android has an Activity lifecycle, iOS has a notification — and because the shared
 * module has no Application or UIApplication of its own to hang it on.
 */
internal expect fun observeAppBackgrounded(onBackgrounded: () -> Unit)

/** "android" or "ios", for the one event that is about the launch rather than about the user. */
internal expect val platformAnalyticsName: String
