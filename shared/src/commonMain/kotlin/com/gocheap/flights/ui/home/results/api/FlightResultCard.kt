package com.gocheap.flights.ui.home.results.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gocheap.flights.data.model.flight.api.CurrencyInfo
import com.gocheap.flights.data.model.flight.api.FlightLeg
import com.gocheap.flights.data.model.flight.api.FlightOption
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.pressable
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightSpacing
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.view_deals
import flighttp.shared.generated.resources.best
import flighttp.shared.generated.resources.cheapest
import flighttp.shared.generated.resources.fastest

/**
 * One itinerary, as this app draws it.
 *
 * The reference app's row carries the same facts; the arrangement is this app's own — a white
 * card on the pale blue page, lifted on the navy shadow every other card here casts, with the
 * route drawn as the dotted path the home screen already uses for a flight.
 *
 * A return trip is one card with two legs stacked inside it, not two cards. The backend prices
 * the journey as a whole and the cheapest outbound paired with the cheapest return is frequently
 * not bookable, so splitting them would offer a fare that does not exist.
 */
@Composable
internal fun FlightResultCard(
    option: FlightOption,
    currency: CurrencyInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .shadow(
                elevation = CardElevation,
                shape = FlightShapes.Card,
                spotColor = FlightColors.CardShadow,
            )
            .clip(FlightShapes.Card)
            .background(FlightColors.Surface)
            .padding(FlightSpacing.CardPadding),
    ) {
        val badges = option.badges()
        if (badges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEach { ResultBadge(it) }
            }
            Spacer(Modifier.height(12.dp))
        }

        option.legs.orEmpty().forEachIndexed { index, leg ->
            if (index > 0) {
                Spacer(Modifier.height(12.dp))
                // A hairline between the legs rather than a gap alone: the two rows are the same
                // shape, and without a rule the return reads as a second itinerary.
                Box(
                    Modifier.fillMaxWidth().height(1.dp).background(FlightColors.Divider),
                )
                Spacer(Modifier.height(12.dp))
            }
            FlightLegRow(leg = leg)
        }

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(FlightColors.Divider))
        Spacer(Modifier.height(12.dp))

        FlightPriceRow(option = option, currency = currency)
    }
}

/**
 * One hop: the airline's mark, the two clock times with their codes beneath, the dotted path
 * between them carrying the duration and the stop count.
 */
@Composable
private fun FlightLegRow(leg: FlightLeg, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AirlineMark(
            logoUrl = leg.segments?.firstOrNull()?.airlineLogo,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(12.dp))

        TimeAndPlace(
            time = formatClockTime(leg.departureTimeAirport),
            code = leg.originCode.orEmpty(),
            alignment = Alignment.Start,
        )

        LegPath(
            duration = durationLabel(leg.duration),
            stops = stopsLabel(leg.stopCount),
            isDirect = leg.stopCount == 0,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )

        TimeAndPlace(
            time = formatClockTime(leg.arriveTimeAirport),
            code = leg.destinationCode.orEmpty(),
            alignment = Alignment.End,
            // "+1" belongs to the arrival and nowhere else — it is what tells a late-night
            // departure apart from one that lands the following morning.
            suffix = dayOffsetLabel(leg.segments?.lastOrNull()?.arrivalDayDifference),
        )
    }
}

@Composable
private fun TimeAndPlace(
    time: String,
    code: String,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = time,
                style = MaterialTheme.typography.headlineSmall,
                color = FlightColors.TextPrimary,
            )
            if (suffix.isNotEmpty()) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = FlightColors.Primary,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
        Text(
            text = code,
            style = MaterialTheme.typography.labelMedium,
            color = FlightColors.TextSecondary,
        )
    }
}

/**
 * The line between the two ends: duration above, a dotted rule with a dot at each end, and the
 * stop count below.
 *
 * A direct flight says so in green. Everything else is grey — a stop is not a warning, it is just
 * the thing the traveller is trading for the fare.
 */
@Composable
private fun LegPath(
    duration: String,
    stops: String,
    isDirect: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = duration,
            style = MaterialTheme.typography.labelSmall,
            color = FlightColors.TextSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PathDot()
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(FlightColors.BorderStrong),
            )
            PathDot()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stops,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDirect) FlightColors.Success else FlightColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PathDot() {
    Box(
        Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(FlightColors.Primary),
    )
}

/** The fare, who is selling it, and the chevron into the deals. */
@Composable
private fun FlightPriceRow(
    option: FlightOption,
    currency: CurrencyInfo?,
    modifier: Modifier = Modifier,
) {
    val cheapest = option.providers
        ?.minByOrNull { it.price ?: Double.MAX_VALUE }
        ?.deals
        ?.firstOrNull()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatPriceCompact(option.displayPrice, currency),
                style = MaterialTheme.typography.headlineMedium,
                color = FlightColors.TextPrimary,
            )
            cheapest?.name?.takeIf(String::isNotBlank)?.let { seller ->
                Text(
                    text = seller,
                    style = MaterialTheme.typography.labelSmall,
                    color = FlightColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(FlightShapes.Pill)
                .background(FlightColors.PrimarySoft)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // The reference app's wording for this control, give or take the plural: it opens
                // the list of sellers for this itinerary, so it names what is on the other side of
                // the tap rather than what the tap does to the row.
                text = stringResource(Res.string.view_deals),
                style = MaterialTheme.typography.labelMedium,
                color = FlightColors.Primary,
            )
            Icon(
                imageVector = FlightIcons.ChevronRight,
                contentDescription = null,
                tint = FlightColors.Primary,
                modifier = Modifier.size(16.dp).padding(start = 2.dp),
            )
        }
    }
}

/**
 * The airline's logo, or a plane on a tinted square where there is none.
 *
 * A placeholder rather than an empty box: the logos load one network round-trip behind the row
 * they sit in, and a hole that fills a moment later makes a settled list look like it is still
 * arriving.
 */
@Composable
private fun AirlineMark(logoUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FlightColors.SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = FlightIcons.FlightOutlined,
            contentDescription = null,
            tint = FlightColors.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** "Best", "Cheapest", "Fastest" — whichever of the three this itinerary is. */
@Composable
private fun ResultBadge(badge: ResultBadgeKind, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(badge.label),
        style = MaterialTheme.typography.labelSmall,
        color = badge.foreground,
        modifier = modifier
            .clip(FlightShapes.Pill)
            .background(badge.background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The three the backend flags, each in its own colour so they are told apart at a glance rather
 * than read.
 */
private enum class ResultBadgeKind(
    /** The resource, not the text: an enum entry is built before any composition exists. */
    val label: StringResource,
    val foreground: Color,
    val background: Color,
) {
    Best(Res.string.best, FlightColors.Primary, FlightColors.PrimarySoft),
    Cheapest(Res.string.cheapest, FlightColors.Success, SuccessSoft),
    Fastest(Res.string.fastest, FlightColors.Warning, WarningSoft),
}

/** Tints of the two status colours, which the palette has no soft variants of. */
private val SuccessSoft = Color(0xFFE4F7ED)
private val WarningSoft = Color(0xFFFEF2E2)

/**
 * Which badges an itinerary wears. In a fixed order, so a result that is both best and cheapest
 * does not reorder its own badges between polls.
 */
private fun FlightOption.badges(): List<ResultBadgeKind> = buildList {
    if (isBest == true) add(ResultBadgeKind.Best)
    if (isCheapest == true) add(ResultBadgeKind.Cheapest)
    if (isFastest == true) add(ResultBadgeKind.Fastest)
}

/** Matched to the other cards on the page. */
private val CardElevation = 8.dp
