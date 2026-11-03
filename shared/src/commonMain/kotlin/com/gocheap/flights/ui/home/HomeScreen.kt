package com.gocheap.flights.ui.home

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
import com.gocheap.flights.data.model.Airport
import com.gocheap.flights.data.model.CabinClass
import com.gocheap.flights.data.model.LocationSuggestion
import com.gocheap.flights.data.model.TripSearch
import com.gocheap.flights.ui.calendar.CalendarMode
import com.gocheap.flights.ui.calendar.CalendarRequest
import com.gocheap.flights.ui.calendar.DateSelection
import com.gocheap.flights.ui.home.location.LocationKind
import com.gocheap.flights.ui.home.location.LocationRequest
import com.gocheap.flights.ui.home.location.LocationSelection
import com.gocheap.flights.ui.home.location.LocationTargets
import com.gocheap.flights.ui.component.AirportField
import com.gocheap.flights.ui.component.BottomBarSpacer
import com.gocheap.flights.ui.component.CircleIconButton
import com.gocheap.flights.ui.component.CompactDateField
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.glassCard
import com.gocheap.flights.ui.component.IconValueField
import com.gocheap.flights.ui.component.PopularLocationGrid
import com.gocheap.flights.ui.component.PrimaryButton
import com.gocheap.flights.ui.component.RecentSearchItem
import com.gocheap.flights.ui.component.RecentSearchSubtitle
import com.gocheap.flights.ui.component.RecentSearchRow
import com.gocheap.flights.ui.component.SectionHeader
import com.gocheap.flights.ui.component.SplitFieldRow
import com.gocheap.flights.ui.component.SegmentedTabs
import com.gocheap.flights.ui.component.SkyHeader
import com.gocheap.flights.ui.component.SwapButton
import com.gocheap.flights.ui.home.travellers.TravellerSelection
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import com.gocheap.flights.ui.component.travellerLabel
import com.gocheap.flights.ui.component.storedDateLabel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.departure
import flighttp.shared.generated.resources.flying_from
import flighttp.shared.generated.resources.flying_to
import flighttp.shared.generated.resources.from_label
import flighttp.shared.generated.resources.let_s_nbook_your_flight
import flighttp.shared.generated.resources.make_your_flight_more_memorable
import flighttp.shared.generated.resources.popular_locations
import flighttp.shared.generated.resources.recent_searches
import flighttp.shared.generated.resources.return_label
import flighttp.shared.generated.resources.search_flights
import flighttp.shared.generated.resources.to_label
import flighttp.shared.generated.resources.travellers
import flighttp.shared.generated.resources.where_from
import flighttp.shared.generated.resources.where_to
import flighttp.shared.generated.resources.departure_date
import flighttp.shared.generated.resources.one_way
import flighttp.shared.generated.resources.round_trip
import flighttp.shared.generated.resources.travel_dates

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel { HomeViewModel() },
    onSearch: (TripSearch) -> Unit = {},
    onOpenCalendar: (CalendarRequest) -> Unit = {},
    pickedDates: DateSelection? = null,
    onPickedDatesHandled: () -> Unit = {},
    onOpenLocationPicker: (LocationRequest) -> Unit = {},
    pickedLocation: LocationSelection? = null,
    onPickedLocationHandled: () -> Unit = {},
    onOpenTravellerPicker: (party: TravellerSelection) -> Unit = {},
    pickedTravellers: TravellerSelection? = null,
    onPickedTravellersHandled: () -> Unit = {},
    onNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Resolved here rather than where the request is built: those builders run inside ordinary
    // lambdas, and `stringResource` is only callable from a composition.
    val travelDatesTitle = stringResource(Res.string.travel_dates)
    val departureDateTitle = stringResource(Res.string.departure_date)
    val calendarTitle = { roundTrip: Boolean -> if (roundTrip) travelDatesTitle else departureDateTitle }
    val flyingFromTitle = stringResource(Res.string.flying_from)
    val flyingToTitle = stringResource(Res.string.flying_to)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pickedDates) {
        val picked = pickedDates ?: return@LaunchedEffect
        viewModel.onDatesPicked(picked.start, picked.end)
        onPickedDatesHandled()
    }

    LaunchedEffect(pickedLocation) {
        val picked = pickedLocation ?: return@LaunchedEffect
        viewModel.onLocationPicked(picked.target, picked.destination)
        onPickedLocationHandled()
    }

    LaunchedEffect(pickedTravellers) {
        val picked = pickedTravellers ?: return@LaunchedEffect
        viewModel.onTravellersPicked(picked)
        onPickedTravellersHandled()
    }

    LaunchedEffect(state.snackbarMessageId) {
        val message = state.snackbarMessage?.let { getString(it) } ?: return@LaunchedEffect
        viewModel.onSnackbarMessageShown()
        snackbarHostState.showSnackbar(message)
    }

    HomeScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onSwap = viewModel::onSwapAirports,
        onPickOrigin = {
            val search = state.search
            if (search != null) onOpenLocationPicker(search.originRequest(flyingFromTitle))
        },
        onPickDestination = {
            val search = state.search
            if (search != null) onOpenLocationPicker(search.destinationRequest(flyingToTitle))
        },
        onPickDates = {
            val search = state.search
            if (search != null) onOpenCalendar(search.toCalendarRequest(calendarTitle(search.isRoundTrip)))
        },
        onPickTravellers = {
            val search = state.search
            if (search != null) onOpenTravellerPicker(search.toTravellerSelection())
        },
        onRoundTripChange = viewModel::onRoundTripChange,
        onSearchFlight = { viewModel.onSearchFlight(onSearch) },
        onRecentSearch = { id -> viewModel.onRecentSearchClick(id, onSearch) },
        onClearRecentSearch = viewModel::onRecentSearchClear,
        onPopularLocation = { id -> viewModel.onPopularLocationClick(id, onSearch) },
        onNotifications = onNotifications,
        modifier = modifier,
    )
}

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onSwap: () -> Unit,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit,
    onPickDates: () -> Unit,
    onPickTravellers: () -> Unit,
    onRoundTripChange: (Boolean) -> Unit,
    onSearchFlight: () -> Unit,
    onRecentSearch: (String) -> Unit,
    onClearRecentSearch: (String) -> Unit,
    onPopularLocation: (String) -> Unit,
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(FlightColors.Background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // The sky sits behind the heading and the top of the search card overlaps it.
            Box {
                SkyHeader(height = 286.dp)

                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    HomeHeading(onNotifications = onNotifications)
                    Spacer(Modifier.height(26.dp))
                    SearchCard(
                        search = state.search,
                        onSwap = onSwap,
                        onPickOrigin = onPickOrigin,
                        onPickDestination = onPickDestination,
                        onPickDates = onPickDates,
                        onPickTravellers = onPickTravellers,
                        onRoundTripChange = onRoundTripChange,
                        onSearchFlight = onSearchFlight,
                        modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Nothing searched yet means no section at all — header included — rather than a
            // heading sitting over a blank row. Each block carries its own trailing gap so the
            // spacing stays right whichever ones are present.
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
private fun HomeHeading(
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = FlightSpacing.ScreenHorizontal, end = FlightSpacing.ScreenHorizontal, top = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.let_s_nbook_your_flight),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.make_your_flight_more_memorable),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.94f),
            )
        }
        /*Spacer(Modifier.height(0.dp))
        CircleIconButton(
            icon = FlightIcons.Bell,
            contentDescription = "Notifications",
            onClick = onNotifications,
            tint = Color.White,
            borderColor = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 4.dp),
        )*/
    }
}

@Composable
private fun SearchCard(
    search: TripSearch?,
    onSwap: () -> Unit,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit,
    onPickDates: () -> Unit,
    onPickTravellers: () -> Unit,
    onRoundTripChange: (Boolean) -> Unit,
    onSearchFlight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
            .padding(horizontal = FlightSpacing.CardPadding, vertical = 12.dp),
    ) {
        if (search == null) {
            Spacer(Modifier.height(270.dp))
            return@Column
        }

        // The trip's shape is chosen before anything is filled in, so it leads the card: a return
        // leg adds a field below, and that is easier to follow when the control that adds it sits
        // above the stack rather than in the middle of it.
        SegmentedTabs(
            options = TripTypeTabs.map { stringResource(it) },
            selectedIndex = if (search.isRoundTrip) RoundTripTab else OneWayTab,
            onSelect = { index -> onRoundTripChange(index == RoundTripTab) },
        )

        Spacer(Modifier.height(10.dp))

        // From and To stacked, with the swap control straddling their shared edge.
        Box {
            Column {
                AirportField(
                    label = stringResource(Res.string.from_label),
                    icon = FlightIcons.FlightTakeoffOutlined,
                    airport = search.from,
                    placeholder = stringResource(Res.string.where_from),
                    onClick = onPickOrigin,
                )
                AirportField(
                    label = stringResource(Res.string.to_label),
                    icon = FlightIcons.FlightLandOutlined,
                    airport = search.to,
                    placeholder = stringResource(Res.string.where_to),
                    onClick = onPickDestination,
                )
            }
            SwapButton(
                onClick = onSwap,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        SplitFieldRow(
            // One way collapses the return column and hands its width to departure. It is still
            // handed in either way, so it has something to draw while it shrinks — the date the
            // view model holds on to is exactly what a switch back would restore.
            secondVisible = search.isRoundTrip,
            second = {
                CompactDateField(
                    label = stringResource(Res.string.return_label),
                    value = storedDateLabel(search.returnDate),
                    onClick = onPickDates,
                    showDivider = false,
                )
            },
        ) {
            CompactDateField(
                label = stringResource(Res.string.departure),
                value = storedDateLabel(search.departureDate),
                onClick = onPickDates,
                showDivider = false,
            )
        }

        IconValueField(
            label = stringResource(Res.string.travellers),
            icon = FlightIcons.PeopleOutlined,
            value = travellerLabel(search.toTravellerSelection()),
            onClick = onPickTravellers,
            // Last field in the stack: its rule would sit right above the button.
            showDivider = false,
        )

        Spacer(Modifier.height(12.dp))

        PrimaryButton(text = stringResource(Res.string.search_flights), onClick = onSearchFlight)
    }
}

private const val RoundTripTab = 0
private const val OneWayTab = 1
/** Resolved where the tabs are drawn — a top-level list is built before any composition. */
private val TripTypeTabs = listOf(Res.string.round_trip, Res.string.one_way)

/** The party as the traveller page edits it, which is also what the field reads back. */
private fun TripSearch.toTravellerSelection(): TravellerSelection = TravellerSelection(
    adults = adults,
    children = children,
    infants = infants,
    cabinClass = cabinClass,
)

/**
 * Both date fields open the same picker: on a round trip that is one range covering departure and
 * return, and on a one-way trip it collapses to a single date.
 */
private fun TripSearch.toCalendarRequest(title: String): CalendarRequest = CalendarRequest(
    title = title,
    mode = if (isRoundTrip) CalendarMode.Range else CalendarMode.Single,
    start = departureDate,
    end = returnDate.takeIf { isRoundTrip },
    spanUnit = "days",
)

/** Each endpoint hides the other, so a trip can never be made to start and end in one place. */
private fun TripSearch.originRequest(title: String): LocationRequest = LocationRequest(
    title = title,
    kind = LocationKind.Airport,
    target = LocationTargets.Origin,
    excludeId = to.code,
)

private fun TripSearch.destinationRequest(title: String): LocationRequest = LocationRequest(
    title = title,
    kind = LocationKind.Airport,
    target = LocationTargets.Destination,
    excludeId = from.code,
)

@Preview
@Composable
private fun HomeScreenPreview() {
    FlightTheme {
        HomeScreenContent(
            state = HomeUiState(
                search = TripSearch(
                    from = Airport(code = "JFK", city = "New York", name = "John F. Kennedy Intl"),
                    to = Airport(code = "LHR", city = "London", name = "Heathrow Airport"),
                    departureDate = "Oct 15",
                    returnDate = "Oct 22",
                    isRoundTrip = true,
                    adults = 2,
                    children = 1,
                    cabinClass = CabinClass.Economy,
                ),
                recentSearches = listOf(
                    RecentSearchItem(
                        id = "JFK-LHR",
                        title = "JFK → LHR",
                        subtitle = RecentSearchSubtitle.Text("Oct 15 → Oct 22"),
                    ),
                    RecentSearchItem(
                        id = "SFO-NRT",
                        title = "SFO → NRT",
                        subtitle = RecentSearchSubtitle.Text("Nov 1 · One way"),
                    ),
                ),
                popularLocations = listOf(
                    LocationSuggestion(
                        id = "TYO",
                        title = "Tokyo",
                        subtitle = "Japan",
                    ),
                    LocationSuggestion(
                        id = "PAR",
                        title = "Paris",
                        subtitle = "France",
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onSwap = {},
            onPickOrigin = {},
            onPickDestination = {},
            onPickDates = {},
            onPickTravellers = {},
            onRoundTripChange = {},
            onSearchFlight = {},
            onRecentSearch = {},
            onClearRecentSearch = {},
            onPopularLocation = {},
            onNotifications = {},
        )
    }
}

