package com.gocheap.travel.ui.home.results.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.travel.analytics.ga4.AnalyticsEvent
import com.gocheap.travel.analytics.ga4.FlightAnalytics
import com.gocheap.travel.data.flight.FlightApiLogTag
import com.gocheap.travel.data.flight.FlightSearchException
import com.gocheap.travel.data.flight.FlightSearchFailure
import com.gocheap.travel.data.flight.FlightSearchRepository
import com.gocheap.travel.data.model.Airport
import com.gocheap.travel.data.model.CabinClass
import com.gocheap.travel.data.model.TripSearch
import com.gocheap.travel.data.model.flight.api.CurrencyInfo
import com.gocheap.travel.data.model.flight.api.FlightAgency
import com.gocheap.travel.data.model.flight.api.FlightFilterType
import com.gocheap.travel.data.model.flight.api.FlightAirline
import com.gocheap.travel.data.model.flight.api.FlightDeal
import com.gocheap.travel.data.model.flight.api.FlightOption
import com.gocheap.travel.data.model.flight.api.FlightPollResponse
import com.gocheap.travel.data.model.flight.api.FlightSearchFilters
import com.gocheap.travel.data.model.flight.api.FlightSort
import com.gocheap.travel.data.recent.RecentSearchStore
import com.gocheap.travel.data.user.SessionTracking
import com.gocheap.travel.utils.Constants
import com.gocheap.travel.utils.logDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Why a results screen has nothing to show.
 *
 * [Empty] is not an error and the screen says so differently: the search ran, the suppliers
 * answered, and the answer was that nothing flies this route on this day. [FilteredOut] is the
 * same emptiness with a way out of it, so the screen offers to clear the filters.
 */
internal enum class FlightApiFailure {
    /** The search or a poll could not be reached, or came back refusing to answer. */
    Network,

    /** The search ran and found nothing. */
    Empty,

    /** Results exist, but none match the filters currently set. */
    FilteredOut,

    /**
     * The backend has dropped this search's cache. The prices on screen are stale and the only
     * way forward is to run the search again — see [FlightPollResponse].
     */
    Expired,

    /**
     * The backend will not run an API search for this build at all — see
     * [FlightSearchFailure.Unavailable].
     *
     * Not an error the user can do anything about, and not one retrying resolves: the screen hands
     * straight back out to whichever flight path does work rather than showing it.
     */
    Unavailable,
}

/** Everything the results screen draws, in one object. */
internal data class FlightApiResultsUiState(
    val search: TripSearch? = null,

    /**
     * The trip as the Modify Search sheet currently has it, which is not yet what is on screen.
     *
     * Edited freely while the sheet is open — a half-changed route with the origin swapped but no
     * new destination yet is a normal intermediate state — and only becomes [search], and only
     * then re-runs the search, when Search is pressed. Dismissing the sheet throws it away.
     */
    val draft: TripSearch? = null,

    /** The itineraries on screen, in the order the backend returned them under [filters]. */
    val options: List<FlightOption> = emptyList(),

    /** How to render a price. Null until the first poll answers; the UI falls back to plain digits. */
    val currency: CurrencyInfo? = null,

    /** Total matching the current filters across every page — "284 results", not `options.size`. */
    val totalCount: Int = 0,

    val filters: FlightSearchFilters = FlightSearchFilters.Unfiltered,

    /** The filter sheets' contents, as the backend reported them for this result set. */
    val airlines: List<FlightAirline> = emptyList(),
    val agencies: List<FlightAgency> = emptyList(),
    val priceBounds: ClosedFloatingPointRange<Float>? = null,
    /** Minutes. */
    val durationBounds: IntRange? = null,

    /**
     * True before anything is on screen — the full-page loader. Distinct from [isCollecting]: once
     * a single result has arrived this goes false and the list takes over, even though the search
     * is still running.
     */
    val isSearching: Boolean = false,

    /**
     * True while the backend is still collecting. The list is live and growing; the screen says so
     * with a quiet banner rather than by blocking it.
     */
    val isCollecting: Boolean = false,

    /** True while a further page is on its way in — the spinner at the end of the list. */
    val isLoadingMore: Boolean = false,

    /** True when the backend named a page after this one. */
    val hasMore: Boolean = false,

    /** True when every page has arrived and the backend has finished — the end-of-list message. */
    val isComplete: Boolean = false,

    val failure: FlightApiFailure? = null,
) {
    /** True when the screen has results to draw, whatever else is happening around them. */
    val hasResults: Boolean get() = options.isNotEmpty()

    /** "LON → NYC", or the app's own word for the screen before a search is known. */
    val title: String
        get() {
            // Blank rather than a word: this runs in a ViewModel, which has no composition to
            // resolve one in. The header substitutes the app's own name for the screen.
            val from = search?.from?.code?.takeIf(String::isNotBlank) ?: return ""
            val to = search.to.code.takeIf(String::isNotBlank) ?: return ""
            return "$from → $to"
        }

    /**
     * "10 Dec - 20 Dec, 2 passengers", under the route in the toolbar.
     *
     * The reference app's line, in its order: dates first, then the party. A one-way shows one
     * date — the trip shape is already carried by the arrow between the two codes above it, so
     * repeating it in words here would say the same thing twice.
     */
    val subtitle: String
        get() {
            val trip = search ?: return ""
            val people = trip.passengers
            val travellers = if (people == 1) "1 passenger" else "$people passengers"
            val dates = if (trip.isRoundTrip && !trip.returnDate.isNullOrBlank()) {
                "${trip.departureDate} - ${trip.returnDate}"
            } else {
                trip.departureDate
            }
            return "$dates, $travellers"
        }

    /** The two codes either side of the toolbar's arrow. */
    val originCode: String get() = search?.from?.code.orEmpty()
    val destinationCode: String get() = search?.to?.code.orEmpty()
}

/**
 * Runs an in-app flight search and keeps the results screen fed while it fills.
 *
 * A search here is not one request. [FlightSearchRepository.openSearch] opens it and returns an
 * id; the results then arrive gradually as suppliers answer, and are collected by polling that id
 * until the backend says its cache is full. The reference app's loop, reproduced:
 *
 * - **Results are published as they arrive**, not held back until the search finishes. The first
 *   poll that returns anything replaces the loader with a live list that keeps growing.
 * - **A poll that answers with no cache field means the search has expired**, not that something
 *   went wrong — see [FlightPollResponse]. Allowed to be a transient blip [MaxExpiryRetries] times
 *   on the initial load, because it is one often enough that failing immediately is wrong; past
 *   that the screen offers to re-run the search.
 * - **An empty page is not an empty search** until [MaxEmptyPolls] of them in a row. A search
 *   polled a second after it opened has nothing in it yet and that is normal.
 * - **A filter change restarts polling from page one** against the same search id, because the
 *   pages already fetched answered a different query.
 *
 * Where it differs from the reference, deliberately: when a page of pagination arrives before the
 * cache is full, this keeps polling *that* page rather than restarting at page one. The reference
 * restarts, and the page-one response then replaces the accumulated list — losing every page the
 * user had already scrolled past. Same intent, without the lost pages.
 */
internal class FlightApiResultsViewModel(
    private val repository: FlightSearchRepository = FlightSearchRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(FlightApiResultsUiState())
    val state: StateFlow<FlightApiResultsUiState> = _state.asStateFlow()

    /** The id every poll runs against. Null until the search has been opened. */
    private var searchId: String? = null

    /** The whole search/poll loop, as one job, so a filter change or a close can cancel all of it. */
    private var searchJob: Job? = null

    /** Separate from [searchJob]: pagination runs alongside polling rather than replacing it. */
    private var pageJob: Job? = null

    /** The debounce timer behind a slider, so a drag runs one search rather than thirty. */
    private var filterJob: Job? = null

    /** The highest page fetched. Pagination asks for the next one after this. */
    private var currentPage = 1

    /** True once the filter sheets have been populated, so a later poll does not reshuffle them. */
    private var filtersPopulated = false

    /** The failure already reported for this search, so it is not reported twice — see [reportFailure]. */
    private var reportedFailure: FlightApiFailure? = null

    init {
        val search = RecentSearchStore.flights.value.firstOrNull()
        if (search == null) {
            // Reachable only if this screen is opened without a search having been run. Nothing to
            // retry, so it is reported as the emptiness it is rather than as a failure.
            _state.update { it.copy(failure = FlightApiFailure.Empty) }
            reportFailure(FlightApiFailure.Empty)
        } else {
            _state.update { it.copy(search = search) }
            start(search)
        }
    }

    /**
     * Opens the search and polls it to completion.
     *
     * Reported to the backend as an API exit the moment the search is opened, not when a deal is
     * clicked: the handoff being measured is the user arriving at results, and most searches end
     * there. See [SessionTracking.FlightExit.Api].
     */
    private fun start(search: TripSearch) {
        searchJob?.cancel()
        searchId = null
        currentPage = 1
        filtersPopulated = false
        reportedFailure = null
        _state.update {
            it.copy(
                options = emptyList(),
                totalCount = 0,
                isSearching = true,
                isCollecting = false,
                isLoadingMore = false,
                hasMore = false,
                isComplete = false,
                failure = null,
            )
        }

        searchJob = viewModelScope.launch {
            SessionTracking.flightSearch(search, SessionTracking.FlightExit.Api)
            // The GA4 half of the same handoff, on the same terms: reported when the user arrives
            // at results, because most searches end there rather than at a deal. See
            // FlightAnalytics.exitClick.
            FlightAnalytics.exitClick(
                vertical = AnalyticsEvent.FLIGHT,
                exit = AnalyticsEvent.EXIT_API,
                screenName = AnalyticsEvent.RESULT_SCREEN,
            )

            val id = openSearchWithRetries(search)
            if (id == null) return@launch
            searchId = id
            pollUntilComplete()
        }
    }

    /**
     * Opens the search, retrying a response that carries no id.
     *
     * A null `search_id` on a 2xx is the backend declining to run the search rather than failing
     * to — it answers that way while it is still provisioning one. The reference retries it
     * [MaxSearchRetries] times a second apart before giving up, and giving up means "no flights",
     * not "something broke".
     *
     * Returns null once it has settled the UI into whichever end state applies.
     */
    private suspend fun openSearchWithRetries(search: TripSearch): String? {
        repeat(MaxSearchRetries) { attempt ->
            val result = repository.openSearch(search)
            val response = result.getOrElse { error ->
                // A backend that will not search for this build says so identically every time,
                // so there is nothing to retry and nothing to show — the caller sends the search
                // somewhere that works instead.
                fail(error.asFailure())
                return null
            }
            val id = response.searchId
            if (!id.isNullOrBlank()) {
                _state.update { it.copy(currency = response.currencyInfo ?: it.currency) }
                return id
            }
            logDebug(FlightApiLogTag, "search returned no id (attempt ${attempt + 1}/$MaxSearchRetries)")
            delay(Constants.Flight.Search.PollIntervalMillis)
        }
        fail(FlightApiFailure.Empty)
        return null
    }

    /**
     * Polls page one until the backend says it has finished collecting, publishing each answer.
     *
     * The loop that makes the list grow on screen. It ends in exactly one of four ways: the cache
     * fills, the search expires, too many empty polls in a row, or the job is cancelled by a
     * filter change or the screen closing.
     */
    private suspend fun pollUntilComplete() {
        val id = searchId ?: return
        var emptyPolls = 0
        var expiryRetries = 0

        while (true) {
            val response = repository.poll(id, page = 1, filters = state.value.filters)
                .getOrElse { error ->
                    // A poll that fails after results are already on screen leaves them there: the
                    // prices shown are real and a network blip is no reason to clear them.
                    if (!state.value.hasResults) fail(error.asFailure())
                    return
                }

            if (response.hasExpired) {
                // Allowed to be a blip a couple of times on the way up, as the reference allows.
                if (expiryRetries < MaxExpiryRetries && !state.value.hasResults) {
                    expiryRetries++
                    logDebug(FlightApiLogTag, "no cache field (retry $expiryRetries/$MaxExpiryRetries)")
                    continue
                }
                fail(FlightApiFailure.Expired)
                return
            }
            expiryRetries = 0

            val results = response.results.orEmpty()
            if (results.isNotEmpty()) {
                emptyPolls = 0
                publish(response, results)
            } else {
                emptyPolls++
            }

            if (response.isComplete) {
                finish(response, results)
                return
            }

            if (results.isEmpty() && emptyPolls >= MaxEmptyPolls) {
                // Polled this long with nothing at all: the suppliers have had their chance.
                fail(if (state.value.filters.isUnfiltered) FlightApiFailure.Empty else FlightApiFailure.FilteredOut)
                return
            }

            _state.update { it.copy(isCollecting = true) }
            delay(Constants.Flight.Search.PollIntervalMillis)
        }
    }

    /**
     * A poll that arrived with results while the search is still running.
     *
     * **Leaves the list alone once the user has paged past the first page.** The loop polls page
     * one and only page one, so adopting its results after the user has scrolled into page three
     * would throw pages two and three away and jump them back to the top. The metadata is still
     * taken — the total and the filter lists are about the whole result set, not about page one —
     * but the rows the user is actually scrolling stay put.
     *
     * This is the reference app's one real defect in this flow, which restarts at page one under
     * exactly these conditions and silently drops everything already paged in.
     */
    private fun publish(response: FlightPollResponse, results: List<FlightOption>) {
        val paged = currentPage > 1
        _state.update { current ->
            current.copy(
                options = if (paged) current.options else results,
                currency = response.currency ?: current.currency,
                totalCount = response.count ?: current.totalCount,
                isSearching = false,
                isCollecting = !response.isComplete,
                // Page one naming a next page says nothing about whether the page the user has
                // scrolled to has one, so the paged answer is left as pagination found it.
                hasMore = if (paged) current.hasMore else !response.isLastPage,
                failure = null,
            ).withFilterOptions(response)
        }
    }

    /** The poll that said the backend has finished. Holds the paged list for the same reason. */
    private fun finish(response: FlightPollResponse, results: List<FlightOption>) {
        val paged = currentPage > 1
        val emptyReason = when {
            paged || results.isNotEmpty() -> null
            state.value.filters.isUnfiltered -> FlightApiFailure.Empty
            else -> FlightApiFailure.FilteredOut
        }
        _state.update { current ->
            current.copy(
                options = if (paged) current.options else results,
                currency = response.currency ?: current.currency,
                totalCount = response.count ?: current.totalCount,
                isSearching = false,
                isCollecting = false,
                hasMore = if (paged) current.hasMore else !response.isLastPage,
                // "Everything is here" needs both halves: the backend has finished collecting, and
                // there is no page after the one the user has reached.
                isComplete = if (paged) current.isComplete else response.isLastPage,
                failure = emptyReason,
            ).withFilterOptions(response)
        }
        emptyReason?.let(::reportFailure)
    }

    /**
     * The filter sheets' contents, taken from the first poll that carries them and left alone
     * afterwards.
     *
     * Once only, because the lists narrow as filters are applied — the airlines a filtered result
     * set contains are a subset of the airlines the search found — and refreshing them would make
     * the airline the user just deselected vanish from the sheet they deselected it in.
     */
    private fun FlightApiResultsUiState.withFilterOptions(
        response: FlightPollResponse,
    ): FlightApiResultsUiState {
        if (filtersPopulated) return this

        // Both lists have a second source, as the reference app's `setFilterData` does: the poll
        // sends `airlines`/`agencies` as flat lists on a complete response, but a page still
        // filling can carry only the `*_details` maps the result rows reference. Deriving from
        // those is what stops the filter sheets opening empty on a fast first tap.
        val airlines = response.airlines
            ?.filter { !it.iata.isNullOrBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: response.airlineDetails.orEmpty().map { (code, detail) ->
                FlightAirline(name = detail.displayName, iata = code, logoUrl = detail.logoUrl)
            }

        val agencies = response.agencies
            ?.filter { !it.code.isNullOrBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: response.providerDetails.orEmpty().map { (code, detail) ->
                FlightAgency(code = code, name = detail.displayName, imageUrl = detail.logoUrls?.imageUrl)
            }

        if (airlines.isEmpty() && agencies.isEmpty()) return this
        filtersPopulated = true

        // Price has its own fallback for the same reason — the summary bounds arrive later than
        // the first rows do, and the rows already carry prices to take a range from.
        val min = response.minPrice ?: response.results?.mapNotNull { it.minPrice }?.minOrNull()
        val max = response.maxPrice ?: response.results?.mapNotNull { it.maxPrice }?.maxOrNull()

        return copy(
            airlines = airlines,
            agencies = agencies,
            // Only where the two bounds actually describe a range; a single-price result set has
            // nothing for a slider to do.
            priceBounds = if (min != null && max != null && max > min) {
                min.toFloat()..max.toFloat()
            } else {
                priceBounds
            },
            durationBounds = response.minDuration
                ?.let { lo -> response.maxDuration?.takeIf { it > lo }?.let { hi -> lo..hi } }
                ?: durationBounds,
        )
    }

    /**
     * Fetches the next page.
     *
     * Safe to call on every scroll — it refuses while a page is already in flight, while the
     * first results are still being waited on, and once the backend has said there is no next
     * page. The list calls it on approach to the end rather than at it, so the page is usually
     * there by the time the user arrives.
     */
    fun loadMore() {
        val id = searchId ?: return
        val current = state.value
        if (!current.hasMore || current.isLoadingMore || current.isSearching || current.failure != null) return

        pageJob?.cancel()
        _state.update { it.copy(isLoadingMore = true) }
        pageJob = viewModelScope.launch {
            val next = currentPage + 1
            val response = repository.poll(id, page = next, filters = state.value.filters)
                .getOrElse {
                    // The pages already on screen stay. A failed page is a spinner that stops, not
                    // a results list that empties.
                    _state.update { it.copy(isLoadingMore = false) }
                    return@launch
                }

            if (response.hasExpired) {
                fail(FlightApiFailure.Expired)
                return@launch
            }

            currentPage = next
            val added = response.results.orEmpty()
            _state.update { state ->
                state.copy(
                    options = state.options + added,
                    totalCount = response.count ?: state.totalCount,
                    isLoadingMore = false,
                    hasMore = !response.isLastPage,
                    // Only once the backend has both run out of pages and finished collecting.
                    isComplete = response.isLastPage && response.isComplete,
                    isCollecting = state.isCollecting && !response.isComplete,
                )
            }
        }
    }

    /**
     * Applies a new set of filters, or a new sort order.
     *
     * Restarts polling from page one against the same search id — the pages in hand answered the
     * previous query — but does not re-open the search: the backend's cache is keyed on the search
     * rather than on the filters, so it is still warm and the first poll usually answers at once.
     */
    fun applyFilters(filters: FlightSearchFilters, debounceMillis: Long = 0) {
        if (filters == state.value.filters) return
        // Shown immediately even though the request is debounced: the chip the user just tapped
        // has to light up on the tap, not 600ms later.
        _state.update { it.copy(filters = filters) }

        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            // A slider drags through dozens of values on its way to one. Debouncing collapses
            // those into a single search; a checkbox passes 0 and runs at once. The reference
            // app debounces the same controls by the same 600ms.
            if (debounceMillis > 0) delay(debounceMillis)
            restartPolling(filters)
        }
    }

    /** How long a slider's changes are collected before one search is run. The reference's value. */
    fun applyFiltersDebounced(filters: FlightSearchFilters) =
        applyFilters(filters, debounceMillis = SliderDebounceMillis)

    /**
     * Throws away the pages in hand and polls page one again under [filters].
     *
     * Not a new search: the backend's cache is keyed on the search id rather than on the filters,
     * so it is still warm and the first poll usually answers at once.
     */
    private fun restartPolling(filters: FlightSearchFilters) {
        searchJob?.cancel()
        pageJob?.cancel()
        currentPage = 1
        currentPage = 1
        _state.update {
            it.copy(
                filters = filters,
                options = emptyList(),
                totalCount = 0,
                isSearching = true,
                isCollecting = false,
                isLoadingMore = false,
                hasMore = false,
                isComplete = false,
                failure = null,
            )
        }

        val id = searchId
        if (id == null) {
            // Filters changed before the search ever opened — nothing to re-poll, so run it again.
            state.value.search?.let(::start)
            return
        }
        searchJob = viewModelScope.launch { pollUntilComplete() }
    }

    fun updateSort(sort: FlightSort) = applyFilters(state.value.filters.copy(sort = sort))

    /** The × on an active chip: resets that one group and leaves the other five alone. */
    fun clearFilter(type: FlightFilterType) = applyFilters(state.value.filters.cleared(type))

    fun clearFilters() = applyFilters(FlightSearchFilters(sort = state.value.filters.sort))

    // --- Modify Search -------------------------------------------------------------------------

    /**
     * Opens the Modify Search sheet on a copy of the search currently on screen.
     *
     * A copy rather than the live search, so backing out of the sheet leaves the results exactly
     * as they were — the user has changed their mind, not their trip.
     */
    fun beginEditingTrip() {
        _state.update { it.copy(draft = it.draft ?: it.search) }
    }

    fun cancelEditingTrip() {
        _state.update { it.copy(draft = null) }
    }

    /** Each field of the sheet, editing the draft in place. */
    fun draftOrigin(airport: Airport) = editDraft { it.copy(from = airport) }

    fun draftDestination(airport: Airport) = editDraft { it.copy(to = airport) }

    fun draftDates(departure: String, returning: String?) = editDraft {
        it.copy(departureDate = departure, returnDate = returning)
    }

    fun draftParty(adults: Int, children: Int, infants: Int, cabinClass: CabinClass) = editDraft {
        it.copy(adults = adults, children = children, infants = infants, cabinClass = cabinClass)
    }

    /**
     * Turns a round trip into a one-way and back.
     *
     * Going one-way keeps the return date rather than clearing it: the search ignores it while
     * [TripSearch.isRoundTrip] is false, and someone who flips the toggle to compare prices and
     * flips it back should find their date still there.
     */
    fun draftTripType(isRoundTrip: Boolean) = editDraft { it.copy(isRoundTrip = isRoundTrip) }

    /** Swaps the two ends. The one edit the sheet applies without opening anything. */
    fun swapDraftLocations() = editDraft { it.copy(from = it.to, to = it.from) }

    private fun editDraft(transform: (TripSearch) -> TripSearch) {
        _state.update { current ->
            val base = current.draft ?: current.search ?: return@update current
            current.copy(draft = transform(base))
        }
    }

    /**
     * Applies the edited trip and runs it as a new search.
     *
     * A genuinely new search, not a re-poll: the search id the backend issued is bound to the
     * route and dates it was opened with, so polling it after changing either would return the
     * old trip's flights under the new trip's heading.
     *
     * Recorded to [RecentSearchStore] on the way, so the search card reflects what the user
     * actually ended up searching for and a later return to this screen reads the same trip back.
     */
    fun applyEditedTrip() {
        val edited = state.value.draft ?: return
        if (!edited.from.isChosen || !edited.to.isChosen) return

        _state.update {
            it.copy(
                search = edited,
                draft = null,
                // Filters belonged to the previous result set — its airlines, its price range —
                // so they are dropped rather than reapplied to a trip that may share none of it.
                // The reference clears them on every new search too.
                filters = FlightSearchFilters.Unfiltered,
            )
        }
        filtersPopulated = false
        RecentSearchStore.recordFlight(edited)
        start(edited)
    }

    /**
     * Runs the whole search again from scratch.
     *
     * What the expired-prices and network-failure states offer. Goes back through `search/` rather
     * than polling: an expired search id has no cache behind it any more, and a poll against one
     * only expires again.
     */
    fun retry() {
        val search = state.value.search ?: return
        start(search)
    }

    /**
     * Stops polling while the screen is not being looked at.
     *
     * A backgrounded app polling once a second is a battery and data cost against a screen nobody
     * can see. Pagination is cancelled with it; the pages already fetched stay on screen.
     */
    fun onPaused() {
        searchJob?.cancel()
        pageJob?.cancel()
        _state.update { it.copy(isCollecting = false, isLoadingMore = false) }
    }

    /**
     * Picks polling back up when the screen is looked at again.
     *
     * Only where there is something to pick up: a search that had already finished, failed or
     * expired is left where it was, because resuming it would either repeat work that is done or
     * re-fail in the same way.
     */
    fun onResumed() {
        // First, because this also fires on the screen's first composition — when the search has
        // just been started from `init` and has no id yet. Any later check would read that as
        // "never opened" and start a second search over the top of the running one, duplicating
        // the work and reporting the exit twice.
        if (searchJob?.isActive == true) return

        val current = state.value
        if (current.isComplete || current.failure != null) return
        val id = searchId
        if (id == null) {
            // Backgrounded before the search ever opened — nothing to resume, so start again.
            current.search?.let(::start)
            return
        }
        searchJob = viewModelScope.launch { pollUntilComplete() }
    }

    /**
     * A deal the user is leaving for.
     *
     * Reported with the booking site's name attached, which is the one thing the exit records that
     * the search exit at the top of this file could not know — see [SessionTracking.FlightExit.Api].
     */
    fun onDealSelected(deal: FlightDeal, index: Int? = null) {
        val search = state.value.search ?: return
        // Outside the coroutine: this is the tap, and the measurement of a tap should not wait on
        // a scope that the screen being left is about to cancel.
        FlightAnalytics.providerButton(
            providerName = deal.name,
            price = deal.price,
            // Where in the list the chosen deal sat, which is what says whether people actually
            // take the cheapest option they are shown. Null where the caller has no list — the
            // single-offer path, which is not a choice between anything.
            index = index,
        )
        viewModelScope.launch {
            SessionTracking.flightSearch(search, SessionTracking.FlightExit.Api, tag = deal.name)
        }
    }

    /**
     * Releases the search on the way out, then calls [onDone] however that went.
     *
     * [onDone] runs immediately rather than waiting: this is a courtesy to the backend, and a user
     * pressing back is not waiting on it.
     *
     * Sent on a process-lifetime scope rather than [viewModelScope], which is the whole point —
     * [onDone] pops this screen off the back stack, that clears the ViewModel, and clearing it
     * cancels [viewModelScope] before the request has left. The same arrangement
     * [SessionTracking] uses, and for the same reason: a call made at the moment the user leaves
     * cannot be owned by the screen they are leaving.
     */
    fun release(onDone: () -> Unit) {
        val id = searchId
        searchJob?.cancel()
        pageJob?.cancel()
        if (!id.isNullOrBlank()) {
            // Captured into a local first: `repository` is a ViewModel field, and reading it
            // inside a coroutine that outlives the ViewModel is what this is avoiding.
            val repo = repository
            ReleaseScope.launch { repo.releaseSearch(id) }
        }
        onDone()
    }

    /**
     * A repository failure as the screen's own vocabulary.
     *
     * Anything the repository did not classify as unavailable is treated as a network problem,
     * which is the retryable half and the safe default for an error this layer does not recognise.
     */
    private fun Throwable.asFailure(): FlightApiFailure =
        if (this is FlightSearchException && failure == FlightSearchFailure.Unavailable) {
            FlightApiFailure.Unavailable
        } else {
            FlightApiFailure.Network
        }

    /** One place to land in a failed state, so every exit clears the same loading flags. */
    private fun fail(failure: FlightApiFailure) {
        _state.update {
            it.copy(
                isSearching = false,
                isCollecting = false,
                isLoadingMore = false,
                failure = failure,
            )
        }
        reportFailure(failure)
    }

    /**
     * Reports a search that produced nothing, once per search.
     *
     * Guarded on [reportedFailure] because the states this is called from are not all one-shot:
     * `finish` runs on the poll that completes the search and can be reached again by pagination,
     * and a user toggling a filter back and forth would otherwise send `search_failed` on every
     * toggle. Cleared by [start], so re-running the search can report again.
     */
    private fun reportFailure(failure: FlightApiFailure) {
        if (reportedFailure == failure) return
        reportedFailure = failure
        FlightAnalytics.searchFailed(
            vertical = AnalyticsEvent.FLIGHT,
            reason = failure.analyticsReason(),
            screenName = AnalyticsEvent.RESULT_SCREEN,
        )
    }

    private companion object {
        /**
         * Where the release call is made, outliving any one results screen — see [release].
         *
         * A supervisor job so one failed release cannot take the scope down and silently stop
         * every later one.
         */
        val ReleaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** How long a slider's changes are collected before one search runs. The reference's 600ms. */
        const val SliderDebounceMillis = 600L

        /** How many times a search answering without an id is asked again. The reference's count. */
        const val MaxSearchRetries = 5

        /**
         * How many consecutive polls may come back with nothing before the search is called empty.
         *
         * 15, a second apart — the reference's number. Long enough that a slow supplier still
         * makes it into the results, short enough that a genuinely empty route does not sit under
         * a spinner.
         */
        const val MaxEmptyPolls = 15

        /** How many missing-cache polls are treated as a blip before the search is called expired. */
        const val MaxExpiryRetries = 2
    }
}

/**
 * This screen's failure as the word the dashboard groups on.
 *
 * Kept beside the enum rather than in [AnalyticsEvent] because three of the five are this screen's
 * own vocabulary and mean nothing to the other two verticals; only the shared two are named there.
 */
private fun FlightApiFailure.analyticsReason(): String = when (this) {
    FlightApiFailure.Network -> AnalyticsEvent.REASON_NETWORK
    FlightApiFailure.Empty -> AnalyticsEvent.REASON_EMPTY
    FlightApiFailure.Unavailable -> AnalyticsEvent.REASON_UNAVAILABLE
    FlightApiFailure.FilteredOut -> "filtered_out"
    FlightApiFailure.Expired -> "expired"
}
