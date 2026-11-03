package com.gocheap.flights.ui.car.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.gocheap.flights.data.model.car.CarLocation
import com.gocheap.flights.data.model.car.CarLocationType
import com.gocheap.flights.ui.component.EmptyState
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.InnerPage
import com.gocheap.flights.ui.home.location.LocationTargets
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.clear
import flighttp.shared.generated.resources.clear_recent_locations
import flighttp.shared.generated.resources.clear_search
import flighttp.shared.generated.resources.couldn_t_search
import flighttp.shared.generated.resources.enter_a_city_or_airport
import flighttp.shared.generated.resources.nothing_found
import flighttp.shared.generated.resources.recent_locations
import flighttp.shared.generated.resources.search_locations
import flighttp.shared.generated.resources.try_again
import flighttp.shared.generated.resources.type_a_city_or_airport_name_to
import flighttp.shared.generated.resources.we_couldn_t_reach_the_location_search
import flighttp.shared.generated.resources.no_pickup_match_try
import flighttp.shared.generated.resources.try_a_city_or_airport_name
import flighttp.shared.generated.resources.try_again_lower

/**
 * The rental location picker, pushed as its own page from the Cars search card.
 *
 * Built like the stays picker — same debounced search, same recents — but flat: with properties
 * filtered out of the response there is no city-and-the-things-inside-it hierarchy left to indent,
 * and a rental is collected from exactly one place.
 *
 * The same page serves both ends of a one-way rental; [target] is what says which one is being
 * filled, and it both titles the page and travels back out with the answer. Picking is a single
 * choice, so a tap returns straight away — there is no confirm step.
 */
@Composable
fun CarLocationScreen(
    target: String,
    onBack: () -> Unit,
    onSelect: (CarLocation) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarLocationViewModel = viewModel { CarLocationViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // The field cannot take focus until the page has been laid out; asking during the push
        // animation is silently dropped and the keyboard never comes up.
        delay(FocusDelayMillis)
        focusRequester.requestFocus()
    }

    InnerPage(
        title = stringResource(carLocationTitle(target)),
        onBack = onBack,
        modifier = modifier,
        imePadding = true,
    ) {

        CarLocationContent(
            state = state,
            focusRequester = focusRequester,
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            onClearRecents = viewModel::onClearRecents,
            onRetry = viewModel::onRetry,
            onSelect = { location ->
                viewModel.onLocationSelected(location)
                onSelect(location)
            },
        )
    }
}

/**
 * How long to wait before grabbing focus. Long enough for the page to settle, short enough that
 * the keyboard still feels like part of the same gesture.
 */
private const val FocusDelayMillis = 220L

@Composable
private fun CarLocationContent(
    state: CarLocationUiState,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onClearRecents: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (CarLocation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal)) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
                focusRequester = focusRequester,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Fixed-height slot rather than one that appears and disappears: letting the indicator take
        // space only while loading would shunt the whole list down a few pixels on every keystroke.
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = FlightColors.Primary,
                    trackColor = FlightColors.Divider,
                )
            }
        }

        when {
            // Failure before "nothing found": an unreachable supplier returns no rows either, and
            // calling that an empty result would send the user off rewording a fine query.
            state.showError -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    icon = FlightIcons.Search,
                    title = stringResource(Res.string.couldn_t_search),
                    message = stringResource(Res.string.we_couldn_t_reach_the_location_search) +
                        stringResource(Res.string.try_again_lower),
                )
                Text(
                    text = stringResource(Res.string.try_again),
                    style = MaterialTheme.typography.labelLarge,
                    color = FlightColors.Primary,
                    modifier = Modifier
                        .clip(FlightShapes.Pill)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            state.showNoMatches -> EmptyState(
                icon = FlightIcons.Search,
                title = stringResource(Res.string.nothing_found),
                message = stringResource(Res.string.no_pickup_match_try, state.query.trim()) + " " +
                    stringResource(Res.string.try_a_city_or_airport_name),
            )

            state.showPrompt -> EmptyState(
                icon = FlightIcons.CarOutlined,
                title = stringResource(Res.string.search_locations),
                message = stringResource(Res.string.type_a_city_or_airport_name_to),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.showRecents) {
                    item(key = "recent-header") {
                        RecentHeader(onClear = onClearRecents)
                    }
                    itemsIndexed(
                        items = state.recents,
                        key = { _, location -> "recent-${location.id}" },
                    ) { index, location ->
                        RecentRow(
                            location = location,
                            onClick = { onSelect(location) },
                        )
                        if (index < state.recents.lastIndex) RowDivider()
                    }
                }

                if (state.showResults) {
                    itemsIndexed(
                        items = state.results,
                        key = { _, location -> "result-${location.id}" },
                    ) { index, location ->
                        LocationRow(
                            location = location,
                            onClick = { onSelect(location) },
                        )
                        if (index < state.results.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentHeader(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = FlightSpacing.ScreenHorizontal, end = FlightSpacing.ScreenHorizontal, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.recent_locations),
            style = MaterialTheme.typography.titleMedium,
            color = FlightColors.TextPrimary,
        )
        Text(
            text = stringResource(Res.string.clear),
            style = MaterialTheme.typography.bodyMedium,
            color = FlightColors.Primary,
            modifier = Modifier
                .clip(FlightShapes.Pill)
                .clickable(onClickLabel = stringResource(Res.string.clear_recent_locations), onClick = onClear)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = FlightSpacing.ScreenHorizontal + RowIconColumn, end = FlightSpacing.ScreenHorizontal),
        thickness = 1.dp,
        color = FlightColors.Divider,
    )
}

@Composable
private fun RecentRow(
    location: CarLocation,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Clock rather than the type icon — the same cue the other pickers use for their recents.
        RowIcon(icon = FlightIcons.Clock)
        Spacer(Modifier.width(12.dp))
        Text(
            text = location.name,
            style = MaterialTheme.typography.titleMedium,
            color = FlightColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LocationRow(
    location: CarLocation,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon = location.type.icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A city row names itself in full and sends no location, so there is no second line to
            // draw — the spacer goes with it rather than leaving the row taller than its one line.
            if (location.location.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = location.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Keeps every row's text on the same baseline. */
@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FlightColors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Field)
            .background(FlightColors.SurfaceMuted)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = FlightIcons.Search,
            contentDescription = null,
            tint = FlightColors.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // BasicTextField has no placeholder slot, so it sits underneath the empty field.
            if (query.isEmpty()) {
                Text(
                    text = stringResource(Res.string.enter_a_city_or_airport),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FlightColors.TextTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = FlightColors.TextPrimary),
                ),
                cursorBrush = SolidColor(FlightColors.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = FlightIcons.Close,
                contentDescription = stringResource(Res.string.clear_search),
                tint = FlightColors.TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(FlightShapes.Pill)
                    .clickable(onClickLabel = stringResource(Res.string.clear_search), onClick = onClear),
            )
        }
    }
}

private val CarLocationType.icon: ImageVector
    get() = when (this) {
        CarLocationType.City -> FlightIcons.Place
        CarLocationType.Airport -> FlightIcons.FlightTakeoff
        CarLocationType.Other -> FlightIcons.Place
    }

@Preview
@Composable
private fun CarLocationScreenPreview() {
    FlightTheme {
        InnerPage(title = stringResource(carLocationTitle(LocationTargets.Pickup)), onBack = {}) {
            CarLocationContent(
                state = CarLocationUiState(
                    query = "san fr",
                    hasSearched = true,
                    results = listOf(
                        CarLocation(
                            id = "city-1-San Francisco",
                            name = "San Francisco",
                            location = "California, United States",
                            type = CarLocationType.City,
                            supplierId = "city-1",
                        ),
                        CarLocation(
                            id = "apt-1-San Francisco International Airport",
                            name = "San Francisco International Airport",
                            location = "San Francisco, United States",
                            type = CarLocationType.Airport,
                            supplierId = "apt-1",
                        ),
                    ),
                ),
                focusRequester = remember { FocusRequester() },
                onQueryChange = {},
                onClearQuery = {},
                onClearRecents = {},
                onRetry = {},
                onSelect = {},
            )
        }
    }
}

/** The 24dp icon plus the 12dp gap after it: what a row's text is indented by. */
private val RowIconColumn = 36.dp
