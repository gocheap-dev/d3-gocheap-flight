package com.gocheap.travel.ui.home.results.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.travel.data.model.TripSearch
import com.gocheap.travel.data.model.flight.api.FlightDeal
import com.gocheap.travel.data.model.flight.api.FlightOption
import com.gocheap.travel.ui.component.EmptyState
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.InnerPage
import com.gocheap.travel.ui.component.PrimaryButton
import com.gocheap.travel.ui.component.SearchLoader
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import com.gocheap.travel.ui.theme.FlightTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.check_your_connection_and_try_again
import flighttp.shared.generated.resources.clear_filters
import flighttp.shared.generated.resources.comparing_fares_across_booking_sites
import flighttp.shared.generated.resources.couldn_t_reach_the_search
import flighttp.shared.generated.resources.fares_change_quickly_run_the_search_again
import flighttp.shared.generated.resources.no_flights_found
import flighttp.shared.generated.resources.no_flights_match_your_filters
import flighttp.shared.generated.resources.nothing_is_flying_this_route_on_these
import flighttp.shared.generated.resources.opening_flight_results
import flighttp.shared.generated.resources.search_again
import flighttp.shared.generated.resources.searching_flights
import flighttp.shared.generated.resources.taking_you_to_the_full_list_of
import flighttp.shared.generated.resources.that_s_every_fare_we_found
import flighttp.shared.generated.resources.these_prices_have_expired
import flighttp.shared.generated.resources.try_again
import flighttp.shared.generated.resources.try_widening_them_to_see_more_fares

/**
 * Flight results drawn in-app, for the `KYK_API` third of the controller's choice.
 *
 * The odd one out among this app's three results screens: stays and rentals load the supplier's
 * own page in a web view, and so does the deeplink half of flights. This one fetches the
 * itineraries itself and draws them in the app's own cards, which is the whole point of the mode
 * — the search stays inside the app until the user picks a fare to book.
 *
 * Reachable only when `new_tab` contains "API". On `KYK_DL` the search opens
 * [com.gocheap.travel.ui.home.results.FlightResultsScreen] instead, and on `TPT_SDK` it never
 * reaches this app's navigation at all.
 */
@Composable
internal fun FlightApiResultsScreen(
    onBack: () -> Unit,
    onOpenDeal: (FlightDeal) -> Unit,
    /**
     * Called when the backend refuses to run an API search for this build at all.
     *
     * The screen cannot recover from that and must not sit on a dead error page, so it hands the
     * search back to the navigator to be reopened on whichever flight path does work. See
     * [FlightApiFailure.Unavailable].
     */
    onUnavailable: () -> Unit,
    /**
     * The three pickers the Modify Search sheet opens.
     *
     * Passed in rather than opened here because they are the *same* pages the home search card
     * pushes, and only the navigator knows how to push them and read their answers back. The
     * sheet closes as each one opens and reopens when it returns — see [ModifySearchSheet].
     */
    onPickOrigin: (TripSearch) -> Unit,
    onPickDestination: (TripSearch) -> Unit,
    onPickDates: (TripSearch) -> Unit,
    onPickTravellers: (TripSearch) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlightApiResultsViewModel = viewModel { FlightApiResultsViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openSheet by remember { mutableStateOf<FlightFilterSheet?>(null) }
    var expandedOption by remember { mutableStateOf<FlightOption?>(null) }

    /**
     * What tapping an itinerary does.
     *
     * **A single offer skips the sheet entirely** and opens that seller's checkout. A sheet whose
     * whole job is to let the user choose between sellers has nothing to offer when there is only
     * one — it would be a list of one, and a tap spent to get past it.
     *
     * Two or more open the sheet, and so does none: the empty case still has something to say
     * ("no booking sites are offering this flight"), and saying it in the sheet the tap already
     * implies is better than a tap that appears to do nothing at all.
     */
    val onOptionTapped: (FlightOption) -> Unit = { option ->
        val offers = option.bookableOffers()
        val only = offers.singleOrNull()
        if (only != null) {
            // No index: one offer is not a choice between anything, and sending 0 would put it in
            // the same bucket as "the cheapest of several", which is the thing being measured.
            viewModel.onDealSelected(only.deal)
            onOpenDeal(only.deal)
        } else {
            expandedOption = option
        }
    }

    // A search polls once a second while it is collecting, which is a cost worth paying only while
    // someone is looking at it. Stopped when the screen is not resumed — backgrounded, or covered
    // by the checkout page — and picked back up on return, where there is anything left to pick up.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { viewModel.onPaused() }
    }

    // Handed straight back out rather than drawn: this failure means the search can never run
    // here, so a moment of loader followed by a redirect is the whole of what the user sees.
    LaunchedEffect(state.failure) {
        if (state.failure == FlightApiFailure.Unavailable) onUnavailable()
    }

    // Back releases the search before leaving, so the backend can stop collecting for a screen
    // nobody is looking at — see FlightApiResultsViewModel.release.
    val leave = { viewModel.release(onBack) }

    Column(modifier = modifier.fillMaxSize().background(FlightColors.Background)) {
        FlightResultsHeader(
            originCode = state.originCode,
            destinationCode = state.destinationCode,
            subtitle = state.subtitle,
            isRoundTrip = state.search?.isRoundTrip == true,
            filters = state.filters,
            // The chip bar is hidden only on the very first load, when there is nothing yet to
            // filter. It stays up through an empty or failed result set, which is exactly when
            // the user most needs the filters still in reach.
            showFilters = !state.isSearching || state.hasResults,
            onBack = leave,
            onEditSearch = viewModel::beginEditingTrip,
            onOpenSheet = { openSheet = it },
            onClearFilter = viewModel::clearFilter,
        )

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when {
                state.isSearching -> SearchLoader(
                    label = stringResource(Res.string.searching_flights),
                    caption = stringResource(Res.string.comparing_fares_across_booking_sites),
                    icon = FlightIcons.FlightOutlined,
                    // Indeterminate on purpose: a search's progress is not knowable — it finishes
                    // when the slowest supplier answers — so the bar creeps rather than lies.
                    progress = 0f,
                    modifier = Modifier.fillMaxSize(),
                )

                state.failure != null && !state.hasResults -> FlightResultsFailure(
                    failure = state.failure!!,
                    onRetry = viewModel::retry,
                    onClearFilters = viewModel::clearFilters,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> Column(Modifier.fillMaxSize()) {
                    FlightResultsSummary(
                        totalCount = state.totalCount,
                        isCollecting = state.isCollecting,
                        activeFilterCount = state.filters.activeCount,
                    )
                    FlightResultsList(
                        state = state,
                        onLoadMore = viewModel::loadMore,
                        onSelect = onOptionTapped,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    openSheet?.let { sheet ->
        FlightFilterSheetHost(
            sheet = sheet,
            filters = state.filters,
            airlines = state.airlines,
            agencies = state.agencies,
            priceBounds = state.priceBounds,
            durationBounds = state.durationBounds,
            // How many time-window pairs the Times sheet draws. Off the search rather than off a
            // result, which may not have arrived when the sheet is opened.
            legCount = if (state.search?.isRoundTrip == true) 2 else 1,
            onApply = { viewModel.applyFilters(it) },
            onApplyDebounced = viewModel::applyFiltersDebounced,
            onDismiss = { openSheet = null },
        )
    }

    // Modify Search. Driven off the draft rather than a local flag, so the sheet reopens by
    // itself when a picker returns — the draft survives the trip out to the picker and back,
    // which a `remember` in this composable would not.
    state.draft?.let { draft ->
        ModifySearchSheet(
            draft = draft,
            onTripTypeChange = viewModel::draftTripType,
            onSwap = viewModel::swapDraftLocations,
            onPickOrigin = { onPickOrigin(draft) },
            onPickDestination = { onPickDestination(draft) },
            onPickDates = { onPickDates(draft) },
            onPickTravellers = { onPickTravellers(draft) },
            onSearch = viewModel::applyEditedTrip,
            onDismiss = viewModel::cancelEditingTrip,
        )
    }

    expandedOption?.let { option ->
        FlightDealsSheet(
            option = option,
            currency = state.currency,
            onSelect = { deal ->
                // Where in the sheet the chosen deal sat. Worked out here rather than threaded
                // through the sheet's callback, which has no reason to know about measurement —
                // the list it draws is this same one, in this same order.
                val index = option.bookableOffers().indexOfFirst { it.deal == deal }
                viewModel.onDealSelected(deal, index.takeIf { it >= 0 })
                expandedOption = null
                onOpenDeal(deal)
            },
            onDismiss = { expandedOption = null },
        )
    }
}

/**
 * The list proper.
 *
 * Pagination is driven off how close the last visible row is to the end rather than off reaching
 * it: asking [PaginationThreshold] rows early usually has the next page in hand by the time the
 * user scrolls onto it, so the spinner at the bottom is rarely seen at all.
 */
@Composable
private fun FlightResultsList(
    state: FlightApiResultsUiState,
    onLoadMore: () -> Unit,
    onSelect: (FlightOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(state.options.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= state.options.size - PaginationThreshold
        }
    }
    LaunchedEffect(listState, state.options.size) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { if (it) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = FlightSpacing.ScreenHorizontal,
            end = FlightSpacing.ScreenHorizontal,
            top = 12.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(FlightSpacing.ItemGap),
    ) {
        items(
            items = state.options,
            // Index folded into the key: the backend repeats an id across pages often enough that
            // keying on it alone drops rows, and a result set with duplicate keys crashes outright.
            key = { option -> option.id ?: option.hashCode().toString() },
        ) { option ->
            FlightResultCard(
                option = option,
                currency = state.currency,
                onClick = { onSelect(option) },
            )
        }

        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = FlightColors.Primary,
                        strokeWidth = 2.5.dp,
                    )
                }
            }
        }

        if (state.isComplete && state.hasResults) {
            item(key = "end-of-results") {
                Text(
                    text = stringResource(Res.string.that_s_every_fare_we_found),
                    style = MaterialTheme.typography.labelSmall,
                    color = FlightColors.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                )
            }
        }
    }
}

/**
 * What the screen shows instead of a list.
 *
 * Four states and four different offers, because the way out of each is different: a network
 * failure retries, an expired search re-runs, a filtered-out result set clears its filters, and a
 * genuinely empty one has nothing to offer but the way back.
 */
@Composable
private fun FlightResultsFailure(
    failure: FlightApiFailure,
    onRetry: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = FlightSpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (failure) {
            FlightApiFailure.Network -> {
                EmptyState(
                    icon = FlightIcons.Globe,
                    title = stringResource(Res.string.couldn_t_reach_the_search),
                    message = stringResource(Res.string.check_your_connection_and_try_again),
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(text = stringResource(Res.string.try_again), onClick = onRetry)
            }

            FlightApiFailure.Expired -> {
                EmptyState(
                    icon = FlightIcons.Clock,
                    title = stringResource(Res.string.these_prices_have_expired),
                    // Honest about why: fares move, and a user who re-runs and sees a different
                    // number should have been told to expect one.
                    message = stringResource(Res.string.fares_change_quickly_run_the_search_again),
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(text = stringResource(Res.string.search_again), onClick = onRetry)
            }

            FlightApiFailure.FilteredOut -> {
                EmptyState(
                    icon = FlightIcons.Filter,
                    title = stringResource(Res.string.no_flights_match_your_filters),
                    message = stringResource(Res.string.try_widening_them_to_see_more_fares),
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(text = stringResource(Res.string.clear_filters), onClick = onClearFilters)
            }

            FlightApiFailure.Empty -> EmptyState(
                icon = FlightIcons.FlightOutlined,
                title = stringResource(Res.string.no_flights_found),
                message = stringResource(Res.string.nothing_is_flying_this_route_on_these),
            )

            // Never actually drawn — the effect above leaves this screen the moment the state is
            // set. Present so the redirect failing to fire shows something rather than a blank
            // page, and so this `when` stays exhaustive if a branch is ever added.
            FlightApiFailure.Unavailable -> EmptyState(
                icon = FlightIcons.FlightOutlined,
                title = stringResource(Res.string.opening_flight_results),
                message = stringResource(Res.string.taking_you_to_the_full_list_of),
            )
        }
    }
}

/** How many rows from the end the next page is asked for. */
private const val PaginationThreshold = 3

@Preview
@Composable
private fun FlightApiResultsLoadingPreview() {
    FlightTheme {
        SearchLoader(
            label = "Searching flights",
            caption = "Comparing fares across booking sites",
            icon = FlightIcons.FlightOutlined,
            progress = 0.3f,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
