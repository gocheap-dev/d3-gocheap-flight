package com.gocheap.travel.ui.home.results.api

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gocheap.travel.data.model.flight.api.FlightFilterType
import com.gocheap.travel.data.model.flight.api.FlightSearchFilters
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.pressable
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightShapes
import com.gocheap.travel.ui.theme.FlightSpacing
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.back
import flighttp.shared.generated.resources.change_search
import flighttp.shared.generated.resources.stops_filter
import flighttp.shared.generated.resources.times_filter
import flighttp.shared.generated.resources.duration_filter
import flighttp.shared.generated.resources.price_filter
import flighttp.shared.generated.resources.airlines_filter
import flighttp.shared.generated.resources.booking_sites_filter
import flighttp.shared.generated.resources.sort_by_value
import flighttp.shared.generated.resources.clear_named_filter

/**
 * The band across the top of the results: the route, and the filters applied to it.
 *
 * Drawn on the app's blue rather than on white, which is the arrangement the reference app uses
 * and the reason the chips inside are white-on-colour. It earns the colour here: the header is
 * the only part of this screen that does not scroll, and putting the trip and its filters on a
 * solid block separates "what was searched for" from "what was found" without a rule between them.
 *
 * Two rows: [FlightResultsToolbar] naming the trip and offering to change it, and
 * [FlightFilterChips] underneath.
 */
@Composable
internal fun FlightResultsHeader(
    originCode: String,
    destinationCode: String,
    subtitle: String,
    isRoundTrip: Boolean,
    filters: FlightSearchFilters,
    showFilters: Boolean,
    onBack: () -> Unit,
    onEditSearch: () -> Unit,
    onOpenSheet: (FlightFilterSheet) -> Unit,
    onClearFilter: (FlightFilterType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(FlightColors.SkyTop, FlightColors.SkyMid),
                ),
            )
            .statusBarsPadding(),
    ) {
        FlightResultsToolbar(
            originCode = originCode,
            destinationCode = destinationCode,
            subtitle = subtitle,
            isRoundTrip = isRoundTrip,
            onBack = onBack,
            onEditSearch = onEditSearch,
        )
        if (showFilters) {
            FlightFilterChips(
                filters = filters,
                onOpenSheet = onOpenSheet,
                onClearFilter = onClearFilter,
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Back, and the trip itself as a button.
 *
 * The whole centre block is the control, not just the pencil beside it: the codes and the dates
 * are what the user reads to decide they want to change something, so they are what should answer
 * the tap. The pencil is there to say the block is tappable at all.
 */
@Composable
private fun FlightResultsToolbar(
    originCode: String,
    destinationCode: String,
    subtitle: String,
    isRoundTrip: Boolean,
    onBack: () -> Unit,
    onEditSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .pressable(onClick = onBack)
                .clip(FlightShapes.Pill)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = FlightIcons.ArrowBack,
                contentDescription = stringResource(Res.string.back),
                tint = FlightColors.OnPrimary,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .pressable(onClick = onEditSearch)
                .clip(FlightShapes.Field)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = originCode,
                    style = MaterialTheme.typography.titleMedium,
                    color = FlightColors.OnPrimary,
                )
                Icon(
                    // The arrow carries the trip shape, which is why the subtitle does not repeat
                    // it: two-way for a return, one-way for a single leg.
                    imageVector = if (isRoundTrip) FlightIcons.SwapVert else FlightIcons.ChevronRight,
                    contentDescription = null,
                    tint = FlightColors.OnPrimary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = destinationCode,
                    style = MaterialTheme.typography.titleMedium,
                    color = FlightColors.OnPrimary,
                )
                Icon(
                    imageVector = FlightIcons.Edit,
                    contentDescription = stringResource(Res.string.change_search),
                    tint = FlightColors.OnPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    // Held back from the codes above it: this line is read once on arrival and
                    // then ignored, and at full white it competes with the route.
                    color = FlightColors.OnPrimary.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Balances the back button so the trip block is centred on the bar rather than on the
        // space left over beside it.
        Spacer(Modifier.width(38.dp))
    }
}

/**
 * The six filter groups as chips, on the header's blue.
 *
 * **Active groups sort to the front.** The reference app does this and it is worth keeping: the
 * row scrolls sideways, and a filter the user has set is the one they are most likely to want to
 * adjust or remove next — leaving it in fixed position would hide it off-screen behind five they
 * have not touched.
 *
 * An active chip fills in and grows a × that clears that group alone. Tapping the chip itself
 * still opens its sheet, so there is no state the × is the only way out of.
 */
@Composable
private fun FlightFilterChips(
    filters: FlightSearchFilters,
    onOpenSheet: (FlightFilterSheet) -> Unit,
    onClearFilter: (FlightFilterType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = FlightFilterType.entries.sortedByDescending(filters::isActive)

    LazyRow(
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp),
        contentPadding = PaddingValues(horizontal = FlightSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "sort") {
            HeaderChip(
                label = stringResource(Res.string.sort_by_value, filters.sort.label),
                isActive = filters.sort != FlightSearchFilters.Unfiltered.sort,
                onClick = { onOpenSheet(FlightFilterSheet.Sort) },
            )
        }
        itemsIndexed(ordered, key = { _, type -> type.name }) { _, type ->
            HeaderChip(
                label = stringResource(type.label),
                isActive = filters.isActive(type),
                onClick = { onOpenSheet(type.sheet) },
                onClear = { onClearFilter(type) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun HeaderChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    // Animated so a chip reordering to the front does not also snap colour mid-slide.
    val border by animateColorAsState(
        targetValue = if (isActive) FlightColors.OnPrimary else InactiveChipBorder,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "chipBorder",
    )
    val fill by animateColorAsState(
        targetValue = if (isActive) ActiveChipFill else Color.Transparent,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "chipFill",
    )

    Row(
        modifier = modifier
            .pressable(onClick = onClick)
            .border(0.5.dp, border, ChipShape)
            .background(fill, ChipShape)
            .clip(ChipShape)
            .padding(
                start = 10.dp,
                end = if (isActive && onClear != null) 6.dp else 10.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = FlightColors.OnPrimary,
            maxLines = 1,
        )
        if (isActive && onClear != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = FlightIcons.Close,
                contentDescription = stringResource(Res.string.clear_named_filter, label),
                tint = FlightColors.OnPrimary,
                modifier = Modifier
                    .size(14.dp)
                    .pressable(onClick = onClear, pressedScale = 1f),
            )
        }
    }
}

/** The reference app's chip shape and its two translucent whites, kept as they are. */
private val ChipShape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp)
private val InactiveChipBorder = Color(0x5EFFFFFF)
private val ActiveChipFill = Color(0x3DFFFFFF)

/**
 * The word on each chip.
 *
 * The resource, not the text — this is an extension property on an enum, reachable from outside a
 * composition. The chip row resolves it.
 */
internal val FlightFilterType.label: StringResource
    get() = when (this) {
        FlightFilterType.Stops -> Res.string.stops_filter
        FlightFilterType.Time -> Res.string.times_filter
        FlightFilterType.Duration -> Res.string.duration_filter
        FlightFilterType.Price -> Res.string.price_filter
        FlightFilterType.Airlines -> Res.string.airlines_filter
        FlightFilterType.Agencies -> Res.string.booking_sites_filter
    }

/** Which sheet a chip opens. */
internal val FlightFilterType.sheet: FlightFilterSheet
    get() = when (this) {
        FlightFilterType.Stops -> FlightFilterSheet.Stops
        FlightFilterType.Time -> FlightFilterSheet.Times
        FlightFilterType.Duration -> FlightFilterSheet.Duration
        FlightFilterType.Price -> FlightFilterSheet.Price
        FlightFilterType.Airlines -> FlightFilterSheet.Airlines
        FlightFilterType.Agencies -> FlightFilterSheet.Agencies
    }
