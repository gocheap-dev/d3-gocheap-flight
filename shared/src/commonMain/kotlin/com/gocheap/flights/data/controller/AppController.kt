package com.gocheap.flights.data.controller

import com.gocheap.flights.data.model.controller.ControllerConfig
import com.gocheap.flights.data.network.ApiService
import com.gocheap.flights.data.settings.AppSettings
import com.gocheap.flights.data.storage.PersistedValue
import com.gocheap.flights.data.user.UserSession
import com.gocheap.flights.data.user.platformAppCode
import com.gocheap.flights.utils.logDebug
import com.gocheap.flights.utils.logWarning
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** The tag the controller fetch logs under: `adb logcat -s Controller`. */
internal const val ControllerLogTag = "Controller"

/**
 * The switches this build runs under, fetched once per launch from `GET /controller/`.
 *
 * The reference app's arrangement, with its two deliberate properties kept:
 *
 * **Stored, so a launch that cannot reach the backend still has an answer.** The last good response
 * survives the process; only a first launch that also fails falls all the way back to
 * [ControllerConfig]'s defaults, which are "behave as though there were no controller".
 *
 * **Failure is not an error state.** The reference stores an empty response on failure rather than
 * leaving the previous one in place, so a backend that has gone away cannot pin the app to a stale
 * decision forever. This does the same — see [refresh].
 *
 * Where it differs: the reference gates its splash on this call finishing, in parallel with
 * `users/add`. Nothing here waits for it, for the same reason [com.gocheap.flights.data.user.UserRegistration]
 * does not gate the splash — every switch has a working default, so making the launch wait would
 * buy a slower start and nothing else.
 */
internal object AppController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apiService = ApiService()
    private val storage = PersistedValue("app_controller", ControllerConfig.serializer())

    /** The country watcher — see [start]. Held so a second call cannot start a second one. */
    private var watcher: Job? = null

    /**
     * The fetch in flight, cancelled by the next one.
     *
     * Without this, two refreshes started close together race, and the slower one wins by landing
     * last — which is exactly the case this is for: a first launch changes country once detection
     * finishes, moments after the launch fetch went out under the old one.
     */
    private var refreshJob: Job? = null

    private val _state = MutableStateFlow(storage.load(ControllerConfig()))

    /** For composition, which is how a screen would watch a switch flipping mid-launch. */
    val state: StateFlow<ControllerConfig> = _state.asStateFlow()

    /** Read straight through for callers outside composition. Never null — see the class comment. */
    val current: ControllerConfig get() = _state.value

    /**
     * Where a flight search goes, as the backend's `new_tab` decides it.
     *
     * "KYK_API" keeps the search in-app and renders it off the search/poll endpoints; "KYK_DL"
     * sends it out to the supplier's own results page; "TPT_SDK" hands it to the Travelpayouts
     * SDK's screen. Matched on the suffix rather than on the whole string — the reference app
     * tests `new_tab?.contains("DL")` — so a value that gains a prefix or a version still routes
     * rather than silently falling back.
     *
     * API is tested first because it is the only one of the three whose marker cannot appear
     * inside another's: "KYK_API" carries neither "DL" nor "SDK", but testing it last would leave
     * the order load-bearing for no reason.
     *
     * [FlightSearchMode.Sdk] when the controller has said nothing: on a first launch the switch
     * arrives after the screen does, and the SDK is what this app did before there was a choice.
     * The alternative — holding the search until an answer lands — would make a failed controller
     * call into a broken search button.
     */
    val flightSearchMode: FlightSearchMode get() = flightSearchModeFor(current.newTab)

    /**
     * Fetches on launch, and again whenever the region changes.
     *
     * **The answer is per country**, which is the reason this watches rather than fetching once:
     * the backend resolves `new_tab` against the country it is asked about, and the same build
     * genuinely gets different answers — "TPT_DL" for US and "KYK_DL" for GB on the same app code.
     * A region changed on the Profile tab without a re-fetch leaves the app routing its flight
     * searches the way the *previous* country said to.
     *
     * Only the country is watched. The endpoint takes country, app code and route, and the other
     * two cannot change while the app is running — so currency and language are not its business.
     *
     * The current country is the collector's first emission, so this also performs the launch
     * fetch; there is no separate initial call to keep in step with it. Idempotent: calling it
     * twice does not start a second watcher.
     */
    fun start() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            AppSettings.state
                .map { it.countryCode }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    /**
     * Fetches the switches for this country, app and route, and keeps whatever comes back.
     *
     * Returns immediately; nothing on screen waits for it. Public for a deliberate re-fetch;
     * the region change that normally triggers one is handled by [start].
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val country = AppSettings.current.countryCode.uppercase()
            val route = UserSession.acquiredRouteOrDefault

            logDebug(
                ControllerLogTag,
                "REQUEST GET controller/ country=$country app_code=$platformAppCode route=$route",
            )

            try {
                val config = apiService.controller(
                    country = country,
                    appCode = platformAppCode,
                    route = route,
                )
                save(config)
                // The whole record, not a summary of the switches this app happens to read today:
                // the backend adds keys without notice, and a switch nothing consumes yet is still
                // the thing someone will be asking about. The reference logs the same.
                logDebug(ControllerLogTag, "SUCCESS — ${config.asLog()}")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Defaults, not the previous answer: a backend that has gone away must not pin this
                // install to a decision it made weeks ago. The reference does exactly this.
                val fallback = ControllerConfig()
                save(fallback)
                logWarning(
                    ControllerLogTag,
                    "FAILED — ${describeFailure(error)} — running on ${fallback.asLog()}",
                    error,
                )
            }
        }
    }

    /**
     * The config as JSON, for the log only.
     *
     * Its own [Json] rather than the flight client's, which is built inline and private to it:
     * this one encodes defaults, so a switch the backend left out still shows the value the app
     * will actually run on instead of vanishing from the line.
     */
    private fun ControllerConfig.asLog(): String =
        runCatching { LogJson.encodeToString(ControllerConfig.serializer(), this) }
            .getOrElse { "(could not be encoded: ${it.message})" }

    private val LogJson = Json { encodeDefaults = true }

    private fun save(config: ControllerConfig) {
        _state.value = config
        storage.save(config)
    }

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

/** The three ways a flight search can be answered. */
internal enum class FlightSearchMode {
    /** Fetched and drawn in-app off `api/v3/search/` and `api/v3/poll/`. */
    Api,

    /** Handed to the supplier's own results page in a web view. */
    Deeplink,

    /** Handed to the Travelpayouts SDK, which brings its own screen. */
    Sdk,
}

/**
 * The `new_tab` mapping itself, as a free function rather than a member of [AppController].
 *
 * Deliberately outside the object: touching any member of it initialises the whole thing —
 * storage, an HTTP client and a coroutine scope — and this mapping is the one part of the
 * decision worth pinning in a test. A test that had to stand up storage to assert on a string
 * match would be testing the storage.
 */
internal fun flightSearchModeFor(newTab: String?): FlightSearchMode {
    val tab = newTab.orEmpty()
    return when {
        tab.contains("API", ignoreCase = true) -> FlightSearchMode.Api
        tab.contains("DL", ignoreCase = true) -> FlightSearchMode.Deeplink
        tab.contains("SDK", ignoreCase = true) -> FlightSearchMode.Sdk
        else -> FlightSearchMode.Sdk
    }
}
