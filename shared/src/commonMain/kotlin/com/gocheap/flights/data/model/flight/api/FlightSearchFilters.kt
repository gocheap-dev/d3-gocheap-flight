package com.gocheap.flights.data.model.flight.api

/**
 * What the results screen is currently filtered and sorted by.
 *
 * Sent to `api/v3/poll/` as query parameters rather than applied on the page in hand: filtering is
 * the backend's job here, because the page on screen is a slice of a result set that is still
 * growing, and narrowing it locally would hide matches that have not arrived yet.
 *
 * Immutable, unlike the reference app's mutable equivalent. Every change goes through a `copy`
 * and restarts polling from page one, which is what a filter change means — the previous pages
 * were a different query's.
 *
 * [Unfiltered] is the default and encodes to no parameters at all.
 */
internal data class FlightSearchFilters(
    /** Minutes, as the results report their own durations. Itineraries longer are dropped. */
    val durationMax: Int? = null,
    /** Stop counts to keep, e.g. `setOf(0, 1)` for direct and one-stop. Empty means no limit. */
    val stopCounts: Set<Int> = emptySet(),
    /** One entry per leg, in leg order — the outbound's window, then the return's. */
    val timeRanges: List<LegTimeWindow> = emptyList(),
    /** Airline IATA codes to keep. Empty means all of them. */
    val airlinesInclude: Set<String> = emptySet(),
    /** Booking site codes to keep. Empty means all of them. */
    val agenciesInclude: Set<String> = emptySet(),
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val sort: FlightSort = FlightSort.Best,
) {
    /**
     * True when nothing has been narrowed — which is what the "clear all" button returns to, and
     * what decides whether the filter bar shows a count.
     *
     * Sorting is deliberately not part of it: a result set sorted by price is not a filtered one,
     * and counting it as a filter would put a badge on a bar the user never touched.
     */
    val isUnfiltered: Boolean get() = activeCount == 0

    /**
     * How many of the six filter groups are narrowing the results, for the badge on the filter
     * button. Groups, not values: picking four airlines is one active filter, not four.
     */
    val activeCount: Int get() = FlightFilterType.entries.count(::isActive)

    /**
     * Whether one group is narrowing the results, for that group's chip.
     *
     * The chip bar sorts active groups to the front and gives each one a clear button, so this is
     * asked per group rather than in aggregate — and it has to agree exactly with [activeCount],
     * which is why both read the same conditions rather than each deciding for itself.
     */
    fun isActive(type: FlightFilterType): Boolean = when (type) {
        FlightFilterType.Stops -> stopCounts.isNotEmpty()
        FlightFilterType.Time -> timeRanges.any { !it.isUnrestricted }
        FlightFilterType.Duration -> durationMax != null
        FlightFilterType.Price -> priceMin != null || priceMax != null
        FlightFilterType.Airlines -> airlinesInclude.isNotEmpty()
        FlightFilterType.Agencies -> agenciesInclude.isNotEmpty()
    }

    /**
     * This set with one group reset — what the × on an active chip applies.
     *
     * The sort is deliberately untouched: clearing a filter should not also reorder the list under
     * the user, and the sort is not one of the six groups anyway.
     */
    fun cleared(type: FlightFilterType): FlightSearchFilters = when (type) {
        FlightFilterType.Stops -> copy(stopCounts = emptySet())
        FlightFilterType.Time -> copy(timeRanges = emptyList())
        FlightFilterType.Duration -> copy(durationMax = null)
        FlightFilterType.Price -> copy(priceMin = null, priceMax = null)
        FlightFilterType.Airlines -> copy(airlinesInclude = emptySet())
        FlightFilterType.Agencies -> copy(agenciesInclude = emptySet())
    }

    internal companion object {
        val Unfiltered = FlightSearchFilters()
    }
}

/**
 * When a leg may depart and when it may land, as seconds from that airport's midnight.
 *
 * Both ends are local to their own airport, matching the timestamps the results carry — see
 * [FlightLeg.departureTimeAirport].
 */
internal data class LegTimeWindow(
    val departure: TimeWindow = TimeWindow.FullDay,
    val arrival: TimeWindow = TimeWindow.FullDay,
) {
    val isUnrestricted: Boolean get() = departure.isFullDay && arrival.isFullDay
}

/** A span of one day, in seconds from midnight. */
internal data class TimeWindow(
    val min: Long = 0,
    val max: Long = SecondsPerDay,
) {
    /**
     * True when this window excludes nothing — the whole day, or an inverted span that can only
     * have come from a slider dragged past itself.
     *
     * An inverted window counts as unrestricted rather than as "nothing matches": sending it would
     * empty the results with no filter visibly set, which reads as a broken search.
     */
    val isFullDay: Boolean get() = (min <= 0L && max >= SecondsPerDay) || min >= max

    /** `"28800-64800"`, clamped to the day. What the query parameter carries. */
    fun asQueryRange(): String = "${min.coerceIn(0L, SecondsPerDay)}-${max.coerceIn(0L, SecondsPerDay)}"

    internal companion object {
        const val SecondsPerDay = 86_400L
        val FullDay = TimeWindow()
    }
}

/**
 * The orders the results can be put in — the reference app's three, exactly.
 *
 * [Best] is the backend's own ranking, a blend of price and duration, and is what a fresh search
 * opens on. It is sent as no `sort_by` at all rather than as an explicit sort, because that is the
 * order the endpoint returns results in anyway.
 *
 * The order is always ascending. The reference sends `sort_order = "asc"` unconditionally and the
 * two sortable fields are price and duration, where ascending is the only direction anyone wants —
 * nobody sorts a fare list most-expensive-first.
 */
internal enum class FlightSort(
    val label: String,
    val sortBy: String?,
) {
    Best("Best", null),
    Cheapest("Cheapest", "price"),
    Fastest("Fastest", "duration"),
    ;

    /** Always ascending — see the class comment. Null on [Best], which sends no sort at all. */
    val sortOrder: String? get() = sortBy?.let { "asc" }
}

/** The six groups the filter bar offers, in the order the reference app lists them. */
internal enum class FlightFilterType {
    Stops,
    Time,
    Duration,
    Price,
    Airlines,
    Agencies,
}

/**
 * The legs' departure windows as one parameter: `"28800-64800,0-86400"`, in leg order.
 *
 * Null when no leg restricts its departure. Unrestricted legs *before* a restricted one are sent
 * as empty slots so the positions still line up with the legs; trailing empty slots are dropped,
 * which is how the reference app builds it and what the backend parses.
 */
internal fun List<LegTimeWindow>.departureRangesQuery(): String? = rangesQuery { it.departure }

/** The same, for arrivals. */
internal fun List<LegTimeWindow>.arrivalRangesQuery(): String? = rangesQuery { it.arrival }

private fun List<LegTimeWindow>.rangesQuery(select: (LegTimeWindow) -> TimeWindow): String? =
    map { window -> select(window).takeUnless { it.isFullDay }?.asQueryRange().orEmpty() }
        .dropLastWhile(String::isEmpty)
        .joinToString(",")
        .takeIf(String::isNotEmpty)
