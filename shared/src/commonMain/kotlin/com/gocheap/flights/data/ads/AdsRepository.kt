package com.gocheap.flights.data.ads

import com.gocheap.flights.data.controller.AppController
import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.data.model.CabinClass
import com.gocheap.flights.data.model.TripSearch
import com.gocheap.flights.data.model.flight.ads.FlightAdsLeg
import com.gocheap.flights.data.model.flight.ads.FlightAdsRequest
import com.gocheap.flights.data.model.flight.ads.InlineAdItem
import com.gocheap.flights.data.network.ApiService
import com.gocheap.flights.data.settings.AppSettings
import com.gocheap.flights.data.storage.PersistedValue
import com.gocheap.flights.data.user.UserSession
import com.gocheap.flights.utils.Constants
import com.gocheap.flights.utils.logDebug
import com.gocheap.flights.utils.logWarning
import com.gocheap.flights.utils.systemEpochMillis
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The tag the ad calls log under: `adb logcat -s InlineAds`. */
internal const val AdsLogTag = "InlineAds"

/**
 * The inline ads shown beside flight results, over the supplier's ad host.
 *
 * Three calls, in a fixed order, and this object is what keeps them in that order:
 *
 * 1. `GET ads/session` opens a session and answers with a `sid`. Reused for twenty minutes rather
 *    than opened per request — see [session].
 * 2. `POST ads/flight/list` asks for the ads for one trip, quoting that `sid`.
 * 3. `GET` on an ad's own impression URL reports that it reached the screen — see
 *    [trackImpression].
 *
 * **Nothing here fails loudly.** Every entry point answers with an empty list, or with nothing at
 * all, when it cannot do its job: an ad is decoration on a screen whose actual content came from
 * somewhere else, and a results list that refuses to render because its ad call 500'd would be a
 * worse app than one with a gap where the ad goes. Failures are logged under [AdsLogTag] and go
 * no further. This is why the public surface returns `List<InlineAdItem>` rather than `Result` —
 * there is no failure for a caller to handle differently.
 *
 * **Two gates, both checked on every call.** The controller's `is_ads_enabled`, which is the
 * backend's switch over this build, and whether the user's country has a supplier domain at all.
 * The second is not a precaution: an ad's links arrive relative to that domain, so an ad fetched
 * for a country without one could be shown but never opened or reported. See [countryDomain].
 */
internal class AdsRepository(
    private val apiService: ApiService = ApiService(),
) {

    /**
     * The session id and when it was opened.
     *
     * Persisted rather than held in memory so that a cold start inside the window reuses the
     * session the last run opened, which is what the reference app does. The timestamp is stored
     * alongside because the host does not say when a session expires — see
     * [Constants.Ads.SessionValidityMillis].
     */
    @Serializable
    private data class StoredSession(
        @SerialName("sid") val sid: String,
        @SerialName("opened_at") val openedAtMillis: Long,
    )

    private val storage = PersistedValue("ads_session", StoredSession.serializer())

    /**
     * Held while a session is being opened.
     *
     * Without it, two screens asking for ads at once each find no valid session and each open
     * one — two round trips, and the second overwrites the first's id while the first is already
     * using it. The lock is around the *whole* read-check-fetch-store sequence rather than the
     * store alone, so the second caller finds the first's session rather than racing it.
     */
    private val sessionLock = Mutex()

    /**
     * The impression URLs already reported, so each ad is counted once — see [trackImpression].
     *
     * In memory only, unlike the session. An impression is "this ad was seen in this sitting";
     * remembering it across launches would silence a genuine second view a week later, and the
     * set is bounded by how many ads one run of the app shows.
     */
    private val reportedImpressions = mutableSetOf<String>()

    private val impressionLock = Mutex()

    /**
     * The ads for one trip, best effort.
     *
     * Empty means every one of: ads are switched off, the country has no supplier site, no
     * session could be opened, the host had nothing to show, or the call failed. None of those is
     * a case a caller does anything different about, which is why they are not distinguished.
     *
     * Ordered as the host ranked them — see [InlineAdItem.rank]. A screen with room for one ad
     * shows the first.
     */
    suspend fun inlineAds(search: TripSearch): List<InlineAdItem> {
        // Said out loud rather than returned silently: "there are no ads" and "ads never ran" look
        // identical on screen, and this is the only place that can tell them apart.
        disabledReason()?.let { reason ->
            logDebug(AdsLogTag, "SKIPPED ads/flight/list — $reason")
            return emptyList()
        }
        val countryCode = countryCode()
        val sid = session(countryCode) ?: run {
            // `session` has already logged why; this says what it cost.
            logDebug(AdsLogTag, "SKIPPED ads/flight/list — no session to quote")
            return emptyList()
        }

        val request = search.toAdsRequest() ?: run {
            logDebug(AdsLogTag, "SKIPPED ads/flight/list — the trip has no usable dates")
            return emptyList()
        }

        // The body exactly as it goes on the wire, which is the only way a rejected request is
        // diagnosable after the fact — the same arrangement `SessionTracking` makes, and the
        // reference app's own ADS BODY line.
        logDebug(
            AdsLogTag,
            "REQUEST POST ${Constants.Ads.FlightListPath} countryCode=$countryCode " +
                "sid=$sid ${request.asLog()}",
        )

        val ads = runCatchingAds("ads/flight/list") {
            apiService.adsFlightList(sid = sid, countryCode = countryCode, request = request)
                .inlineItems
                .orEmpty()
        } ?: return emptyList()

        if (ads.isEmpty()) {
            // A 2xx that carried nothing. Not a failure — the host often has no inventory for a
            // route — but it is the answer most often mistaken for one, so it gets its own line.
            logDebug(AdsLogTag, "SUCCESS ads/flight/list — 2xx carried no ads for this route")
        } else {
            logDebug(
                AdsLogTag,
                "SUCCESS ads/flight/list — ${ads.size} ad(s): " +
                    ads.joinToString { "${it.site ?: "(unnamed)"}#${it.rank ?: "-"}" },
            )
        }
        return ads
    }

    /**
     * Reports that [ad] has been on screen, and says whether this call was the one that did it.
     *
     * Call once per ad actually shown, not once per ad fetched: an impression is what the
     * publisher is paid on, and reporting one for an ad that scrolled past below the fold reports
     * a view that did not happen.
     *
     * **Reports each ad at most once**, keyed on its impression URL — a list that recomposes, or a
     * card that scrolls back into view, would otherwise report the same ad repeatedly. A failed
     * report releases the key again, so a later attempt is still possible; only a report that
     * actually landed is remembered.
     *
     * Returns true exactly once per ad, on the call that reported it. That is what the matching
     * `FlightAnalytics.adImpression` should be fired on — the GA4 event and the host's own count
     * are read side by side, and a guard reimplemented at the call site is one that drifts from
     * this one.
     *
     * False, and nothing sent, when: ads are off, the ad carries no impression URL, the country
     * has no supplier site to resolve it against, this ad was already reported, or the call
     * failed.
     */
    suspend fun trackImpression(ad: InlineAdItem): Boolean {
        if (!isEnabled()) return false
        val url = absoluteUrl(ad.impressionUrl) ?: run {
            logDebug(AdsLogTag, "SKIPPED impression — no URL to report to")
            return false
        }
        // Claimed before the request rather than after it, so two coroutines arriving together
        // cannot both find it unclaimed and both report. The loser is released below if the
        // request then fails.
        if (!claimImpression(url)) {
            logDebug(AdsLogTag, "SKIPPED impression — already reported")
            return false
        }

        logDebug(AdsLogTag, "REQUEST GET $url")
        val sent = runCatchingAds("impression") { apiService.adsImpression(url) } != null
        if (!sent) {
            releaseImpression(url)
            return false
        }
        logDebug(AdsLogTag, "SUCCESS impression")
        return true
    }

    /** True if this call is the one that took [url]; false if it was already taken. */
    private suspend fun claimImpression(url: String): Boolean =
        impressionLock.withLock { reportedImpressions.add(url) }

    private suspend fun releaseImpression(url: String) {
        impressionLock.withLock { reportedImpressions.remove(url) }
    }

    /**
     * The supplier's site for the user's country, or blank if it runs none there.
     *
     * Exposed because an ad's *logo* is relative to it too, and that one is resolved by the card
     * rather than here — the repository has no business knowing what an image URL is for. Blank
     * rather than null so a UI state can hold it without a nullable field; the same condition
     * that makes it blank also makes [inlineAds] empty, so a caller never has one without the
     * other.
     */
    fun countryBaseDomain(): String = countryDomain().orEmpty()

    /**
     * Where tapping [ad] should go, or null if it cannot be resolved.
     *
     * The same absolutising [trackImpression] does, exposed because a deeplink is the caller's to
     * open — this module has no business launching a browser.
     */
    fun deeplink(ad: InlineAdItem): String? = absoluteUrl(ad.deepLink)

    /**
     * A valid session id, opening one if the stored one is missing or stale.
     *
     * Null when the host would not give one. That is not retried here: the reference app backs
     * off and tries again on a 429, and the equivalent in this app is the next screen that asks
     * for ads, which is cheaper than a timer and no slower in practice.
     */
    private suspend fun session(countryCode: String): String? = sessionLock.withLock {
        storedSessionOrNull()?.let { return@withLock it }

        val label = UserSession.deeplinkUserId
        logDebug(AdsLogTag, "REQUEST GET ads/session countryCode=$countryCode label=$label")

        val sid = runCatchingAds("ads/session") {
            apiService.adsSession(label = label, countryCode = countryCode).sid
        }?.takeIf(String::isNotBlank)

        if (sid == null) {
            // A 200 with no `sid` lands here too, not just a failure: the host answers that way
            // when it has no session to give, and an empty id is not one.
            logWarning(AdsLogTag, "FAILED ads/session — no session id; ads are off for now")
            return@withLock null
        }

        storage.save(StoredSession(sid = sid, openedAtMillis = systemEpochMillis()))
        logDebug(AdsLogTag, "SUCCESS ads/session — opened a session")
        sid
    }

    /** The stored session if it is still inside its window, and null if there is none or it is old. */
    private fun storedSessionOrNull(): String? {
        val stored = storage.load(EmptySession).takeIf { it.sid.isNotBlank() } ?: return null
        val age = systemEpochMillis() - stored.openedAtMillis
        // `age < 0` is a clock moved backwards since it was written — see `systemEpochMillis`.
        // Treated as expired rather than as freshly opened, which costs one call rather than
        // pinning the app to an id it can no longer tell the age of.
        return stored.sid.takeIf { age in 0 until Constants.Ads.SessionValidityMillis }
    }

    /**
     * Both gates in one place — see the class comment.
     *
     * Reads the controller rather than caching the answer: it is fetched per country and can flip
     * mid-run, and an ad call that ran because the switch was on when the screen opened is
     * exactly the case this is for.
     */
    private fun isEnabled(): Boolean = disabledReason() == null

    /**
     * Why ads are off, or null if they are on.
     *
     * A reason rather than a boolean because both gates are invisible from outside: a build with
     * the controller switch off and a build in a country the supplier does not serve both show
     * exactly no ads, and without this the difference is undiagnosable from a log.
     */
    private fun disabledReason(): String? = when {
        AppController.current.isAdsEnabled == false ->
            "the controller has is_ads_enabled=false"
        countryDomain() == null ->
            "${AppSettings.current.countryCode.uppercase()} has no supplier site in the catalogue"
        else -> null
    }

    private fun countryCode(): String = AppSettings.current.countryCode.lowercase()

    /** The supplier's site for the user's country, normalised — see [supplierDomain]. */
    private fun countryDomain(): String? = supplierDomain(AppSettings.current.country.domain)

    /** One of an ad's relative URLs against that site — see [absoluteAdUrl]. */
    private fun absoluteUrl(path: String?): String? = absoluteAdUrl(path, countryDomain())

    /**
     * One place for the three calls' error handling: log it, swallow it, hand back [T]'s empty
     * value — and never swallow a cancellation, which is a screen that closed rather than a call
     * that failed.
     *
     * A 401 clears the stored session on the way past. That is the one failure worth acting on:
     * it means the id expired early, before the twenty minutes this app assumes, and leaving it
     * stored would make every later call fail the same way until the window ran out on its own.
     * The *next* call opens a fresh one; this one is not retried, because an ad is not worth a
     * second round trip on a screen that has already rendered without it.
     */
    private suspend fun <T> runCatchingAds(what: String, call: suspend () -> T): T? = try {
        call()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        if (error.isUnauthorized()) {
            storage.save(EmptySession)
            logWarning(
                AdsLogTag,
                "FAILED $what — ${error.describe()}; session rejected, it will be reopened " +
                    "on the next call",
            )
        } else {
            logWarning(AdsLogTag, "FAILED $what — ${error.describe()}", error)
        }
        null
    }

    private fun Throwable.isUnauthorized(): Boolean =
        this is ResponseException && response.status == HttpStatusCode.Unauthorized

    /**
     * What went wrong, in the terms the ad host's owner would ask about.
     *
     * The body included, not the status alone — the same treatment `SessionTracking` and
     * `AppController` give a failed call, and for the same reason: a 400 from this host does not
     * say whether it was the session id, the country or the trip body that it objected to, and
     * its message is the only thing that does.
     */
    private suspend fun Throwable.describe(): String = when (this) {
        is ResponseException -> {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            "HTTP ${response.status} body=${body.take(MaxLoggedBodyChars).ifBlank { "(empty)" }}"
        }
        else -> "${this::class.simpleName}: ${message ?: "(no message)"}"
    }

    /** The request body as JSON, for the log only. */
    private fun FlightAdsRequest.asLog(): String =
        runCatching { LogJson.encodeToString(FlightAdsRequest.serializer(), this) }
            .getOrElse { "(body could not be encoded: ${it.message})" }

    private companion object {
        /** The "no session" record, used both as a load fallback and to clear a rejected one. */
        val EmptySession = StoredSession(sid = "", openedAtMillis = 0L)

        /** Encodes defaults, so a field left at its default still shows in the logged body. */
        val LogJson = Json { encodeDefaults = true }

        /** Enough of an error body to identify it; the platform log truncates a long line anyway. */
        const val MaxLoggedBodyChars = 300
    }
}

/**
 * A supplier site from the region catalogue, in the one shape an ad's URLs can be appended to:
 * `https`, host, trailing slash.
 *
 * Null for a country the supplier runs no site in, which the catalogue marks by leaving the field
 * out — about three quarters of its rows. The normalising is not cosmetic: the rows that do carry
 * one are not consistent about the scheme or the trailing slash, and `domain + path` is wrong in a
 * different way for each variation.
 *
 * A free function rather than a member of [AdsRepository] for the same reason
 * [com.gocheap.flights.data.controller.flightSearchModeFor] is one: touching any member of the
 * repository initialises storage and an HTTP client, and this is a string rule worth pinning in a
 * test that should not have to stand either of them up.
 */
internal fun supplierDomain(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
    // Scheme off first, then the slashes: doing it the other way round turns a bare "https://"
    // into "https:/", which no longer looks like a scheme and gets a second one prefixed to it.
    val host = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
        else -> trimmed
    }.trim('/')
    // A value that was nothing but a scheme is not a site.
    return host.takeIf(String::isNotBlank)?.let { "https://$it/" }
}

/**
 * One of an ad's URLs resolved against [domain], or null if it cannot be.
 *
 * Already-absolute values pass through untouched. The host sends relative paths today, but it
 * costs one check not to break the day it sends an absolute one — and prefixing an absolute URL
 * with a domain produces a string that is neither.
 *
 * Null [domain] with a relative path is the country-without-a-site case, and it has to be null
 * rather than the bare path: requesting a relative URL on the ads client would resolve it against
 * the *ad host*, reporting an impression to a server that never served the ad.
 */
internal fun absoluteAdUrl(path: String?, domain: String?): String? {
    val trimmed = path?.trim()?.takeIf(String::isNotBlank) ?: return null
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }
    // Both sides carry a slash of their own; exactly one belongs in the join.
    return domain?.plus(trimmed.removePrefix("/"))
}

/**
 * The app's own trip, in the ad host's vocabulary.
 *
 * Null when the departure date cannot be parsed: a leg with an empty date is rejected by the host,
 * and there is nothing useful to send instead. See [FlightAdsRequest] for why the airport codes,
 * the party and the cabin are all spelled differently from the search request built out of this
 * same object.
 *
 * Free for the same reason [supplierDomain] is.
 */
internal fun TripSearch.toAdsRequest(): FlightAdsRequest? {
    val outbound = FlightAdsLeg(
        originAirport = from.code,
        destinationAirport = to.code,
        date = departureDate.asIsoDate() ?: return null,
    )
    // Only on a round trip, and only once the return date parses — an unparseable one is dropped
    // rather than sent empty, which would be read as a malformed leg.
    val inbound = returnDate
        ?.takeIf { isRoundTrip }
        ?.asIsoDate()
        ?.let {
            FlightAdsLeg(
                originAirport = to.code,
                destinationAirport = from.code,
                date = it,
            )
        }

    val party = List(adults.coerceAtLeast(1)) { Constants.Ads.Adult } +
        List(children.coerceAtLeast(0)) { Constants.Ads.Child } +
        List(infants.coerceAtLeast(0)) { Constants.Ads.Infant }

    return FlightAdsRequest(
        cabinClass = cabinClass.adsValue(),
        passengers = party,
        legs = listOfNotNull(outbound, inbound),
    )
}

/** The ad host's own words for a cabin. The same set the search endpoint takes, as it happens. */
private fun CabinClass.adsValue(): String = when (this) {
    CabinClass.Economy -> "economy"
    CabinClass.PremiumEconomy -> "premiumEconomy"
    CabinClass.Business -> "business"
    CabinClass.First -> "first"
}

private fun String.asIsoDate(): String? = CalendarDate.parse(this)?.isoDate
