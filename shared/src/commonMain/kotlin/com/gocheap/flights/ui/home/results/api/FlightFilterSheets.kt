package com.gocheap.flights.ui.home.results.api

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.gocheap.flights.analytics.ga4.AnalyticsEvent
import com.gocheap.flights.analytics.ga4.TrackSheetView
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gocheap.flights.data.model.flight.api.FlightAgency
import com.gocheap.flights.data.model.flight.api.FlightAirline
import com.gocheap.flights.data.model.flight.api.FlightSearchFilters
import com.gocheap.flights.data.model.flight.api.FlightSort
import com.gocheap.flights.data.model.flight.api.LegTimeWindow
import com.gocheap.flights.data.model.flight.api.TimeWindow
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.PrimaryButton
import com.gocheap.flights.ui.component.SecondaryButton
import com.gocheap.flights.ui.component.pressable
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightSpacing
import kotlin.math.roundToInt
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.airlines
import flighttp.shared.generated.resources.all
import flighttp.shared.generated.resources.arrival
import flighttp.shared.generated.resources.booking_sites
import flighttp.shared.generated.resources.clear
import flighttp.shared.generated.resources.departure
import flighttp.shared.generated.resources.done
import flighttp.shared.generated.resources.duration
import flighttp.shared.generated.resources.durations_are_still_arriving_try_again_in
import flighttp.shared.generated.resources.price
import flighttp.shared.generated.resources.prices_are_still_arriving_try_again_in
import flighttp.shared.generated.resources.sort_by
import flighttp.shared.generated.resources.stops
import flighttp.shared.generated.resources.times
import flighttp.shared.generated.resources.outbound
import flighttp.shared.generated.resources.return_label_2
import flighttp.shared.generated.resources.up_to_duration
import flighttp.shared.generated.resources.direct
import flighttp.shared.generated.resources.one_stop_filter
import flighttp.shared.generated.resources.two_plus_stops

/**
 * The sheets behind the filter bar.
 *
 * All seven share one shell — [FilterSheet] — rather than each bringing its own, so they open the
 * same way, sit at the same corner radius and put their footer in the same place.
 *
 * Each sheet edits a working copy and applies it on dismissal or on "Show results", never on
 * every tap. A filter change restarts polling from page one, so applying per tap would mean a
 * four-airline selection throwing away three searches on its way to the fourth.
 */
@Composable
internal fun FlightFilterSheetHost(
    sheet: FlightFilterSheet,
    filters: FlightSearchFilters,
    airlines: List<FlightAirline>,
    agencies: List<FlightAgency>,
    priceBounds: ClosedFloatingPointRange<Float>?,
    durationBounds: IntRange?,
    legCount: Int,
    onApply: (FlightSearchFilters) -> Unit,
    /**
     * For the controls that move continuously. A slider passes through dozens of values on its way
     * to one, and running a search at each would be thirty searches to answer one question — so
     * these are collected and run once the drag settles.
     */
    onApplyDebounced: (FlightSearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        FlightFilterSheet.Sort -> SortSheet(filters, onApply, onDismiss)
        FlightFilterSheet.Stops -> StopsSheet(filters, onApply, onDismiss)
        FlightFilterSheet.Times -> TimesSheet(filters, legCount, onApplyDebounced, onApply, onDismiss)
        FlightFilterSheet.Airlines -> AirlinesSheet(filters, airlines, onApply, onDismiss)
        FlightFilterSheet.Agencies -> AgenciesSheet(filters, agencies, onApply, onDismiss)
        FlightFilterSheet.Price -> PriceSheet(filters, priceBounds, onApplyDebounced, onApply, onDismiss)
        FlightFilterSheet.Duration -> DurationSheet(filters, durationBounds, onApplyDebounced, onApply, onDismiss)
    }
}

/**
 * The shell: a grab handle, a title with a "Clear" beside it, the sheet's own body, and a footer
 * button that closes it.
 *
 * Height-capped rather than free: an airline list can run to sixty rows, and a sheet that reaches
 * the status bar has no visible edge left to drag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    title: String,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    // All seven filter and sort sheets come through here, and all seven report under the one name:
    // [title] is translated, and keying the measurement on it would split a single sheet into a
    // row per language. Which filter was opened is not what the dashboard is asked for.
    TrackSheetView(AnalyticsEvent.FILTER_SCREEN)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FlightColors.Surface,
        shape = FlightShapes.Sheet,
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(FlightShapes.Pill)
                        .background(FlightColors.BorderStrong),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlightSpacing.CardPadding)
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = FlightColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(16.dp))
            // Clear and Done, as the reference app pairs them. Both are shortcuts rather than
            // commits: every control in these sheets has already applied itself by the time
            // either is pressed — see the file comment.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onClear != null) {
                    SecondaryButton(
                        text = stringResource(Res.string.clear),
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    text = stringResource(Res.string.done),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SortSheet(
    filters: FlightSearchFilters,
    onApply: (FlightSearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    FilterSheet(title = stringResource(Res.string.sort_by), onClear = null, onDismiss = onDismiss) {
        Column {
            FlightSort.entries.forEach { sort ->
                CheckRow(
                    label = sort.label,
                    isChecked = filters.sort == sort,
                    // A single choice, so the tick is round — the shape is what says these are
                    // exclusive and the multi-select sheets' squares are not.
                    isExclusive = true,
                    onToggle = {
                        onApply(filters.copy(sort = sort))
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun StopsSheet(
    filters: FlightSearchFilters,
    onApply: (FlightSearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    // Empty means "all of them", exactly as the airline and agency sets do — so an untouched
    // sheet draws every row ticked while sending no `stop_counts` at all.
    val all = StopChoices.map { it.first }

    FilterSheet(
        title = stringResource(Res.string.stops),
        onClear = { onApply(filters.copy(stopCounts = emptySet())) },
        onDismiss = onDismiss,
    ) {
        Column {
            SelectAllRow(
                isAllSelected = filters.stopCounts.isEmpty(),
                onSelectAll = { onApply(filters.copy(stopCounts = emptySet())) },
            )
            StopChoices.forEach { (count, label) ->
                CheckRow(
                    label = stringResource(label),
                    isChecked = filters.stopCounts.isEmpty() || count in filters.stopCounts,
                    onToggle = {
                        onApply(filters.copy(stopCounts = filters.stopCounts.toggleIn(count, all)))
                    },
                )
            }
        }
    }
}

/**
 * Departure and arrival windows, one pair per leg.
 *
 * Both ends of a leg get a slider because the two restrict different things: when the traveller
 * has to leave for the airport, and when they land. A single "time of day" control would collapse
 * the two and answer neither.
 */
@Composable
private fun TimesSheet(
    filters: FlightSearchFilters,
    legCount: Int,
    onApplyDebounced: (FlightSearchFilters) -> Unit,
    onApply: (FlightSearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    // Padded out to the trip's leg count: the stored list is empty until something is restricted,
    // and the sliders need a window each to read from either way.
    val windows = List(legCount.coerceAtLeast(1)) {
        filters.timeRanges.getOrNull(it) ?: LegTimeWindow()
    }

    /** Drops the list entirely once nothing is restricted, so the chip does not stay lit. */
    fun publish(updated: List<LegTimeWindow>, apply: (FlightSearchFilters) -> Unit) {
        apply(filters.copy(timeRanges = updated.takeIf { list -> list.any { !it.isUnrestricted } }.orEmpty()))
    }

    FilterSheet(
        title = stringResource(Res.string.times),
        onClear = { onApply(filters.copy(timeRanges = emptyList())) },
        onDismiss = onDismiss,
    ) {
        Column {
            windows.forEachIndexed { index, window ->
                if (index > 0) Spacer(Modifier.height(18.dp))
                Text(
                    text = if (index == 0) stringResource(Res.string.outbound) else stringResource(Res.string.return_label_2),
                    style = MaterialTheme.typography.titleSmall,
                    color = FlightColors.TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                TimeWindowSlider(
                    label = stringResource(Res.string.departure),
                    window = window.departure,
                    onChange = { updated ->
                        publish(windows.replaceAt(index) { it.copy(departure = updated) }, onApplyDebounced)
                    },
                )
                Spacer(Modifier.height(10.dp))
                TimeWindowSlider(
                    label = stringResource(Res.string.arrival),
                    window = window.arrival,
                    onChange = { updated ->
                        publish(windows.replaceAt(index) { it.copy(arrival = updated) }, onApplyDebounced)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeWindowSlider(
    label: String,
    window: TimeWindow,
    onChange: (TimeWindow) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = FlightColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${formatTimeOfDay(window.min)} – ${formatTimeOfDay(window.max)}",
                style = MaterialTheme.typography.labelMedium,
                color = FlightColors.TextPrimary,
            )
        }
        RangeSlider(
            value = window.min.toFloat()..window.max.toFloat(),
            onValueChange = { range ->
                onChange(
                    TimeWindow(
                        // Snapped to the quarter hour: the track is a day wide on a phone, so a
                        // free slider lands on "07:43" and reads as an accident.
                        min = range.start.toLong().snapTo(QuarterHourSeconds),
                        max = range.endInclusive.toLong().snapTo(QuarterHourSeconds),
                    ),
                )
            },
            valueRange = 0f..TimeWindow.SecondsPerDay.toFloat(),
            colors = sliderColors(),
            startThumb = { RoundSliderThumb() },
            endThumb = { RoundSliderThumb() },
            track = { RoundSliderTrack(it) },
        )
    }
}

@Composable
private fun AirlinesSheet(
    filters: FlightSearchFilters,
    airlines: List<FlightAirline>,
    onApply: (FlightSearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val all = airlines.mapNotNull { it.iata }
    val selected = filters.airlinesInclude

    FilterSheet(
        title = stringResource(Res.string.airlines),
        onClear = { onApply(filters.copy(airlinesInclude = emptySet())) },
        onDismiss = onDismiss,
    ) {
        // "All" is the unfiltered state, which is an empty set rather than every code — see the
        // filter model. The two mean the same thing to the user and different things to the
        // endpoint.
        SelectAllRow(
            isAllSelected = selected.isEmpty(),
            onSelectAll = { onApply(filters.copy(airlinesInclude = emptySet())) },
        )
        LazyColumn(modifier = Modifier.heightIn(max = ListMaxHeight)) {
            items(airlines, key = { it.iata.orEmpty() }) { airline ->
                val code = airline.iata.orEmpty()
                CheckRow(
                    label = airline.name?.takeIf(String::isNotBlank) ?: code,
                    isChecked = selected.isEmpty() || code in selected,
                    logoUrl = airline.logoUrl,
                    onToggle = {
                        onApply(filters.copy(airlinesInclude = selected.toggleIn(code, all)))
                    },
                )
            }
        }
    }
}

@Composable
private fun AgenciesSheet(
    filters: FlightSearchFilters,
    agencies: List<FlightAgency>,
    onApply: (FlightSearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val all = agencies.mapNotNull { it.code }
    val selected = filters.agenciesInclude

    FilterSheet(
        title = stringResource(Res.string.booking_sites),
        onClear = { onApply(filters.copy(agenciesInclude = emptySet())) },
        onDismiss = onDismiss,
    ) {
        SelectAllRow(
            isAllSelected = selected.isEmpty(),
            onSelectAll = { onApply(filters.copy(agenciesInclude = emptySet())) },
        )
        LazyColumn(modifier = Modifier.heightIn(max = ListMaxHeight)) {
            items(agencies, key = { it.code.orEmpty() }) { agency ->
                val code = agency.code.orEmpty()
                CheckRow(
                    label = agency.name?.takeIf(String::isNotBlank) ?: code,
                    isChecked = selected.isEmpty() || code in selected,
                    logoUrl = agency.imageUrl,
                    onToggle = {
                        onApply(filters.copy(agenciesInclude = selected.toggleIn(code, all)))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceSheet(
    filters: FlightSearchFilters,
    bounds: ClosedFloatingPointRange<Float>?,
    onApplyDebounced: (FlightSearchFilters) -> Unit,
    onApply: (FlightSearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    // Nothing to drag until the backend has said what this result set's prices span.
    if (bounds == null) {
        FilterSheet(title = stringResource(Res.string.price), onClear = null, onDismiss = onDismiss) {
            Text(
                text = stringResource(Res.string.prices_are_still_arriving_try_again_in),
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.TextSecondary,
            )
        }
        return
    }

    val range = (filters.priceMin?.toFloat() ?: bounds.start)..
        (filters.priceMax?.toFloat() ?: bounds.endInclusive)

    FilterSheet(
        title = stringResource(Res.string.price),
        onClear = { onApply(filters.copy(priceMin = null, priceMax = null)) },
        onDismiss = onDismiss,
    ) {
        Column {
            Text(
                text = "${range.start.roundToInt()} – ${range.endInclusive.roundToInt()}",
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
            )
            RangeSlider(
                value = range,
                onValueChange = { picked ->
                    // Only sent where a handle has actually left the end: a bound equal to the
                    // result set's own limit filters nothing and would still light the chip up.
                    onApplyDebounced(
                        filters.copy(
                            priceMin = picked.start.takeIf { it > bounds.start }?.toDouble(),
                            priceMax = picked.endInclusive.takeIf { it < bounds.endInclusive }?.toDouble(),
                        ),
                    )
                },
                valueRange = bounds,
                colors = sliderColors(),
                startThumb = { RoundSliderThumb() },
                endThumb = { RoundSliderThumb() },
                track = { RoundSliderTrack(it) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationSheet(
    filters: FlightSearchFilters,
    bounds: IntRange?,
    onApplyDebounced: (FlightSearchFilters) -> Unit,
    onApply: (FlightSearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    if (bounds == null) {
        FilterSheet(title = stringResource(Res.string.duration), onClear = null, onDismiss = onDismiss) {
            Text(
                text = stringResource(Res.string.durations_are_still_arriving_try_again_in),
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.TextSecondary,
            )
        }
        return
    }

    // One handle, not two: a traveller filters out journeys that take too long, never ones that
    // are too quick. The reference app's sheet is the same shape.
    val maximum = (filters.durationMax ?: bounds.last).toFloat()

    FilterSheet(
        title = stringResource(Res.string.duration),
        onClear = { onApply(filters.copy(durationMax = null)) },
        onDismiss = onDismiss,
    ) {
        Column {
            Text(
                text = stringResource(Res.string.up_to_duration, durationLabel(maximum.roundToInt())),
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
            )
            Slider(
                value = maximum,
                onValueChange = { picked ->
                    onApplyDebounced(
                        filters.copy(durationMax = picked.roundToInt().takeIf { it < bounds.last }),
                    )
                },
                valueRange = bounds.first.toFloat()..bounds.last.toFloat(),
                colors = sliderColors(),
                thumb = { RoundSliderThumb() },
                track = { RoundSliderTrack(it) },
            )
        }
    }
}

// --- shared rows -----------------------------------------------------------------------------

/**
 * One selectable row: a tick box, an optional logo, and a label.
 *
 * [isExclusive] rounds the box, which is the only thing distinguishing a sort choice from a
 * filter choice at a glance.
 */
@Composable
private fun CheckRow(
    label: String,
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isExclusive: Boolean = false,
    logoUrl: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onToggle, pressedScale = 1f)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(21.dp)
                .clip(if (isExclusive) CircleShape else RoundedCornerShape(6.dp))
                .background(if (isChecked) FlightColors.Primary else FlightColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            if (isChecked) {
                Icon(
                    imageVector = FlightIcons.Check,
                    contentDescription = null,
                    tint = FlightColors.OnPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = FlightColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SelectAllRow(isAllSelected: Boolean, onSelectAll: () -> Unit) {
    Column {
        CheckRow(label = stringResource(Res.string.all), isChecked = isAllSelected, onToggle = onSelectAll)
        Box(Modifier.fillMaxWidth().height(1.dp).background(FlightColors.Divider))
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = FlightColors.Primary,
    activeTrackColor = FlightColors.Primary,
    inactiveTrackColor = FlightColors.Border,
)

/**
 * A round handle, in place of the tall bar Material 3 draws by default.
 *
 * The expressive Slider's own thumb is a narrow vertical pill. It reads as part of the track
 * rather than as something to pick up, and nothing else in this app is shaped like it — the swap
 * control on the Modify Search sheet is the nearest relative, and it is a white disc with a
 * coloured ring on a soft shadow. This is the same object at slider size, so the two read as one
 * family of handles.
 *
 * White fill rather than solid blue: the thumb sits *on* the active blue track, and a blue disc
 * on a blue bar loses its edge exactly where the user needs to see it.
 *
 * The ring is a grey hairline rather than a second blue. Against the active track the white fill
 * is already the contrast; what the ring is for is the other half of the journey, holding an edge
 * once the handle has slid off the blue and onto the white sheet behind it.
 *
 * **No elevation.** A drop shadow at this size does not resolve into a circle — the falloff is
 * wider than the 22dp disc casting it, so it reads as a soft grey box sitting behind a round
 * thumb. The ring already separates the thumb from both the blue track and the white sheet, which
 * is the whole job the shadow would have been doing.
 *
 * Drawn rather than carried as an asset. The spec for this handle is a 22dp vector — a circle of
 * radius 10.25 stroked 1.5 — and an inset border on a 22dp box is the same circle: the stroke
 * spans radius 9.5 to 11.0 either way. Keeping it as a shape also keeps it on both platforms,
 * which an Android vector drawable in a module compiled for iOS as well would not do.
 */
@Composable
private fun RoundSliderThumb() {
    Box(
        Modifier
            .size(ThumbDiameter)
            .clip(CircleShape)
            .background(FlightColors.Surface)
            .border(ThumbRing, FlightColors.SliderThumbRing, CircleShape),
    )
}

/** Big enough to be dragged with a thumb without covering the track it is being read against. */
private val ThumbDiameter = 22.dp
private val ThumbRing = 1.5.dp

/**
 * The track, with the notch Material 3 cuts around the thumb closed up.
 *
 * By default the bar stops short of the handle and starts again past it, and each stub is capped
 * with a 2dp corner on a bar several times that tall. At the size this slider draws, that stub
 * reads as a small square block sitting against the round handle rather than as the end of a line
 * — which is the rectangle that appeared beside the thumb.
 *
 * A gap of zero removes the stubs rather than rounding them: there is then one continuous bar
 * running under the handle, with the active and inactive halves meeting beneath it. That is what
 * the thumb was drawn for — a white disc that holds its own edge needs nothing cut out behind it.
 *
 * The bar is also taken down to [TrackThickness] from the 16dp Material defaults to. At the
 * default it stands nearly as tall as the 22dp handle, so the two read as one slab with a notch
 * in it rather than as a handle sitting on a line. Set through the modifier rather than a
 * parameter because Material exposes no height on this slot — the track applies its own
 * `height(16.dp)` inside, and an outer height wins by constraining what that call is measured
 * against. Its end caps follow: the corner radius is derived from the measured height, so a
 * thinner bar stays fully rounded without being told to.
 *
 * Two overloads because Material models a one-handle and a two-handle slider as separate states,
 * and the track slot is typed to whichever it belongs to.
 */
@Composable
private fun RoundSliderTrack(state: SliderState) {
    SliderDefaults.Track(
        sliderState = state,
        modifier = Modifier.height(TrackThickness),
        colors = sliderColors(),
        thumbTrackGapSize = 0.dp,
    )
}

@Composable
private fun RoundSliderTrack(state: RangeSliderState) {
    SliderDefaults.Track(
        rangeSliderState = state,
        modifier = Modifier.height(TrackThickness),
        colors = sliderColors(),
        thumbTrackGapSize = 0.dp,
    )
}

/** Thin enough that the handle reads as sitting *on* the track rather than being part of it. */
private val TrackThickness = 6.dp

// --- helpers ---------------------------------------------------------------------------------

/**
 * The three the reference app offers, keyed by the stop count the backend filters on.
 *
 * Two is the top of the list and means "two or more": the endpoint's `stop_counts` takes exact
 * counts, and the reference sends the index of the checked row — so a three-stop itinerary is
 * reached through the same value a two-stop one is.
 */
private val StopChoices = listOf(
    0 to Res.string.direct,
    1 to Res.string.one_stop_filter,
    2 to Res.string.two_plus_stops,
)

private const val QuarterHourSeconds = 900L

private val ListMaxHeight = 320.dp

private fun Long.snapTo(step: Long): Long = (this + step / 2) / step * step

private fun <T> List<T>.replaceAt(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, value -> if (i == index) transform(value) else value }

/**
 * Toggles one code within a multi-select whose empty state means "all of them".
 *
 * The awkward case is the first tap: an empty set is drawn with every row ticked, so unticking one
 * has to become "every code except this one" rather than "only this one". And the last tap back
 * the other way — selecting every code individually — collapses to empty, because a set naming
 * all of them filters nothing and the chip should not claim it does.
 */
private fun <T> Set<T>.toggleIn(value: T, all: List<T>): Set<T> {
    if (value is String && value.isBlank()) return this
    if (all.isEmpty()) return this
    val explicit = ifEmpty { all.toSet() }
    val next = if (value in explicit) explicit - value else explicit + value
    return if (next.size == all.size) emptySet() else next
}
