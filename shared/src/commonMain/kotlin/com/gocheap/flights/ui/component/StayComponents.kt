package com.gocheap.flights.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gocheap.flights.data.model.Car
import com.gocheap.flights.data.model.Hotel
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.theme.FlightShapes
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.back
import flighttp.shared.generated.resources.per_day
import flighttp.shared.generated.resources.per_night
import flighttp.shared.generated.resources.reviews_count
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Single-value field — destination, pick-up point, party size — with its glyph leading it.
 *
 * A null [icon] leaves the leading edge bare, for the cards that read cleaner without it.
 */
@Composable
fun IconValueField(
    label: String,
    icon: ImageVector?,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    /**
     * Shown in place of a blank [value], greyed, as an invitation to fill the field rather than a
     * statement about where the trip is — which is what a prefilled city would be.
     */
    placeholder: String = "",
) {
    val isEmpty = value.isBlank()
    LabeledFieldBox(
        label = label,
        modifier = modifier,
        onClick = onClick,
        showDivider = showDivider,
        leading = if (icon != null) {
            { FieldLeadingIcon(icon) }
        } else {
            null
        },
    ) {
        Text(
            text = if (isEmpty) placeholder else value,
            style = MaterialTheme.typography.headlineLarge,
            color = if (isEmpty) FlightColors.TextTertiary else FlightColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Narrow date field sized to sit two-across in a row, the calendar glyph leading it.
 *
 * [showCalendarIcon] drops that glyph for the cards that read cleaner without it.
 */
@Composable
fun CompactDateField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    showCalendarIcon: Boolean = true,
    caption: String? = null,
) {
    LabeledFieldBox(
        label = label,
        modifier = modifier,
        onClick = onClick,
        showDivider = showDivider,
        leading = if (showCalendarIcon) {
            { FieldLeadingIcon(FlightIcons.CalendarOutlined) }
        } else {
            null
        },
    ) {
        // A step down from the single-value fields: two of these share a row, so a long date has
        // half the card's width to fit into.
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = FlightColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The caption sits under the date rather than beside it — at half the card's width there
        // is no room for both on one line.
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small rounded square standing in for a property or vehicle photo. */
@Composable
fun ThumbnailTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(FlightShapes.Field)
            .background(FlightColors.PrimarySoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FlightColors.Primary,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/** Review score badge, e.g. "9.2". */
@Composable
fun ScoreBadge(
    score: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(FlightShapes.Field)
            .background(FlightColors.Primary)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = score,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

@Composable
fun StarRow(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(count) {
            Icon(
                imageVector = FlightIcons.Star,
                contentDescription = null,
                tint = FlightColors.Warning,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** Hotel row used on the Hotels tab and the hotel results list. */
@Composable
fun HotelCard(
    hotel: Hotel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Card)
            .border(1.dp, FlightColors.Border, FlightShapes.Card)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row {
            ThumbnailTile(icon = FlightIcons.HotelFilled)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hotel.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = FlightColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                StarRow(count = hotel.starRating)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = FlightIcons.Place,
                        contentDescription = null,
                        tint = FlightColors.TextTertiary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = hotel.distanceFromCentreKm,
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreBadge(score = hotel.reviewScore)
            Spacer(Modifier.width(8.dp))
            Text(
                text = pluralStringResource(Res.plurals.reviews_count, hotel.reviewCount, hotel.reviewCount),
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${hotel.pricePerNightUsd}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = FlightColors.Primary,
                )
                Text(
                    text = stringResource(Res.string.per_night),
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                )
            }
        }
    }
}

/** Car row used on the Cars tab and the car results list. */
@Composable
fun CarCard(
    car: Car,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Card)
            .border(1.dp, FlightColors.Border, FlightShapes.Card)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThumbnailTile(icon = FlightIcons.CarFilled)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = car.model,
                    style = MaterialTheme.typography.titleLarge,
                    color = FlightColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${car.category} · ${car.supplier}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ScoreBadge(score = car.reviewScore)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InfoChip(text = "${car.seats}", icon = FlightIcons.Seat)
            InfoChip(text = "${car.bags}", icon = FlightIcons.Luggage)
            InfoChip(
                text = car.transmission,
                containerColor = FlightColors.SurfaceMuted,
                contentColor = FlightColors.TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${car.pricePerDayUsd}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = FlightColors.Primary,
                )
                Text(
                    text = stringResource(Res.string.per_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                )
            }
        }
    }
}

/** Blue header band shared by the hotel and car results screens. */
@Composable
fun ResultsHeaderBand(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FlightColors.Primary)
            .padding(horizontal = FlightSpacing.ScreenHorizontal)
            .padding(top = 12.dp, bottom = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = FlightIcons.ArrowBack,
            contentDescription = stringResource(Res.string.back),
            onClick = onBack,
            tint = Color.White,
            containerColor = Color.Transparent,
            borderColor = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
