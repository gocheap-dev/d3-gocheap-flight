package com.gocheap.flights.data.flight

import com.gocheap.flights.data.model.CabinClass
import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.data.model.TripSearch
import com.gocheap.flights.data.model.flight.api.DeleteSearchResponse
import com.gocheap.flights.data.model.flight.api.FlightPollResponse
import com.gocheap.flights.data.model.flight.api.FlightSearchFilters
import com.gocheap.flights.data.model.flight.api.FlightSearchLeg
import com.gocheap.flights.data.model.flight.api.FlightSearchRequest
import com.gocheap.flights.data.model.flight.api.FlightSearchResponse
import com.gocheap.flights.data.network.ApiService
import com.gocheap.flights.data.settings.AppSettings
import com.gocheap.flights.data.user.UserSession
import com.gocheap.flights.data.user.platformAppCode
import com.gocheap.flights.utils.Constants
import com.gocheap.flights.utils.logDebug
import com.gocheap.flights.utils.logWarning
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** The tag the in-app flight search logs under: `adb logcat -s FlightApi`. */
internal const val FlightApiLogTag = "FlightApi"

/**
 * The in-app flight search — the `KYK_API` third of the controller's choice.
 *
 * Sits between the ViewModel and [ApiService]: turns the app's own [TripSearch] into the shape the
 * endpoint wants, reads the region and user id off storage so the ViewModel does not have to, and
 * returns [Result] rather than throwing, because every one of these calls is expected to fail
 * sometimes and the screen has something to show for each failure.
 *
 * Deliberately separate from [FlightRepository], which serves the app's local/demo flight data and
 * has no backend behind it. The two answer different questions and share nothing.
 */
internal class FlightSearchRepository(
    private val apiService: ApiService = ApiService(),
) {

    /**
     * Opens a search and returns the id to poll on.
     *
     * The region, the user id and the acquisition route are read here rather than passed in: they
     * are the same on every call and come from stores the ViewModel has no other reason to touch.
     *
     * [provider] names a single supplier to search rather than all of them — the reference app
     * sends "sky" for a search opened off its Explore tab. Null, which is every search this app
     * runs today, searches everything.
     */
    suspend fun openSearch(
        search: TripSearch,
        provider: String? = null,
    ): Result<FlightSearchResponse> = runCatchingApi("search") {
        val settings = AppSettings.current
        val request = search.toApiRequest()
        // The whole body and the whole query, as the reference app's API_LOG prints them. Enough
        // to replay the call with curl, which is how a client fault gets told apart from a backend
        // one without a debugger attached.
        logDebug(
            FlightApiLogTag,
            "SEARCH REQUEST POST ${Constants.Flight.SearchPath}" +
                "?user_id=${UserSession.deeplinkUserId}" +
                "&app_code=$platformAppCode" +
                "&currency=${settings.currencyCode}" +
                "&language=${settings.languageCode}" +
                "&route=${UserSession.acquiredRouteOrDefault}" +
                (provider?.let { "&provider=$it" } ?: "") +
                (UserSession.current.attribution?.campaignId?.let { "&orkey=$it" } ?: "") +
                " [header] country=${settings.countryCode.uppercase()}" +
                " [body] ${request.asLog()}",
        )
        apiService.searchFlight(
            request = request,
            userId = UserSession.deeplinkUserId,
            country = settings.countryCode.uppercase(),
            currency = settings.currencyCode,
            language = settings.languageCode,
            route = UserSession.acquiredRouteOrDefault,
            provider = provider,
            // What the install was attributed to, which the backend uses to key the search back to
            // the campaign that produced it. Absent on an organic install, and omitted then.
            orkey = UserSession.current.attribution?.campaignId,
        ).also {
            logDebug(
                FlightApiLogTag,
                "SEARCH RESPONSE search_id=${it.searchId ?: "(none)"} " +
                    "currency=${it.currency ?: "-"} provider=${it.provider ?: "-"} mode=${it.mode ?: "-"}",
            )
        }
    }

    /**
     * Fetches one page of results for an open search.
     *
     * Called in a loop until the response says its cache is full — see [FlightPollResponse]. The
     * loop itself lives in the ViewModel, because what to do between polls is a UI decision.
     */
    suspend fun poll(
        searchId: String,
        page: Int = 1,
        filters: FlightSearchFilters = FlightSearchFilters.Unfiltered,
    ): Result<FlightPollResponse> = runCatchingApi("poll") {
        logDebug(
            FlightApiLogTag,
            "POLL REQUEST GET ${Constants.Flight.PollPath}?search_id=$searchId&page=$page" +
                "&limit=${Constants.Flight.Search.PollPageSize} [filters] $filters",
        )
        apiService.pollFlights(
            searchId = searchId,
            page = page,
            filters = filters,
        ).also {
            logDebug(
                FlightApiLogTag,
                "POLL RESPONSE page=$page cache=${it.cache} results=${it.results?.size ?: 0} " +
                    "count=${it.count ?: 0} hasNext=${!it.isLastPage} " +
                    "price=[${it.minPrice ?: "-"}..${it.maxPrice ?: "-"}] " +
                    "duration=[${it.minDuration ?: "-"}..${it.maxDuration ?: "-"}]min " +
                    "airlines=${it.airlines?.size ?: 0} agencies=${it.agencies?.size ?: 0}",
            )
        }
    }

    /**
     * Releases a search the user has walked away from.
     *
     * Best effort by design: a failure here costs the backend one cache entry it drops on its own
     * anyway, and the screen it was called from is already closing.
     */
    suspend fun releaseSearch(searchId: String): Result<DeleteSearchResponse> =
        runCatchingApi("delete") { apiService.deleteFlightSearch(searchId) }

    /**
     * The app's trip as the endpoint's body.
     *
     * Three translations happen here and each is load-bearing:
     *
     * - **Cities, not airports.** [com.gocheap.flights.data.model.Airport.searchCityCode] is sent,
     *   so a search from Heathrow searches London. The supplier groups its results by city and
     *   finds nothing under an airport code that sits inside one.
     * - **A return is a second leg**, not a second date — the same shape the deeplink builder uses.
     * - **The party is a list of ages.** See [FlightSearchRequest.passengers]. Lap infants are not
     *   in it: they do not occupy a seat and the endpoint prices them separately.
     */
    private fun TripSearch.toApiRequest(): FlightSearchRequest {
        val outbound = FlightSearchLeg(
            origin = from.searchCityCode,
            destination = to.searchCityCode,
            date = departureDate.asIsoDate().orEmpty(),
        )
        // Only on a round trip, and only once the return date parses — an unparseable one would
        // otherwise be sent as an empty date and rejected as a malformed leg.
        val inbound = returnDate
            ?.takeIf { isRoundTrip }
            ?.asIsoDate()
            ?.let {
                FlightSearchLeg(
                    origin = to.searchCityCode,
                    destination = from.searchCityCode,
                    date = it,
                )
            }

        val seated = List(adults.coerceAtLeast(1)) { FlightSearchRequest.AdultAge } +
            List(children.coerceAtLeast(0)) { ChildAge }

        return FlightSearchRequest(
            legs = listOfNotNull(outbound, inbound),
            cabinClass = cabinClass.apiValue(),
            passengers = seated.ifEmpty { listOf(FlightSearchRequest.AdultAge) },
            infantsInSeat = 0,
        )
    }

    /**
     * One place for the three calls' shared error handling: log it, wrap it, and never swallow a
     * cancellation — a search abandoned because the screen closed is not a search that failed.
     */
    private suspend inline fun <T> runCatchingApi(
        label: String,
        crossinline block: suspend () -> T,
    ): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        val classified = error.classify()
        logWarning(FlightApiLogTag, "$label FAILED [${classified.failure}] — ${classified.message}", error)
        Result.failure(classified)
    }

    /**
     * Sorts a thrown call into "the backend will never run this" and "try again".
     *
     * The body is read for the log only, and defensively: it is the difference between chasing a
     * client bug for an afternoon and reading "Provided API key is disabled" — but a failed read
     * must not turn one failure into a different one, so it falls back to the status alone.
     */
    private suspend fun Throwable.classify(): FlightSearchException {
        if (this !is ResponseException) {
            return FlightSearchException(
                failure = FlightSearchFailure.Transport,
                message = "${this::class.simpleName}: ${message ?: "(no message)"}",
                cause = this,
            )
        }
        val status = response.status.value
        val body = runCatching { response.bodyAsText() }.getOrDefault("").take(MaxLoggedBodyChars)
        return FlightSearchException(
            failure = flightSearchFailureFor(status),
            message = "HTTP $status ${body.ifBlank { "(empty body)" }}",
            cause = this,
        )
    }

    private companion object {
        /** Enough of an error body to identify it; the platform log truncates a long line anyway. */
        const val MaxLoggedBodyChars = 200

        /**
         * The age every child is sent as.
         *
         * A band, like [FlightSearchRequest.AdultAge] — the search card collects a count of
         * children rather than their ages, so one value inside the child band stands in for all of
         * them. Collecting real ages would price more accurately and is the change to make if the
         * card ever asks for them.
         */
        const val ChildAge = 8
    }
}

/**
 * The request body as the JSON that actually goes on the wire.
 *
 * Its own [Json] rather than the flight client's: this one encodes defaults, so a field left at
 * its default still shows the value the backend will receive instead of vanishing from the line —
 * the same reason `AppController` keeps a separate logging instance.
 */
private fun FlightSearchRequest.asLog(): String =
    runCatching { LogJson.encodeToString(FlightSearchRequest.serializer(), this) }
        .getOrElse { "(could not be encoded: ${it.message})" }

private val LogJson = Json { encodeDefaults = true }

/** The supplier's own words for a cabin — the same set `SessionTracking` sends. */
private fun CabinClass.apiValue(): String = when (this) {
    CabinClass.Economy -> FlightSearchRequest.DefaultCabinClass
    CabinClass.PremiumEconomy -> "premiumEconomy"
    CabinClass.Business -> "business"
    CabinClass.First -> "first"
}

private fun String.asIsoDate(): String? = CalendarDate.parse(this)?.isoDate
