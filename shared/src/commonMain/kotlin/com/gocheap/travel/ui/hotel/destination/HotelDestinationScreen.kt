package com.gocheap.travel.ui.hotel.destination

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
import com.gocheap.travel.data.model.hotel.HotelDestination
import com.gocheap.travel.data.model.hotel.HotelDestinationType
import com.gocheap.travel.ui.component.EmptyState
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.InnerPage
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import com.gocheap.travel.ui.theme.FlightShapes
import com.gocheap.travel.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.clear
import flighttp.shared.generated.resources.clear_recent_destinations
import flighttp.shared.generated.resources.clear_search
import flighttp.shared.generated.resources.couldn_t_search
import flighttp.shared.generated.resources.enter_a_city_area_or_hotel
import flighttp.shared.generated.resources.nothing_found
import flighttp.shared.generated.resources.recent_destinations
import flighttp.shared.generated.resources.search_destinations
import flighttp.shared.generated.resources.try_again
import flighttp.shared.generated.resources.type_a_city_area_airport_or_hotel
import flighttp.shared.generated.resources.we_couldn_t_reach_the_hotel_search
import flighttp.shared.generated.resources.destination_2
import flighttp.shared.generated.resources.no_stay_match_try
import flighttp.shared.generated.resources.try_a_city_area_or_property
import flighttp.shared.generated.resources.try_again_lower

/**
 * The stays destination picker, pushed as its own page from the Hotels search card.
 *
 * Deliberately separate from the shared location picker: a stay can be booked against a city, a
 * district, an airport or one specific property, and those come back interleaved — a property is
 * shown indented under the city it sits in. The flight and car cards only ever pick one flat thing,
 * so folding this into the shared page would make it carry a hierarchy neither of them has.
 *
 * Picking is a single choice, so a tap returns straight away — there is no confirm step.
 */
@Composable
fun HotelDestinationScreen(
    onBack: () -> Unit,
    onSelect: (HotelDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HotelDestinationViewModel = viewModel { HotelDestinationViewModel() },
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
        title = stringResource(DestinationTitle),
        onBack = onBack,
        modifier = modifier,
        imePadding = true,
    ) {

        HotelDestinationContent(
            state = state,
            focusRequester = focusRequester,
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            onClearRecents = viewModel::onClearRecents,
            onRetry = viewModel::onRetry,
            onSelect = { destination ->
                viewModel.onDestinationSelected(destination)
                onSelect(destination)
            },
        )
    }
}

/**
 * How long to wait before grabbing focus. Long enough for the page to settle, short enough that
 * the keyboard still feels like part of the same gesture.
 */
private const val FocusDelayMillis = 220L

/**
 * Named after the field it fills rather than asked as a question the way the sheet did — a top bar
 * gives it one centred line, which "Where do you want to stay?" would spend on an ellipsis.
 */
private val DestinationTitle = Res.string.destination_2

@Composable
private fun HotelDestinationContent(
    state: HotelDestinationUiState,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onClearRecents: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (HotelDestination) -> Unit,
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
                    message = stringResource(Res.string.we_couldn_t_reach_the_hotel_search) +
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
                message = stringResource(Res.string.no_stay_match_try, state.query.trim()) + " " +
                    stringResource(Res.string.try_a_city_area_or_property),
            )

            state.showPrompt -> EmptyState(
                icon = FlightIcons.HotelOutlined,
                title = stringResource(Res.string.search_destinations),
                message = stringResource(Res.string.type_a_city_area_airport_or_hotel),
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
                        key = { _, destination -> "recent-${destination.id}" },
                    ) { index, destination ->
                        RecentRow(
                            destination = destination,
                            onClick = { onSelect(destination) },
                        )
                        if (index < state.recents.lastIndex) RowDivider()
                    }
                }

                if (state.showResults) {
                    val rows = state.results.toNestedRows()
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> "result-${row.destination.id}" },
                    ) { index, row ->
                        DestinationRow(
                            destination = row.destination,
                            depth = row.depth,
                            onClick = { onSelect(row.destination) },
                        )
                        if (index < rows.lastIndex) RowDivider()
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
            text = stringResource(Res.string.recent_destinations),
            style = MaterialTheme.typography.titleMedium,
            color = FlightColors.TextPrimary,
        )
        Text(
            text = stringResource(Res.string.clear),
            style = MaterialTheme.typography.bodyMedium,
            color = FlightColors.Primary,
            modifier = Modifier
                .clip(FlightShapes.Pill)
                .clickable(onClickLabel = stringResource(Res.string.clear_recent_destinations), onClick = onClear)
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
    destination: HotelDestination,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Clock rather than the type icon — the same cue the shared picker uses for its recents.
        RowIcon(icon = FlightIcons.Clock)
        Spacer(Modifier.width(12.dp))
        Text(
            text = destination.name,
            style = MaterialTheme.typography.titleMedium,
            color = FlightColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DestinationRow(
    destination: HotelDestination,
    depth: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (depth > 0) Spacer(Modifier.width((16 * depth).dp))
        RowIcon(icon = destination.type.icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = destination.name,
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = destination.location,
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Keeps every row's text on the same baseline, indentation aside. */
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
                    text = stringResource(Res.string.enter_a_city_area_or_hotel),
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

/** A destination plus how far it is indented. */
private data class NestedRow(
    val destination: HotelDestination,
    val depth: Int,
)

/**
 * Indents a result under its parent when both are in the response. Depth stops at one: the supplier
 * only ever nests a property under its city, and a deeper ladder would push the text off a narrow
 * phone anyway.
 *
 * Matched against every id a row can be referenced by, since the supplier points a child at
 * whichever of the parent's ids it indexed on rather than consistently at one of them.
 *
 * A child whose parent is not in the response is left flush rather than dropped — searching a hotel
 * by name should still find it when its city did not match.
 */
private fun List<HotelDestination>.toNestedRows(): List<NestedRow> {
    val presentKeys = flatMapTo(mutableSetOf()) { it.lookupKeys }
    return map { destination ->
        NestedRow(
            destination = destination,
            depth = if (destination.parentId in presentKeys) 1 else 0,
        )
    }
}

private val HotelDestinationType.icon: ImageVector
    get() = when (this) {
        HotelDestinationType.City -> FlightIcons.Place
        HotelDestinationType.Airport -> FlightIcons.FlightTakeoff
        HotelDestinationType.Hotel -> FlightIcons.HotelOutlined
        HotelDestinationType.Other -> FlightIcons.Place
    }

@Preview
@Composable
private fun HotelDestinationScreenPreview() {
    FlightTheme {
        InnerPage(title = stringResource(DestinationTitle), onBack = {}) {
            HotelDestinationContent(
                state = HotelDestinationUiState(
                    query = "san fr",
                    hasSearched = true,
                    results = listOf(
                        HotelDestination(
                            id = "city-1-San Francisco",
                            name = "San Francisco",
                            location = "California, United States",
                            type = HotelDestinationType.City,
                            supplierId = "city-1",
                        ),
                        HotelDestination(
                            id = "apt-1-San Francisco International Airport",
                            name = "San Francisco International Airport",
                            location = "San Francisco, United States",
                            type = HotelDestinationType.Airport,
                            parentId = "city-1",
                            supplierId = "apt-1",
                        ),
                        HotelDestination(
                            id = "htl-1-The Harbour View",
                            name = "The Harbour View",
                            location = "412 Embarcadero, San Francisco",
                            type = HotelDestinationType.Hotel,
                            parentId = "city-1",
                            supplierId = "htl-1",
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
