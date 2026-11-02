package com.gocheap.travel.data.network

import com.gocheap.travel.data.model.car.CarDestinationDto
import com.gocheap.travel.data.model.controller.ControllerConfig
import com.gocheap.travel.data.model.flight.FlightDestinationDto
import com.gocheap.travel.data.model.flight.api.DeleteSearchResponse
import com.gocheap.travel.data.model.flight.api.FlightPollResponse
import com.gocheap.travel.data.model.flight.api.FlightSearchFilters
import com.gocheap.travel.data.model.flight.api.FlightSearchRequest
import com.gocheap.travel.data.model.flight.api.FlightSearchResponse
import com.gocheap.travel.data.model.flight.api.arrivalRangesQuery
import com.gocheap.travel.data.model.flight.api.departureRangesQuery
import com.gocheap.travel.data.model.hotel.HotelDestinationDto
import com.gocheap.travel.data.model.user.AddUserRequest
import com.gocheap.travel.data.model.user.AddUserResponse
import com.gocheap.travel.data.model.user.CarSessionRequest
import com.gocheap.travel.data.model.user.FlightSessionRequest
import com.gocheap.travel.data.model.user.HotelSessionRequest
import com.gocheap.travel.data.model.user.StartSessionResponse
import com.gocheap.travel.data.model.user.UpdatePushTokenRequest
import com.gocheap.travel.data.model.user.UpdatePushTokenResponse
import com.gocheap.travel.data.network.car.CarHttpClient
import com.gocheap.travel.data.network.flight.FlightHttpClient
import com.gocheap.travel.data.network.hotel.HotelHttpClient
import com.gocheap.travel.data.network.user.UserHttpClient
import com.gocheap.travel.data.user.platformAppCode
import com.gocheap.travel.utils.Constants
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
// Do not let "optimise imports" drop this: it is what makes client.get(...) below resolve to
// Ktor's request builder instead of MatchGroupCollection.get.
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import io.ktor.client.HttpClient as KtorHttpClient

/** Thin wrapper over the stay endpoints: builds the request, returns the parsed body, nothing else. */
internal class ApiService(
    // Aliased for the same reason as in HttpClient.kt: the object next door shares Ktor's name.
    private val hotelClient: KtorHttpClient = HotelHttpClient.instance,
    private val carClient: KtorHttpClient = CarHttpClient.instance,
    private val flightClient: KtorHttpClient = FlightHttpClient.instance,
    private val userClient: KtorHttpClient = UserHttpClient.instance,
) {

    /**
     * `GET /api/v4/autocomplete/?search=&country=&language=&app=&route=`
     *
     * Throws on transport failure or a non-2xx response — the repository is what turns that into a
     * result the UI can show.
     */
    suspend fun hotelAutocomplete(
        search: String,
        country: String = Constants.Defaults.Country,
        language: String = Constants.Defaults.Language,
        appCode: String = platformAppCode,
        route: String = Constants.Defaults.Route,
    ): List<HotelDestinationDto> = hotelClient.get(Constants.Hotel.AutocompletePath) {
        url {
            parameters.append("search", search)
            parameters.append("country", country)
            parameters.append("language", language)
            parameters.append("app", appCode)
            parameters.append("route", route)
        }
    }.body()

    /**
     * `GET /api/v3/autocomplete/?app_code=&route=&search=&country=&language=&switch=`
     *
     * A different endpoint version from [hotelAutocomplete], with its own parameter names —
     * `app_code` rather than `app`, plus a `switch` the stay endpoint has no equivalent for — so the
     * two are built separately rather than sharing one builder.
     *
     * Throws on transport failure or a non-2xx response — the repository is what turns that into a
     * result the UI can show.
     */
    suspend fun carAutocomplete(
        search: String,
        country: String = Constants.Defaults.Country,
        language: String = Constants.Defaults.Language,
        appCode: String = platformAppCode,
        route: String = Constants.Defaults.Route,
        switch: Boolean = false,
    ): List<CarDestinationDto> = carClient.get(Constants.Car.AutocompletePath) {
        url {
            parameters.append("app_code", appCode)
            parameters.append("route", route)
            parameters.append("search", search)
            parameters.append("country", country)
            parameters.append("language", language)
            parameters.append("switch", switch.toString())
        }
    }.body()

    /**
     * `GET /api/v3/autocomplete/?search=&country=&language=&app_code=&route=`
     *
     * Its own host and its own parameter set — no `switch`, and the country and language travel
     * under the plain names the stay endpoint uses — so it is built separately from the two above.
     *
     * Throws on transport failure or a non-2xx response — the repository is what turns that into a
     * result the UI can show.
     */
    suspend fun flightAutocomplete(
        search: String,
        country: String = Constants.Defaults.Country,
        language: String = Constants.Defaults.Language,
        appCode: String = platformAppCode,
        route: String = Constants.Defaults.Route,
    ): List<FlightDestinationDto> = flightClient.get(Constants.Flight.AutocompletePath) {
        url {
            parameters.append("search", search)
            parameters.append("country", country)
            parameters.append("language", language)
            parameters.append("app_code", appCode)
            parameters.append("route", route)
        }
    }.body()

    /**
     * `GET /controller/?country=&app_code=&route=` — the switches this build runs under.
     *
     * Three parameters, which is the whole request: the backend resolves a different answer per
     * country, per app and per acquisition route, and sends no body back beyond the switches.
     *
     * Throws on transport failure or a non-2xx response; `AppController` is what turns that into
     * the defaults rather than into a retry.
     */
    suspend fun controller(
        country: String = Constants.Defaults.Country,
        appCode: String = platformAppCode,
        route: String = Constants.Defaults.Route,
    ): ControllerConfig = flightClient.get(Constants.Flight.ControllerPath) {
        url {
            parameters.append("country", country)
            parameters.append("app_code", appCode)
            parameters.append("route", route)
        }
    }.body()

    /**
     * `POST users/add/` — registers this install and answers with the id everything else is keyed
     * on, plus whatever campaign the backend resolved the referrer to.
     *
     * The only write in this file, so it is the only call that sets a Content-Type; the autocomplete
     * endpoints above send no body at all.
     *
     * Throws on transport failure or a non-2xx response — [com.gocheap.travel.data.user.UserRepository]
     * is what turns that into a retry.
     */
    suspend fun addUser(request: AddUserRequest): AddUserResponse =
        userClient.post(Constants.User.AddUserPath) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /**
     * `POST users/update-push-token/` — stores the FCM registration token this install can be
     * reached at.
     *
     * Keyed on the `user_id` [addUser] produced, so it cannot usefully be called before that has
     * succeeded; `PushTokenRegistration` is what waits for both halves.
     *
     * Throws on transport failure or a non-2xx response — and this endpoint answers 400 for a
     * missing field and for an unknown `user_id`, both of which arrive that way.
     */
    suspend fun updatePushToken(request: UpdatePushTokenRequest): UpdatePushTokenResponse =
        userClient.post(Constants.User.UpdatePushTokenPath) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /**
     * `POST users/session/` — one call per vertical, because the three bodies are three types and
     * Ktor serialises what it is handed statically.
     *
     * Throws on transport failure or a non-2xx response, as the calls above do.
     */
    suspend fun startFlightSession(request: FlightSessionRequest): StartSessionResponse =
        postSession(request)

    suspend fun startHotelSession(request: HotelSessionRequest): StartSessionResponse =
        postSession(request)

    suspend fun startCarSession(request: CarSessionRequest): StartSessionResponse =
        postSession(request)

    /**
     * `POST api/v3/search/` — opens an in-app flight search and answers with the id to poll on.
     *
     * The trip itself travels as the body; who is asking, and in what currency and language the
     * answer should come back, travel as query parameters. [country] is the exception — it goes in
     * a header, which is the endpoint's own arrangement and not a choice made here.
     *
     * [provider] and [orkey] are omitted entirely when null rather than sent empty: the backend
     * treats a present-but-blank `provider` as a supplier named "" and finds nothing.
     *
     * Given its own timeout, well past the client's 15s — see [Constants.Flight.Search.TimeoutMillis].
     *
     * Throws on transport failure or a non-2xx response; the repository turns that into a result.
     */
    suspend fun searchFlight(
        request: FlightSearchRequest,
        userId: String,
        appCode: String = platformAppCode,
        country: String = Constants.Defaults.Country,
        currency: String = Constants.Defaults.Currency,
        language: String = Constants.Defaults.Language,
        route: String = Constants.Defaults.Route,
        provider: String? = null,
        orkey: String? = null,
    ): FlightSearchResponse = flightClient.post(Constants.Flight.SearchPath) {
        url {
            parameters.append("user_id", userId)
            parameters.append("app_code", appCode)
            parameters.append("currency", currency)
            parameters.append("language", language)
            parameters.append("route", route)
            provider?.let { parameters.append("provider", it) }
            orkey?.let { parameters.append("orkey", it) }
        }
        header("country", country)
        contentType(ContentType.Application.Json)
        setBody(request)
        timeout { requestTimeoutMillis = Constants.Flight.Search.TimeoutMillis }
    }.body()

    /**
     * `GET api/v3/poll/` — one page of results for a search already opened.
     *
     * Called repeatedly against the same [searchId] until the response says its cache is full;
     * see [FlightPollResponse]. The filters ride along on every call rather than being applied to
     * what comes back, because the result set is still growing.
     *
     * Throws on transport failure or a non-2xx response; the repository turns that into a result.
     */
    suspend fun pollFlights(
        searchId: String,
        page: Int = 1,
        limit: Int = Constants.Flight.Search.PollPageSize,
        filters: FlightSearchFilters = FlightSearchFilters.Unfiltered,
    ): FlightPollResponse = flightClient.get(Constants.Flight.PollPath) {
        url {
            parameters.append("search_id", searchId)
            parameters.append("limit", limit.coerceAtLeast(1).toString())
            parameters.append("page", page.coerceAtLeast(1).toString())
            parameters.appendFilters(filters)
        }
        timeout { requestTimeoutMillis = Constants.Flight.Search.TimeoutMillis }
    }.body()

    /**
     * `DELETE api/search/delete/` — releases a search the user has walked away from.
     *
     * Throws like the rest; the repository swallows it, because a failed release costs the backend
     * a cache entry it drops on its own and costs the user nothing at all.
     */
    suspend fun deleteFlightSearch(searchId: String): DeleteSearchResponse =
        flightClient.delete(Constants.Flight.DeleteSearchPath) {
            url { parameters.append("search_id", searchId) }
        }.body()

    /**
     * The filters, as the parameter names the endpoint reads them under.
     *
     * Nothing unset is sent. An empty `airline_include` would be read as "include no airlines"
     * rather than as "no airline filter", which is the difference between every result and none.
     */
    private fun ParametersBuilder.appendFilters(filters: FlightSearchFilters) {
        filters.durationMax?.let { append("duration_max", it.toString()) }
        filters.stopCounts
            .takeIf { it.isNotEmpty() }
            ?.let { append("stop_counts", it.sorted().joinToString(",")) }
        filters.airlinesInclude
            .takeIf { it.isNotEmpty() }
            ?.let { append("airline_include", it.joinToString(",")) }
        filters.agenciesInclude
            .takeIf { it.isNotEmpty() }
            ?.let { append("provider_include", it.joinToString(",")) }
        filters.priceMin?.let { append("price_min", it.toString()) }
        filters.priceMax?.let { append("price_max", it.toString()) }
        filters.sort.sortBy?.let { append("sort_by", it) }
        filters.sort.sortOrder?.let { append("sort_order", it) }
        filters.timeRanges.departureRangesQuery()?.let { append("departure_time_ranges", it) }
        filters.timeRanges.arrivalRangesQuery()?.let { append("arrival_time_ranges", it) }
    }

    /**
     * Private so it can be inline: a reified body type is what lets one function serialise all
     * three, and only a private inline function may touch [userClient] without publishing it.
     */
    private suspend inline fun <reified T> postSession(request: T): StartSessionResponse =
        userClient.post(Constants.User.StartSessionPath) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
