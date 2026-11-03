package com.gocheap.flights.analytics.crash

/**
 * Everything this app reports to Crashlytics, as one object both platforms share.
 *
 * The crashes themselves need nothing from here: both platform SDKs install their own handler as
 * the process starts and will file a report whether or not a line of this is ever called. What
 * this adds is the part of a report that is worth more than the stack — the screen the user was on,
 * the region they were pricing in, the last few things they did — and a way for the shared code to
 * report an error it *handled*, which by definition never reaches a crash handler at all.
 *
 * Built the way [com.gocheap.flights.analytics.ga4.FlightAnalytics] is, and for the same reasons:
 * named calls rather than a free-form `log`, one place per piece of metadata so two call sites
 * cannot spell the same key differently, and nothing here can affect what the app does. Every
 * function returns Unit, none of them suspend, and the platform halves swallow what they throw —
 * a crash reporter that can cost a user their booking is worse than no crash reporter.
 *
 * Public rather than internal so the native side can report from the screens the shared module
 * does not own — the Travelpayouts SDK's results on either platform.
 */
object CrashReporting {

    /** True once [start] has run, so a second call cannot install a second exception hook. */
    private var started = false

    /**
     * Call once per launch, from the same place [com.gocheap.flights.analytics.ga4.FlightAnalytics.start]
     * is called and for the same reason: everything below is metadata attached to the *next* crash,
     * so anything set after the crash is of no use.
     *
     * On iOS this is also what routes an uncaught Kotlin exception into a report before the process
     * goes down — see the iOS actual. On Android the platform handler already catches those.
     */
    fun start() {
        if (started) return
        started = true
        startCrashReporting()
    }

    /* ----------------------------------- Breadcrumbs ----------------------------------- */

    /**
     * The screen the user is on.
     *
     * Recorded twice on purpose: as a key, so the crash list can be grouped and filtered by the
     * screen a crash happened on, and as a breadcrumb, so the report also shows the route taken to
     * get there. A key only ever holds the latest value; the log holds the sequence.
     *
     * Called from [com.gocheap.flights.analytics.ga4.FlightAnalytics] rather than from the screens
     * themselves, so that a screen added later is covered by having an analytics name at all.
     */
    fun screen(screenName: String) {
        if (screenName.isBlank()) return
        setCrashKey(ScreenKey, screenName.take(MaxValueChars))
        breadcrumb("screen: $screenName")
    }

    /**
     * One line in the log that travels with the next crash.
     *
     * Deliberately thin — a breadcrumb is only ever read next to a stack trace, so there is nothing
     * to validate beyond not sending an empty one. Keep call sites to things that change what the
     * app is doing (a search fired, a handoff to a supplier, a response that failed to parse); a
     * breadcrumb per frame drowns the report it is meant to explain.
     */
    fun breadcrumb(message: String) {
        if (message.isBlank()) return
        logCrashBreadcrumb(message.take(MaxValueChars))
    }

    /* -------------------------------------- Keys --------------------------------------- */

    /**
     * The three codes the whole app is priced and localised off, pinned to every report.
     *
     * Worth keys rather than a breadcrumb: a crash that only happens in one currency or one
     * language is a bug that is invisible in the aggregate and obvious the moment the list can be
     * grouped by it.
     *
     * Passed in rather than read from `AppSettings` here, so that this stays callable from the
     * place that already knows a setting changed.
     */
    fun region(countryCode: String, currencyCode: String, languageCode: String) {
        setKey(CountryKey, countryCode.uppercase())
        setKey(CurrencyKey, currencyCode.uppercase())
        setKey(LanguageKey, languageCode)
    }

    /** One arbitrary key on every later report. Blank names and values are dropped, not sent. */
    fun setKey(name: String, value: String) {
        val key = name.trim().take(MaxNameChars)
        val clean = value.trim().take(MaxValueChars)
        if (key.isEmpty() || clean.isEmpty()) return
        setCrashKey(key, clean)
    }

    /**
     * The id this install is already known by elsewhere, so a crash can be matched to the session
     * and the backend records that go with it.
     *
     * The same identifier `users/add` registers the device under — see
     * [com.gocheap.flights.data.user.DeviceIdentity] — and not anything the user typed. Crashlytics
     * keeps this until it is cleared, which is the whole point: a crash report is read days later.
     */
    fun setUserId(id: String) {
        val clean = id.trim().take(MaxValueChars)
        if (clean.isEmpty()) return
        setCrashUserId(clean)
    }

    /* ------------------------------------ Non-fatals ----------------------------------- */

    /**
     * An error the app caught and carried on from.
     *
     * This is the half of crash reporting that does not happen on its own. A search that comes back
     * unparseable, a deeplink that cannot be built, an SDK that refuses to initialise — all of those
     * are handled, none of them reach a crash handler, and all of them are bugs. [origin] is what
     * separates them in the console: Crashlytics groups non-fatals by stack, and a stack that bottoms
     * out in shared networking code is the same stack for every caller.
     */
    fun record(error: Throwable, origin: String? = null) {
        val where = origin?.trim()?.take(MaxValueChars)
        if (!where.isNullOrEmpty()) breadcrumb("error at $where: ${error.message ?: error::class.simpleName}")
        recordCrashError(error, where)
    }

    /* ------------------------------------ Internals ------------------------------------ */

    private const val ScreenKey = "screen"
    private const val CountryKey = "country"
    private const val CurrencyKey = "currency"
    private const val LanguageKey = "language"

    /**
     * Crashlytics' own limits: 64 characters of key, 1024 of value, and the value is truncated
     * rather than rejected past that. Clipped here anyway so that both platforms agree on what was
     * sent, and so a report never shows a key that was silently cut in half on one platform only.
     */
    private const val MaxNameChars = 64
    private const val MaxValueChars = 1024
}

/**
 * Readies the platform SDK and, where the platform needs it, routes uncaught Kotlin exceptions into
 * it.
 *
 * **Must never throw.** Called from `Application.onCreate` and from the iOS app's `init`, where
 * anything thrown takes the launch down.
 */
internal expect fun startCrashReporting()

/** One line in the log that travels with the next crash. Must never throw. */
internal expect fun logCrashBreadcrumb(message: String)

/** One key on every later report. Already trimmed and clipped by the caller. Must never throw. */
internal expect fun setCrashKey(name: String, value: String)

/** The id later reports are filed under. Must never throw. */
internal expect fun setCrashUserId(id: String)

/**
 * Files one handled error as a non-fatal.
 *
 * [origin] is the call site's own name for where this happened, already clipped; the platform halves
 * use it to keep errors from different callers out of the same group.
 *
 * Must never throw — this runs on paths that have *already* gone wrong once, and the user is still
 * owed whatever the caller does next.
 */
internal expect fun recordCrashError(error: Throwable, origin: String?)
