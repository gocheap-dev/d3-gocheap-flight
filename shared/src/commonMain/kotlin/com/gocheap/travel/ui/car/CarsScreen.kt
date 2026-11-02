package com.gocheap.travel.ui.car

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.travel.data.model.CarSearch
import com.gocheap.travel.data.model.LocationSuggestion
import com.gocheap.travel.ui.calendar.CalendarMode
import com.gocheap.travel.ui.calendar.CalendarRequest
import com.gocheap.travel.ui.calendar.DateSelection
import com.gocheap.travel.ui.car.location.CarLocationSelection
import com.gocheap.travel.ui.home.location.LocationTargets
import com.gocheap.travel.ui.component.BottomBarSpacer
import com.gocheap.travel.ui.component.CompactDateField
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.glassCard
import com.gocheap.travel.ui.component.IconValueField
import com.gocheap.travel.ui.component.PopularLocationGrid
import com.gocheap.travel.ui.component.PrimaryButton
import com.gocheap.travel.ui.component.RecentSearchItem
import com.gocheap.travel.ui.component.RecentSearchSubtitle
import com.gocheap.travel.ui.component.RecentSearchRow
import com.gocheap.travel.ui.component.SectionHeader
import com.gocheap.travel.ui.component.SearchFieldMotion
import com.gocheap.travel.ui.component.SegmentedTabs
import com.gocheap.travel.ui.component.SkyHeader
import com.gocheap.travel.ui.component.SplitFieldRow
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import com.gocheap.travel.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import com.gocheap.travel.ui.component.storedDateLabel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.drop_off
import flighttp.shared.generated.resources.drop_off_location
import flighttp.shared.generated.resources.pick_up
import flighttp.shared.generated.resources.pick_up_at_the_airport_or_in
import flighttp.shared.generated.resources.pick_up_location
import flighttp.shared.generated.resources.popular_locations
import flighttp.shared.generated.resources.recent_searches
import flighttp.shared.generated.resources.rent_a_car_nanywhere
import flighttp.shared.generated.resources.rental_dates
import flighttp.shared.generated.resources.search_cars
import flighttp.shared.generated.resources.where_from
import flighttp.shared.generated.resources.where_to
import flighttp.shared.generated.resources.different_drop_off
import flighttp.shared.generated.resources.same_drop_off

@Composable
fun CarsScreen(
    viewModel: CarsViewModel = viewModel { CarsViewModel() },
    onSearch: (CarSearch) -> Unit = {},
    onOpenCalendar: (CalendarRequest) -> Unit = {},
    pickedDates: DateSelection? = null,
    onPickedDatesHandled: () -> Unit = {},
    onOpenLocationPicker: (target: String) -> Unit = {},
    pickedLocation: CarLocationSelection? = null,
    onPickedLocationHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pickedDates) {
        val picked = pickedDates ?: return@LaunchedEffect
        viewModel.onDatesPicked(picked.start, picked.end, picked.startTime, picked.endTime)
        onPickedDatesHandled()
    }

    // Resolved here rather than inside the lambda below: `stringResource` is only callable
    // from a composition, and that lambda is an ordinary callback.
    val rentalDatesTitle = stringResource(Res.string.rental_dates)

    LaunchedEffect(pickedLocation) {
        val picked = pickedLocation ?: return@LaunchedEffect
        viewModel.onLocationPicked(picked.target, picked.location)
        onPickedLocationHandled()
    }

    LaunchedEffect(state.snackbarMessageId) {
        val message = state.snackbarMessage?.let { getString(it) } ?: return@LaunchedEffect
        viewModel.onSnackbarMessageShown()
        snackbarHostState.showSnackbar(message)
    }

    CarsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        // The picker fills the field rather than reading from it, so it opens whether or not the
        // card has loaded its defaults yet.
        onPickCity = { onOpenLocationPicker(LocationTargets.Pickup) },
        onPickDropoffCity = { onOpenLocationPicker(LocationTargets.Dropoff) },
        onSameDropoffChange = viewModel::onSameDropoffChange,
        onPickDates = {
            val search = state.search
            if (search != null) {
                onOpenCalendar(
                    CalendarRequest(
                        title = rentalDatesTitle,
                        mode = CalendarMode.Range,
                        start = search.pickupDate,
                        end = search.dropoffDate,
                        // Only the rental cards book against a clock.
                        withTime = true,
                        startTime = search.pickupTime,
                        endTime = search.dropoffTime,
                        spanUnit = "days",
                    ),
                )
            }
        },
        onSearch = { viewModel.onSearch(onSearch) },
        onRecentSearch = { id -> viewModel.onRecentSearchClick(id, onSearch) },
        onClearRecentSearch = viewModel::onRecentSearchClear,
        onPopularLocation = { id -> viewModel.onPopularLocationClick(id, onSearch) },
        modifier = modifier,
    )
}

@Composable
private fun CarsContent(
    state: CarsUiState,
    snackbarHostState: SnackbarHostState,
    onPickCity: () -> Unit,
    onPickDropoffCity: () -> Unit,
    onSameDropoffChange: (Boolean) -> Unit,
    onPickDates: () -> Unit,
    onSearch: () -> Unit,
    onRecentSearch: (String) -> Unit,
    onClearRecentSearch: (String) -> Unit,
    onPopularLocation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(FlightColors.Background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box {
                SkyHeader(height = 262.dp, showPlane = false)

                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Column(modifier = Modifier.padding(start = FlightSpacing.ScreenHorizontal, end = FlightSpacing.ScreenHorizontal, top = 20.dp)) {
                        Text(
                            text = stringResource(Res.string.rent_a_car_nanywhere),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(Res.string.pick_up_at_the_airport_or_in),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.94f),
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                    CarSearchCard(
                        search = state.search,
                        onPickCity = onPickCity,
                        onPickDropoffCity = onPickDropoffCity,
                        onSameDropoffChange = onSameDropoffChange,
                        onPickDates = onPickDates,
                        onSearch = onSearch,
                        modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Nothing searched yet means no section at all — header included. Each block carries
            // its own trailing gap so the spacing stays right whichever ones are present.
            if (state.recentSearches.isNotEmpty()) {
                SectionHeader(
                    title = stringResource(Res.string.recent_searches),
                    modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal),
                )

                Spacer(Modifier.height(10.dp))

                RecentSearchRow(
                    items = state.recentSearches,
                    onClick = { onRecentSearch(it.id) },
                    onClear = { onClearRecentSearch(it.id) },
                )

                Spacer(Modifier.height(18.dp))
            }

            if (state.popularLocations.isNotEmpty()) {
                SectionHeader(
                    title = stringResource(Res.string.popular_locations),
                    modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal),
                )

                Spacer(Modifier.height(10.dp))

                PopularLocationGrid(
                    items = state.popularLocations,
                    onClick = { onPopularLocation(it.id) },
                )
            }

            BottomBarSpacer()
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
        )
    }
}

@Composable
private fun CarSearchCard(
    search: CarSearch?,
    onPickCity: () -> Unit,
    onPickDropoffCity: () -> Unit,
    onSameDropoffChange: (Boolean) -> Unit,
    onPickDates: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
            .padding(horizontal = FlightSpacing.CardPadding, vertical = 12.dp),
    ) {
        if (search == null) {
            Spacer(Modifier.height(240.dp))
            return@Column
        }

        // Where the car goes back is chosen before anything is filled in, so it leads the card the
        // way the trip type leads the flight one: picking the second option adds a field below, and
        // that is easier to follow when the control that adds it sits above the stack.
        SegmentedTabs(
            options = DropoffTabs.map { stringResource(it) },
            selectedIndex = if (search.isSameDropoff) SameDropoffTab else DifferentDropoffTab,
            onSelect = { index -> onSameDropoffChange(index == SameDropoffTab) },
        )

        Spacer(Modifier.height(14.dp))

        IconValueField(
            label = stringResource(Res.string.pick_up_location),
            icon = FlightIcons.PlaceOutlined,
            value = search.pickupPoint,
            placeholder = stringResource(Res.string.where_from),
            onClick = onPickCity,
        )

        // The drop-off field belongs to the tabs, so it expands and collapses with them — on their
        // clock, not the library default, so the pill finishes travelling as the field finishes
        // opening instead of the two running at different speeds.
        AnimatedVisibility(
            visible = !search.isSameDropoff,
            enter = expandVertically(animationSpec = fieldMotion()) +
                fadeIn(animationSpec = fieldMotion()),
            exit = shrinkVertically(animationSpec = fieldMotion()) +
                fadeOut(animationSpec = fieldMotion()),
        ) {
            IconValueField(
                label = stringResource(Res.string.drop_off_location),
                icon = FlightIcons.PlaceOutlined,
                value = search.dropoffPoint.orEmpty(),
                placeholder = stringResource(Res.string.where_to),
                onClick = onPickDropoffCity,
            )
        }

        SplitFieldRow(
            // Last row in the stack: a rule would sit right above the button.
            showDivider = false,
            second = {
                CompactDateField(
                    label = stringResource(Res.string.drop_off),
                    value = storedDateLabel(search.dropoffDate),
                    onClick = onPickDates,
                    caption = search.dropoffTime,
                    showDivider = false,
                )
            },
        ) {
            CompactDateField(
                label = stringResource(Res.string.pick_up),
                value = storedDateLabel(search.pickupDate),
                onClick = onPickDates,
                caption = search.pickupTime,
                showDivider = false,
            )
        }

        Spacer(Modifier.height(16.dp))

        PrimaryButton(text = stringResource(Res.string.search_cars), onClick = onSearch)
    }
}

private fun <T> fieldMotion() = tween<T>(
    durationMillis = SearchFieldMotion.DurationMillis,
    easing = SearchFieldMotion.Easing,
)

private const val SameDropoffTab = 0
private const val DifferentDropoffTab = 1
/** Resolved where the tabs are drawn — a top-level list is built before any composition. */
private val DropoffTabs = listOf(Res.string.same_drop_off, Res.string.different_drop_off)

@Preview
@Composable
private fun CarsScreenPreview() {
    FlightTheme {
        CarsContent(
            state = CarsUiState(
                search = CarSearch(
                    city = "Los Angeles",
                    pickupPoint = "Los Angeles downtown",
                    pickupDate = "Oct 15",
                    dropoffDate = "Oct 18",
                    days = 3,
                    pickupTime = "10:00 AM",
                    dropoffTime = "10:00 AM",
                ),
                recentSearches = listOf(
                    RecentSearchItem(
                        id = "1",
                        title = "Los Angeles",
                        subtitle = RecentSearchSubtitle.Text("Oct 15, 10:00 AM · 3 days"),
                    ),
                    RecentSearchItem(
                        id = "2",
                        title = "Miami → Orlando",
                        subtitle = RecentSearchSubtitle.Text("Nov 1, 09:00 AM · 5 days"),
                    ),
                ),
                popularLocations = listOf(
                    LocationSuggestion(
                        id = "1",
                        title = "Los Angeles",
                        subtitle = "United States",
                    ),
                    LocationSuggestion(
                        id = "2",
                        title = "Rome",
                        subtitle = "Italy",
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onPickCity = {},
            onPickDropoffCity = {},
            onSameDropoffChange = {},
            onPickDates = {},
            onSearch = {},
            onRecentSearch = {},
            onClearRecentSearch = {},
            onPopularLocation = {},
        )
    }
}

