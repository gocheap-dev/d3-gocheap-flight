package com.gocheap.flights.data.user

import com.gocheap.flights.data.model.CabinClass
import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.data.model.CarSearch
import com.gocheap.flights.data.model.HotelSearch
import com.gocheap.flights.data.model.TripSearch
import com.gocheap.flights.data.model.car.CarLocation
import com.gocheap.flights.data.model.car.CarLocationType
import com.gocheap.flights.data.model.user.Attribution
import com.gocheap.flights.data.model.user.CarSessionParams
import com.gocheap.flights.data.model.user.CarSessionRequest
import com.gocheap.flights.data.model.user.FlightSessionParams
import com.gocheap.flights.data.model.user.FlightSessionRequest
import com.gocheap.flights.data.model.user.HotelSessionParams
import com.gocheap.flights.data.model.user.HotelSessionRequest
import com.gocheap.flights.data.model.user.StartSessionResponse
import com.gocheap.flights.data.network.ApiService
import com.gocheap.flights.data.network.user.UserHttpClient
import com.gocheap.flights.data.settings.AppSettings
import com.gocheap.flights.utils.Constants
import com.gocheap.flights.utils.logDebug
import com.gocheap.flights.utils.logWarning
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationStrategy

/** The tag the whole handoff logs under: `adb logcat -s StartSession`. */
internal const val StartSessionLogTag = "StartSession"

/**
 * Records a search being handed off to a supplier, as `POST users/session/`.
 *
 * One row per exit, which is what the backend measures the install against: `users/add` says an
 * install exists and where it came from, and this says what it then went and did. The two are keyed
 * together on `user_id`, which is why an unregistered install is skipped rather than sent with a
 * null — see [send].
 *
 * **Android only, deliberately.** The endpoint is wired for this app's Android codes and has not
 * been confirmed for iOS, so [sessionTrackingEnabled] is false there and every call below returns
 * without a request. Everything else lives in common code: when iOS is approved, that one actual
 * flips to true and the whole feature arrives with it.
 *
 * Fire-and-forget on a process-lifetime scope, like [UserRegistration] and for the same reason —
 * the call is made at the moment the user leaves for the supplier, so a scope tied to the screen
 * they are leaving would cancel it on the way out. Which is also why every outcome is logged: this
 * is the only call in the app whose success or failure changes nothing the user can see.
 */
internal object SessionTracking {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apiService = ApiService()

    /**
     * How the search left the app — a flight can leave three ways and the backend counts them
     * apart.
     *
     * [Sdk] is the default: with nothing said by the controller, a flight search is handed to the
     * Travelpayouts SDK's own results screen. [Deeplink] is a link straight out to a supplier's
     * page, which is how stays and rentals always leave, and [Api] is results fetched and drawn
     * in-app. All three have a flight path now — which one a search takes is the controller's
     * `new_tab` decision.
     *
     * Four cases, three wire values: the two results paths that leave the app share one word. See
     * [Sdk] for why, and [Ads] for the one that is not a search at all.
     */
    enum class FlightExit(val wireValue: String) {
        /**
         * Handed to the Travelpayouts SDK's own results screen.
         *
         * **Sent as "deeplink", not "sdk".** The endpoint's vocabulary is exactly two words —
         * `api` for results this app drew itself and `deeplink` for a search that left it — and
         * anything else is rejected outright with
         * `400 {"msg":"Invalid type","type":"invalid_type"}`. Verified against the staging
         * backend: `sdk`, `tpt_sdk`, `tp` and `travelpayouts` are all refused.
         *
         * A handoff to the SDK is a search leaving this app, so `deeplink` is the honest one of
         * the two available. The alternative is what this used to do — send a word the backend
         * does not know and have the row dropped on the floor, which records nothing at all.
         *
         * The case stays separate from [Deeplink] because the app genuinely distinguishes them:
         * which one a search takes is the controller's decision, and that is worth reading in
         * logs even where the wire cannot tell them apart.
         */
        Sdk("deeplink"),

        /** Out to a supplier's own results page in a web view. */
        Deeplink("deeplink"),

        /** Fetched and drawn in-app off the search/poll endpoints. */
        Api("api"),

        /**
         * An inline ad being tapped — not a search leaving at all, but a paid placement being
         * taken up. Its own word so that ad revenue can be told apart from search revenue in the
         * backend's numbers, which is the whole reason the row is worth sending.
         *
         * **Not verified against the backend, unlike the three above.** The note on [Sdk] records
         * that `type` was tested and found to accept exactly `api` and `deeplink` — but `ads` was
         * not among the words tried, and the reference app sends it on this exact path
         * (`AdResultViewModel`, `type = "ads"`), which is good evidence the endpoint knows it.
         * If it does not, the symptom is the one [Sdk] describes: `400 {"msg":"Invalid type"}`
         * and a row recorded nowhere, visible in the `StartSession` log and nowhere else.
         */
        Ads("ads"),
    }

    /** How a stay or a rental leaves, which is only ever out to the supplier's own page. */
    private const val DeeplinkType = "deeplink"

    /**
     * A flight search handed off to results.
     *
     * [exit] defaults to [FlightExit.Sdk] because that is what the search card does when the
     * controller has not said otherwise — hands straight to the SDK's results screen. It is a
     * parameter rather than a constant so the in-app and deeplinked flight flows report themselves
     * honestly instead of arriving in the backend's numbers as SDK traffic.
     *
     * [tag] names the booking site an [FlightExit.Api] search was handed off to, which is the one
     * thing the exit knows that the search that produced it could not. Left null everywhere else:
     * a deeplink or an SDK handoff names no supplier until the user is already outside this app.
     */
    fun flightSearch(
        search: TripSearch,
        exit: FlightExit = FlightExit.Sdk,
        tag: String? = null,
    ) = send(
        vertical = "flight",
        serializer = FlightSessionRequest.serializer(),
        post = { apiService.startFlightSession(it) },
        build = { envelope ->
            FlightSessionRequest(
                userId = envelope.userId,
                type = exit.wireValue,
                tag = tag,
                route = envelope.route,
                vertical = envelope.vertical,
                countryCode = envelope.countryCode,
                gclid = envelope.gclid,
                fbclid = envelope.fbclid,
                msclkid = envelope.msclkid,
                attribution = envelope.attribution,
                params = FlightSessionParams(
                    origin = search.from.code,
                    destination = search.to.code,
                    startDate = search.departureDate.asIsoDate(),
                    // Left absent on a one-way rather than sent empty: the backend reads the two
                    // trip shapes apart on this field being there at all.
                    endDate = search.returnDate?.takeIf { search.isRoundTrip }?.asIsoDate(),
                    cabinClass = search.cabinClass.sessionValue(),
                    adults = search.adults,
                    children = search.children,
                    infants = search.infants,
                ),
            )
        },
    )

    /**
     * An inline ad being tapped, recorded against the trip it was shown for.
     *
     * The same body a flight search sends — the backend reads an ad against the route it appeared
     * on, which is the only thing that makes it comparable with the searches around it — with
     * [FlightExit.Ads] as the type and the ad's supplier as the tag.
     *
     * Fired beside `FlightAnalytics.adClick`, so the two counts of an ad tap agree, exactly as
     * they do for every other exit. The reference app does the same from `AdResultViewModel`.
     */
    fun adClick(search: TripSearch, site: String?) =
        flightSearch(search = search, exit = FlightExit.Ads, tag = site)

    /** A stay search handed to the supplier's results page. */
    fun staySearch(search: HotelSearch) = send(
        vertical = "hotel",
        serializer = HotelSessionRequest.serializer(),
        post = { apiService.startHotelSession(it) },
        build = { envelope ->
            HotelSessionRequest(
                userId = envelope.userId,
                type = DeeplinkType,
                route = envelope.route,
                vertical = envelope.vertical,
                countryCode = envelope.countryCode,
                gclid = envelope.gclid,
                fbclid = envelope.fbclid,
                msclkid = envelope.msclkid,
                attribution = envelope.attribution,
                params = HotelSessionParams(
                    // The name the user saw, not the ids the deeplink sends: this is read by people
                    // looking at what was searched for.
                    location = search.destination?.cityName ?: search.city,
                    checkInDate = search.checkInDate.asIsoDate(),
                    checkOutDate = search.checkOutDate.asIsoDate(),
                    adults = search.adults,
                    children = search.childAges.size,
                    rooms = search.roomCount,
                ),
            )
        },
    )

    /** A rental search handed to the supplier's results page. */
    fun rentalSearch(search: CarSearch) = send(
        vertical = "car",
        serializer = CarSessionRequest.serializer(),
        post = { apiService.startCarSession(it) },
        build = { envelope ->
            CarSessionRequest(
                userId = envelope.userId,
                type = DeeplinkType,
                route = envelope.route,
                vertical = envelope.vertical,
                countryCode = envelope.countryCode,
                gclid = envelope.gclid,
                fbclid = envelope.fbclid,
                msclkid = envelope.msclkid,
                attribution = envelope.attribution,
                params = CarSessionParams(
                    pickupLocation = search.pickupLocation.sessionName() ?: search.city,
                    // Same encoding as the deeplink: a same-location return has no drop-off at all
                    // rather than a copy of the pick-up.
                    dropoffLocation = if (search.isSameDropoff) {
                        null
                    } else {
                        search.dropoffLocation.sessionName() ?: search.dropoffCity
                    },
                    pickupDate = search.pickupDate.asIsoDate(),
                    dropoffDate = search.dropoffDate.asIsoDate(),
                    pickupTime = search.pickupTime.asDeskTime(),
                    dropoffTime = search.dropoffTime.asDeskTime(),
                ),
            )
        },
    )

    /**
     * Fills in the half of the body that is the same whatever was searched for, sends it, and says
     * in the log what came back.
     *
     * [serializer] is here for the log rather than for the request — Ktor serialises the body
     * itself — because a session the backend rejects is only diagnosable against the exact JSON it
     * rejected, and that JSON is visible nowhere else: this call has no UI, no retry, and the user
     * is already in the supplier's page by the time it answers.
     *
     * Skipped without a registered id: the row would be attributed to nobody, and the backend keys
     * a session on the user it belongs to. That is a real gap on a first launch, where the sign-up
     * behind the splash can still be in flight when the first search leaves — logged rather than
     * silently dropped, so it is visible if it turns out to happen often.
     */
    private fun <T> send(
        vertical: String,
        serializer: SerializationStrategy<T>,
        build: (SessionEnvelope) -> T,
        post: suspend (T) -> StartSessionResponse,
    ) {
        if (!sessionTrackingEnabled) return

        val session = UserSession.current
        if (!session.isRegistered) {
            logDebug(
                StartSessionLogTag,
                "SKIPPED $vertical session — this install has no user_id yet",
            )
            return
        }

        val referrer = parseReferrerParameters(session.referrerUrl)
        val request = build(
            SessionEnvelope(
                userId = session.userId,
                vertical = vertical,
                // What `users/add` recorded, repeated so a session reads on its own. Falls back to
                // the platform default only if an install registered before that field was stored.
                route = session.acquiredRoute.ifBlank { Constants.Defaults.Route },
                countryCode = AppSettings.current.countryCode.uppercase(),
                // Re-read from the stored referrer rather than kept on the session: these are only
                // ever needed here, and the referrer they come from is already persisted.
                gclid = referrer["gclid"],
                fbclid = referrer["fbclid"],
                msclkid = referrer["msclkid"],
                attribution = session.attribution,
            ),
        )

        scope.launch {
            // The body exactly as it goes on the wire, encoded with the client's own Json so the
            // line cannot drift from what was sent. The reference app's START_SESSION_BODY.
            logDebug(
                StartSessionLogTag,
                "REQUEST $vertical → POST ${Constants.User.StartSessionPath} " +
                    runCatching { UserHttpClient.UserJson.encodeToString(serializer, request) }
                        .getOrElse { "(body could not be encoded: ${it.message})" },
            )
            try {
                val response = post(request)
                val sessionId = response.userSessionId
                if (sessionId == null) {
                    // Same shape of failure as `users/add` answering without a user id: a 2xx that
                    // recorded nothing, which no retry would fix and which looks like success.
                    logWarning(
                        StartSessionLogTag,
                        "FAILED $vertical session — 2xx carried no user_session_id " +
                            "(msg=${response.msg ?: "(none)"})",
                    )
                } else {
                    logDebug(
                        StartSessionLogTag,
                        "SUCCESS $vertical session — user_session_id=$sessionId " +
                            "user_id=${response.userId ?: "(none)"} " +
                            "msg=${response.msg ?: "(none)"}",
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Not retried, unlike the sign-up: this is a record of something that already
                // happened, and the user is in the supplier's page by now. A lost row is a lost
                // row — worth knowing about, not worth holding a coroutine open for.
                logWarning(
                    StartSessionLogTag,
                    "FAILED $vertical session — ${describeFailure(error)}",
                    error,
                )
            }
        }
    }

    /**
     * What went wrong, in the terms the backend owner would ask about — the same treatment
     * `UserRepository` gives a failed sign-up, error body included, because a status alone does not
     * separate a rejected user id from a malformed `params`.
     */
    private suspend fun describeFailure(error: Throwable): String = when (error) {
        is ResponseException -> {
            val body = runCatching { error.response.bodyAsText() }.getOrDefault("")
            "HTTP ${error.response.status} body=${body.take(MaxLoggedBodyChars).ifBlank { "(empty)" }}"
        }
        else -> "${error::class.simpleName}: ${error.message ?: "(no message)"}"
    }

    /** Enough of an error body to identify it; the platform log truncates a long line regardless. */
    private const val MaxLoggedBodyChars = 300
}

/** The envelope fields, resolved once so the three verticals cannot fill them differently. */
private data class SessionEnvelope(
    val userId: Int,
    val vertical: String,
    val route: String,
    val countryCode: String,
    val gclid: String?,
    val fbclid: String?,
    val msclkid: String?,
    val attribution: Attribution?,
)

/**
 * Whether this platform records sessions at all.
 *
 * True on Android, false on iOS — the one line that makes this an Android-only feature, rather than
 * the feature itself being written twice.
 */
internal expect val sessionTrackingEnabled: Boolean

/** The card's own date text, as the endpoint wants it. Null rather than guessed if it will not parse. */
private fun String.asIsoDate(): String? = CalendarDate.parse(this)?.isoDate

/** "HH:mm", falling back the way the rental deeplink does rather than dropping the time. */
private fun String.asDeskTime(): String = takeIf { DeskTime.matches(it) } ?: "00:00"

private val DeskTime = Regex("""\d{2}:\d{2}""")

/**
 * What to call this end of the rental in the session row.
 *
 * The human name, not the id the deeplink sends: an airport reads better as its own name than as
 * "SFO", and this field is only ever read by someone looking at what was searched for.
 */
private fun CarLocation?.sessionName(): String? = when {
    this == null -> null
    type == CarLocationType.Airport -> name.takeIf(String::isNotBlank) ?: iata
    else -> name.takeIf(String::isNotBlank)
}

/** The supplier's own words for a cabin, which is what the reference app sends. */
private fun CabinClass.sessionValue(): String = when (this) {
    CabinClass.PremiumEconomy -> "premiumEconomy"
    CabinClass.Business -> "business"
    CabinClass.First -> "first"
    CabinClass.Economy -> "economy"
}
