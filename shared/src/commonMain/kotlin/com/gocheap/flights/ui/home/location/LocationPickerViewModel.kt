package com.gocheap.flights.ui.home.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gocheap.flights.data.Repository
import com.gocheap.flights.data.model.flight.FlightDestination
import com.gocheap.flights.data.model.flight.FlightDestinationType
import com.gocheap.flights.data.recent.RecentLocationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class LocationPickerUiState(
    val query: String = "",
    val recents: List<FlightDestination> = emptyList(),
    val results: List<FlightDestination> = emptyList(),
    val isLoading: Boolean = false,
    /**
     * Whether the current query has come back yet. Without it the empty state would flash between
     * the keystroke and the result, which reads as "nothing found" for a query still in flight.
     */
    val hasSearched: Boolean = false,
    /** The last call failed — a different thing from it returning nothing, and worth saying so. */
    val hasFailed: Boolean = false,
) {
    val isSearching: Boolean get() = query.trim().isNotEmpty()

    /** Recents are the idle state only; a query replaces them rather than stacking underneath. */
    val showRecents: Boolean get() = !isSearching && recents.isNotEmpty()

    val showResults: Boolean get() = isSearching && results.isNotEmpty()

    val showError: Boolean get() = isSearching && hasSearched && !isLoading && hasFailed

    val showNoMatches: Boolean
        get() = isSearching && hasSearched && !isLoading && !hasFailed && results.isEmpty()

    /** Idle with nothing picked before — there is genuinely nothing to list yet. */
    val showPrompt: Boolean get() = !isSearching && recents.isEmpty()
}

/**
 * Backs the flight origin and destination page, over the supplier's autocomplete endpoint — the
 * same call, parameters and debounce the reference app's flight picker makes.
 *
 * Typing is debounced and the in-flight request cancelled on every keystroke, so a fast typist sends
 * one call rather than one per letter, and a slow earlier response can never overwrite a newer one.
 *
 * [kind] narrows what the endpoint answers with rather than choosing a different one: it returns
 * cities and the airports inside them interleaved, and a city-only field has no use for the second.
 * [excludeId] drops the trip's other endpoint, so it cannot start and end in one place.
 */
class LocationPickerViewModel(
    private val kind: LocationKind,
    private val excludeId: String?,
    private val repository: Repository = Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(LocationPickerUiState())
    val state: StateFlow<LocationPickerUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            RecentLocationStore.flightDestinations.collect { recents ->
                _state.update { it.copy(recents = recents.visible()) }
            }
        }
    }

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(query = query, hasSearched = false, hasFailed = false) }

        val trimmed = query.trim()
        if (trimmed.length < MinQueryLength) {
            // Too short to send: drop whatever the last query matched rather than leaving stale rows
            // on screen under a query that no longer produced them.
            _state.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }

        _state.update { it.copy(isLoading = true) }
        searchJob = viewModelScope.launch {
            // The debounce is the cancellation point that matters: a keystroke inside this window
            // kills the job before a request is ever opened.
            delay(DebounceMillis.milliseconds)
            repository.flightSearchDestinations(trimmed)
                .onSuccess { destinations ->
                    _state.update {
                        it.copy(
                            results = destinations.visible(),
                            isLoading = false,
                            hasSearched = true,
                            hasFailed = false,
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            results = emptyList(),
                            isLoading = false,
                            hasSearched = true,
                            hasFailed = true,
                        )
                    }
                }
        }
    }

    /** Re-runs the current query, for the button the error state offers. */
    fun onRetry() {
        onQueryChange(state.value.query)
    }

    fun onClearQuery() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                results = emptyList(),
                isLoading = false,
                hasSearched = false,
                hasFailed = false,
            )
        }
    }

    /**
     * Records the pick as the page hands its answer back, so the next visit lists this place first —
     * and, since the store writes through to disk, so does the next launch.
     *
     * Off the caller's thread: the tap that lands here also pops the page, and a disk write has no
     * business on that frame.
     */
    fun onLocationSelected(destination: FlightDestination) {
        viewModelScope.launch(Dispatchers.Default) {
            RecentLocationStore.recordFlightDestination(destination)
        }
    }

    /** Same treatment as [onLocationSelected]: clearing rewrites the stored list. */
    fun onClearRecents() {
        viewModelScope.launch(Dispatchers.Default) {
            RecentLocationStore.clearFlightDestinations()
        }
    }

    /**
     * What every list on this page goes through — results and recents alike.
     *
     * Deduped on the code as well as filtered: the rows are keyed on it in the list, so two rows
     * sharing one would be a crash rather than a cosmetic repeat, and the supplier is not this
     * app's invariant to rely on.
     */
    private fun List<FlightDestination>.visible(): List<FlightDestination> = this
        .filter { destination ->
            destination.iata != excludeId &&
                (kind != LocationKind.City || destination.type == FlightDestinationType.City)
        }
        .distinctBy { it.iata }

    private companion object {
        /** Both lifted from the reference app's flight picker, tuned against this same endpoint. */
        const val DebounceMillis = 300L
        const val MinQueryLength = 3
    }
}
