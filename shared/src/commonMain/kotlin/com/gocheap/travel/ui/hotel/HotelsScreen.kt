package com.gocheap.travel.ui.hotel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.gocheap.travel.data.model.HotelSearch
import com.gocheap.travel.data.model.LocationSuggestion
import com.gocheap.travel.data.model.StayRoom
import com.gocheap.travel.data.model.hotel.HotelDestination
import com.gocheap.travel.ui.calendar.CalendarMode
import com.gocheap.travel.ui.calendar.CalendarRequest
import com.gocheap.travel.ui.calendar.DateSelection
import com.gocheap.travel.ui.hotel.guests.GuestSelection
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
import com.gocheap.travel.ui.component.SkyHeader
import com.gocheap.travel.ui.component.SplitFieldRow
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import com.gocheap.travel.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import com.gocheap.travel.ui.component.storedDateLabel
import com.gocheap.travel.ui.component.guestsAndRoomsLabel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.book_hotels_at_the_best_price
import flighttp.shared.generated.resources.check_in
import flighttp.shared.generated.resources.check_out
import flighttp.shared.generated.resources.destination
import flighttp.shared.generated.resources.find_your_nperfect_stay
import flighttp.shared.generated.resources.guests
import flighttp.shared.generated.resources.popular_locations
import flighttp.shared.generated.resources.recent_searches
import flighttp.shared.generated.resources.search_hotels
import flighttp.shared.generated.resources.stay_dates
import flighttp.shared.generated.resources.where_to

@Composable
fun HotelsScreen(
    viewModel: HotelsViewModel = viewModel { HotelsViewModel() },
    onSearch: (HotelSearch) -> Unit = {},
    onOpenCalendar: (CalendarRequest) -> Unit = {},
    pickedDates: DateSelection? = null,
    onPickedDatesHandled: () -> Unit = {},
    onOpenGuestPicker: (rooms: List<StayRoom>) -> Unit = {},
    pickedGuests: GuestSelection? = null,
    onPickedGuestsHandled: () -> Unit = {},
    onOpenDestinationPicker: () -> Unit = {},
    pickedDestination: HotelDestination? = null,
    onPickedDestinationHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Resolved here rather than inside the lambda below: `stringResource` is only callable
    // from a composition, and that lambda is an ordinary callback.
    val stayDatesTitle = stringResource(Res.string.stay_dates)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pickedDates) {
        val picked = pickedDates ?: return@LaunchedEffect
        viewModel.onDatesPicked(picked.start, picked.end)
        onPickedDatesHandled()
    }

    LaunchedEffect(pickedGuests) {
        val picked = pickedGuests ?: return@LaunchedEffect
        viewModel.onGuestsPicked(picked)
        onPickedGuestsHandled()
    }

    LaunchedEffect(pickedDestination) {
        val picked = pickedDestination ?: return@LaunchedEffect
        viewModel.onDestinationPicked(picked)
        onPickedDestinationHandled()
    }

    LaunchedEffect(state.snackbarMessageId) {
        val message = state.snackbarMessage?.let { getString(it) } ?: return@LaunchedEffect
        viewModel.onSnackbarMessageShown()
        snackbarHostState.showSnackbar(message)
    }

    HotelsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        // The picker fills the field rather than reading from it, so it opens whether or not the
        // card has loaded its defaults yet.
        onPickCity = onOpenDestinationPicker,
        onPickDates = {
            val search = state.search
            if (search != null) {
                onOpenCalendar(
                    CalendarRequest(
                        title = stayDatesTitle,
                        mode = CalendarMode.Range,
                        start = search.checkInDate,
                        end = search.checkOutDate,
                    ),
                )
            }
        },
        onPickGuests = {
            val search = state.search
            if (search != null) onOpenGuestPicker(search.rooms)
        },
        onSearch = { viewModel.onSearch(onSearch) },
        onRecentSearch = { id -> viewModel.onRecentSearchClick(id, onSearch) },
        onClearRecentSearch = viewModel::onRecentSearchClear,
        onPopularLocation = { id -> viewModel.onPopularLocationClick(id, onSearch) },
        modifier = modifier,
    )
}

@Composable
private fun HotelsContent(
    state: HotelsUiState,
    snackbarHostState: SnackbarHostState,
    onPickCity: () -> Unit,
    onPickDates: () -> Unit,
    onPickGuests: () -> Unit,
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
                            text = stringResource(Res.string.find_your_nperfect_stay),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(Res.string.book_hotels_at_the_best_price),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.94f),
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                    HotelSearchCard(
                        search = state.search,
                        onPickCity = onPickCity,
                        onPickDates = onPickDates,
                        onPickGuests = onPickGuests,
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
private fun HotelSearchCard(
    search: HotelSearch?,
    onPickCity: () -> Unit,
    onPickDates: () -> Unit,
    onPickGuests: () -> Unit,
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
            Spacer(Modifier.height(280.dp))
            return@Column
        }

        IconValueField(
            label = stringResource(Res.string.destination),
            icon = FlightIcons.PlaceOutlined,
            value = search.city,
            placeholder = stringResource(Res.string.where_to),
            onClick = onPickCity,
        )

        SplitFieldRow(
            second = {
                CompactDateField(
                    label = stringResource(Res.string.check_out),
                    value = storedDateLabel(search.checkOutDate),
                    onClick = onPickDates,
                    showDivider = false,
                )
            },
        ) {
            CompactDateField(
                label = stringResource(Res.string.check_in),
                value = storedDateLabel(search.checkInDate),
                onClick = onPickDates,
                showDivider = false,
            )
        }

        IconValueField(
            label = stringResource(Res.string.guests),
            icon = FlightIcons.PeopleOutlined,
            value = guestsAndRoomsLabel(GuestSelection(rooms = search.rooms)),
            onClick = onPickGuests,
            // Last field in the stack: its rule would sit right above the button.
            showDivider = false,
        )

        Spacer(Modifier.height(16.dp))

        PrimaryButton(text = stringResource(Res.string.search_hotels), onClick = onSearch)
    }
}

@Preview
@Composable
private fun HotelsScreenPreview() {
    FlightTheme {
        HotelsContent(
            state = HotelsUiState(
                search = HotelSearch(
                    city = "Paris",
                    checkInDate = "Oct 15",
                    checkOutDate = "Oct 20",
                    nights = 5,
                    rooms = listOf(StayRoom(adults = 2)),
                ),
                recentSearches = listOf(
                    RecentSearchItem(
                        id = "1",
                        title = "Tokyo",
                        subtitle = RecentSearchSubtitle.Text("Nov 1 · 3 nights · 2 guests"),
                    ),
                    RecentSearchItem(
                        id = "2",
                        title = "London",
                        subtitle = RecentSearchSubtitle.Text("Dec 10 · 4 nights · 1 guest"),
                    ),
                ),
                popularLocations = listOf(
                    LocationSuggestion(
                        id = "1",
                        title = "New York",
                        subtitle = "United States",
                    ),
                    LocationSuggestion(
                        id = "2",
                        title = "Bali",
                        subtitle = "Indonesia",
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onPickCity = {},
            onPickDates = {},
            onPickGuests = {},
            onSearch = {},
            onRecentSearch = {},
            onClearRecentSearch = {},
            onPopularLocation = {},
        )
    }
}

