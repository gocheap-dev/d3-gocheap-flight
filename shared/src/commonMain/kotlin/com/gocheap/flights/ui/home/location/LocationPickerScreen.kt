package com.gocheap.flights.ui.home.location

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.gocheap.flights.data.model.flight.FlightDestination
import com.gocheap.flights.data.model.flight.FlightDestinationType
import com.gocheap.flights.ui.component.EmptyState
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.InnerPage
import com.gocheap.flights.ui.component.ThumbnailTile
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.theme.FlightShapes
import flighttp.shared.generated.resources.Res
import flighttp.shared.generated.resources.search_city_or_country
import flighttp.shared.generated.resources.search_city_airport_or_code
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.clear
import flighttp.shared.generated.resources.clear_recent_locations
import flighttp.shared.generated.resources.clear_search
import flighttp.shared.generated.resources.couldn_t_search
import flighttp.shared.generated.resources.nothing_found
import flighttp.shared.generated.resources.recent
import flighttp.shared.generated.resources.try_again
import flighttp.shared.generated.resources.we_couldn_t_reach_the_location_search
import flighttp.shared.generated.resources.search_airports
import flighttp.shared.generated.resources.search_cities
import flighttp.shared.generated.resources.type_a_city_airport_name_or_code
import flighttp.shared.generated.resources.type_a_city_or_country_to_get
import flighttp.shared.generated.resources.no_match_try_again
import flighttp.shared.generated.resources.try_again_lower

/**
 * The flight location picker, pushed from the search card for either end of a trip.
 *
 * Rows come from the supplier's autocomplete rather than a list this app ships, so the page is a
 * search box first: there is nothing to browse until something is typed, and what comes back is a
 * mix of cities and the airports inside them.
 *
 * Picking is a single choice, so a tap returns straight away — there is no confirm step the way the
 * calendar needs one.
 */
@Composable
fun LocationPickerScreen(
    title: String,
    kind: LocationKind,
    target: String,
    excludeId: String?,
    onBack: () -> Unit,
    onSelect: (LocationSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LocationPickerViewModel = viewModel(key = "$kind-$target-$excludeId") {
        LocationPickerViewModel(kind = kind, excludeId = excludeId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // The field cannot take focus until the page has been laid out; asking during the push
        // animation is silently dropped and the keyboard never comes up.
        delay(FocusDelayMillis)
        focusRequester.requestFocus()
    }

    InnerPage(
        title = title,
        onBack = onBack,
        modifier = modifier,
        imePadding = true,
    ) {

        SearchField(
            query = state.query,
            placeholder = when (kind) {
                LocationKind.Airport -> stringResource(Res.string.search_city_airport_or_code)
                LocationKind.City -> stringResource(Res.string.search_city_or_country)
            },
            onQueryChange = viewModel::onQueryChange,
            onClear = viewModel::onClearQuery,
            focusRequester = focusRequester,
            modifier = Modifier.padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 12.dp),
        )

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

        val select: (FlightDestination) -> Unit = { destination ->
            viewModel.onLocationSelected(destination)
            onSelect(LocationSelection(target = target, destination = destination))
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
                        .clickable(onClick = viewModel::onRetry)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            state.showNoMatches -> EmptyState(
                icon = FlightIcons.Search,
                title = stringResource(Res.string.nothing_found),
                message = stringResource(Res.string.no_match_try_again, state.query.trim()),
            )

            state.showPrompt -> EmptyState(
                icon = FlightIcons.Search,
                title = when (kind) {
                    LocationKind.Airport -> stringResource(Res.string.search_airports)
                    LocationKind.City -> stringResource(Res.string.search_cities)
                },
                message = when (kind) {
                    LocationKind.Airport -> stringResource(Res.string.type_a_city_airport_name_or_code)
                    LocationKind.City -> stringResource(Res.string.type_a_city_or_country_to_get)
                },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = FlightSpacing.ScreenHorizontal, end = FlightSpacing.ScreenHorizontal, bottom = 20.dp),
            ) {
                // Recents and results are never on screen together, but the keys stay prefixed so
                // that cannot become a duplicate-key crash if that ever changes.
                if (state.showRecents) {
                    item(key = "label-recent") {
                        RecentHeader(onClear = viewModel::onClearRecents)
                    }
                    items(state.recents, key = { "recent-${it.iata}" }) { destination ->
                        LocationRow(
                            destination = destination,
                            // Clock rather than pin, the same cue the Recent Searches cards use.
                            icon = FlightIcons.Clock,
                            onClick = { select(destination) },
                        )
                    }
                }

                if (state.showResults) {
                    items(state.results, key = { "result-${it.iata}" }) { destination ->
                        LocationRow(destination = destination, onClick = { select(destination) })
                    }
                }
            }
        }
    }
}

/**
 * How long to wait before grabbing focus. Long enough for the page to settle, short enough that
 * the keyboard still feels like part of the same gesture.
 */
private const val FocusDelayMillis = 220L

/** Quiet divider between the recent shortcut and whatever the supplier answers with. */
@Composable
private fun RecentHeader(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.recent),
            style = MaterialTheme.typography.labelMedium,
            color = FlightColors.TextSecondary,
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
private fun SearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Field)
            .border(1.dp, FlightColors.Border, FlightShapes.Field)
            .background(FlightColors.SurfaceMuted)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                    text = placeholder,
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
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(FlightColors.BorderStrong)
                    .clickable(onClickLabel = stringResource(Res.string.clear_search), onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FlightIcons.Close,
                    contentDescription = stringResource(Res.string.clear_search),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun LocationRow(
    destination: FlightDestination,
    onClick: () -> Unit,
    icon: ImageVector = destination.type.icon,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FlightShapes.Card)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThumbnailTile(icon = icon, size = 44.dp)
        Spacer(Modifier.width(14.dp))
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
                text = destination.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The code is what the search actually runs on, so every row wears it ahead of its place. */
private val FlightDestination.subtitle: String
    get() = listOf(iata, location).filter { it.isNotBlank() }.joinToString(separator = " · ")

private val FlightDestinationType.icon: ImageVector
    get() = when (this) {
        FlightDestinationType.Airport -> FlightIcons.FlightTakeoff
        FlightDestinationType.City -> FlightIcons.Place
        FlightDestinationType.Other -> FlightIcons.Place
    }
