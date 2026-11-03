package com.gocheap.flights.data.flight

/**
 * Why a flight search call failed, narrowed to the distinction the UI has to act on.
 *
 * [Unavailable] is the one that matters and the reason this type exists. The backend decides per
 * app and per country whether an in-app API search is possible at all, and it says so with a
 * status rather than with a flag the controller carries:
 *
 * - **403** — the app is known but the upstream provider's API key is off for it
 *   (`{"error":"Provided API key is disabled"}`, or a bare `Forbidden`).
 * - **423** — no provider is wired up for this app and country at all
 *   (`{"error":"No API provider configured for this app/country"}`).
 *
 * Neither is retryable and neither is the user's fault, so neither should be shown as "check your
 * connection and try again" — the answer will be identical every time. A search that comes back
 * this way has to leave for a path that does work instead; see `FlightApiResultsViewModel`.
 *
 * [Transport] is everything else — no network, a timeout, a 5xx, a body that would not parse.
 * Those are worth retrying, because the next attempt genuinely may differ.
 */
internal enum class FlightSearchFailure {
    /** The backend will not run an API search for this build. Retrying cannot help. */
    Unavailable,

    /** Something went wrong on the way. Retrying may help. */
    Transport,
}

/**
 * A failed flight API call, carrying [failure] so the UI can tell the two apart without having to
 * know what an HTTP status is.
 *
 * Wraps the original [cause] rather than replacing it, so the log still shows what actually went
 * wrong underneath the classification.
 */
internal class FlightSearchException(
    val failure: FlightSearchFailure,
    override val message: String,
    override val cause: Throwable?,
) : Exception(message, cause)

/**
 * Which kind of failure an HTTP status represents, apart from the call that produced it.
 *
 * A free function so it can be tested against the statuses the backend actually returns without
 * standing up a response to throw — the mapping is the decision worth pinning here, and getting
 * it wrong is silent: a misclassified 403 puts the user on a "try again" button that can never
 * succeed, and a misclassified 503 bounces them off a search that would have worked on retry.
 */
internal fun flightSearchFailureFor(status: Int): FlightSearchFailure = when (status) {
    // The app is known but the upstream provider's key is disabled for it.
    403 -> FlightSearchFailure.Unavailable
    // No provider is wired up for this app and country at all.
    423 -> FlightSearchFailure.Unavailable
    // Everything else — 5xx, 429, gateway timeouts — may genuinely answer differently next time.
    else -> FlightSearchFailure.Transport
}
