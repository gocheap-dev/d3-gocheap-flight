package com.gocheap.flights.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.gocheap.flights.data.model.Booking
import com.gocheap.flights.ui.theme.FlightColors
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.class_label
import flighttp.shared.generated.resources.date
import flighttp.shared.generated.resources.gate
import flighttp.shared.generated.resources.passenger
import flighttp.shared.generated.resources.seat
import flighttp.shared.generated.resources.terminal

/**
 * Rounded card with a semicircular bite taken out of each side, giving the torn-stub silhouette of
 * a physical boarding pass.
 *
 * [notchCenterY] is the distance in pixels from the card's top edge, measured from the laid-out
 * tear line so the bites always line up with the dashed rule. A non-positive value falls back to
 * 60% of the card height, which is only used on the first frame before measurement lands.
 */
class BoardingPassShape(
    private val notchCenterY: Float = 0f,
    private val cornerRadius: Dp = 20.dp,
    private val notchRadius: Dp = 14.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        with(density) {
            val corner = cornerRadius.toPx()
            val notch = notchRadius.toPx()
            val requestedY = if (notchCenterY > 0f) notchCenterY else size.height * 0.6f
            val notchY = requestedY.coerceIn(notch, (size.height - notch).coerceAtLeast(notch))

            val body = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        cornerRadius = CornerRadius(corner),
                    ),
                )
            }
            val notches = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = Offset(0f, notchY),
                        radius = notch,
                    ),
                )
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = Offset(size.width, notchY),
                        radius = notch,
                    ),
                )
            }

            return Outline.Generic(
                Path().apply { op(body, notches, PathOperation.Difference) },
            )
        }
    }
}

/** Full boarding pass used on the ticket screen. */
@Composable
fun BoardingPassCard(
    booking: Booking,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    var tearCenterY by remember { mutableStateOf(0f) }
    val shape = remember(tearCenterY) { BoardingPassShape(notchCenterY = tearCenterY) }
    val flight = booking.flight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 14.dp, shape = shape, spotColor = FlightColors.Primary.copy(alpha = 0.5f))
            .clip(shape)
            .background(FlightColors.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(FlightColors.PrimarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = FlightIcons.Flight,
                        contentDescription = null,
                        tint = FlightColors.Primary,
                        modifier = Modifier.size(20.dp).rotate(45f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = flight.airline,
                        style = MaterialTheme.typography.titleMedium,
                        color = FlightColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = flight.flightNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightColors.TextSecondary,
                    )
                }
                InfoChip(text = booking.status.name)
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = flight.from.code,
                        style = MaterialTheme.typography.displaySmall,
                        color = FlightColors.TextPrimary,
                    )
                    Text(
                        text = flight.departureTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlightColors.TextSecondary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DashedFlightPath(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = flight.durationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = FlightColors.Primary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = flight.to.code,
                        style = MaterialTheme.typography.displaySmall,
                        color = FlightColors.TextPrimary,
                    )
                    Text(
                        text = flight.arrivalTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlightColors.TextSecondary,
                    )
                }
            }
        }

        TearLine(onPositioned = { tearCenterY = it })

        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailStat(
                    label = stringResource(Res.string.passenger),
                    value = booking.passenger.fullName,
                    modifier = Modifier.weight(1.4f),
                )
                DetailStat(
                    label = stringResource(Res.string.seat),
                    value = booking.seatLabels.joinToString().ifBlank { "—" },
                    modifier = Modifier.weight(1f),
                )
                DetailStat(
                    label = stringResource(Res.string.gate),
                    value = flight.gate,
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.End,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                DetailStat(label = stringResource(Res.string.date), value = flight.date, modifier = Modifier.weight(1.4f))
                DetailStat(label = stringResource(Res.string.terminal), value = flight.terminal, modifier = Modifier.weight(1f))
                DetailStat(
                    label = stringResource(Res.string.class_label),
                    value = flight.cabinClass,
                    modifier = Modifier.weight(1f),
                    alignment = Alignment.End,
                )
            }
            Spacer(Modifier.height(20.dp))
            Barcode(seed = booking.reference, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                text = booking.reference,
                style = MaterialTheme.typography.labelMedium,
                color = FlightColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Dashed rule running between the two notches. Reports its own vertical centre relative to the
 * card so the enclosing shape can cut the notches at exactly that height.
 */
/**
 * The dashed rule across a ticket, reporting back where it landed so the card's side notches can
 * be cut level with it. Shared with the saved-trip cards on the search tabs.
 */
@Composable
internal fun TearLine(
    onPositioned: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .onGloballyPositioned { coordinates ->
                onPositioned(coordinates.positionInParent().y + coordinates.size.height / 2f)
            }
            .padding(horizontal = 24.dp),
    ) {
        drawLine(
            color = FlightColors.BorderStrong,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height.coerceAtLeast(2f),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        )
    }
}

/**
 * Decorative barcode. Bar widths are derived from the booking reference so the same ticket always
 * renders the same stripes.
 */
@Composable
private fun Barcode(
    seed: String,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.height(56.dp)) {
        val codes = seed.ifBlank { "FLIGHTTP" }.map { it.code }
        var x = 0f
        var index = 0
        while (x < size.width) {
            val code = codes[index % codes.size]
            val barWidth = 1.5f + (code % 4)
            val gap = 1.5f + ((code / 3) % 3)
            val short = (code % 7) == 0
            drawRect(
                color = FlightColors.TextPrimary,
                topLeft = Offset(x, if (short) size.height * 0.16f else 0f),
                size = Size(
                    width = barWidth.coerceAtMost(size.width - x),
                    height = if (short) size.height * 0.84f else size.height,
                ),
            )
            x += barWidth + gap
            index++
        }
    }
}

/** Condensed pass used in the My Tickets list. */
@Composable
fun BoardingPassSummary(
    booking: Booking,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var tearCenterY by remember { mutableStateOf(0f) }
    val shape = remember(tearCenterY) { BoardingPassShape(notchCenterY = tearCenterY) }
    val flight = booking.flight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape, spotColor = FlightColors.Primary.copy(alpha = 0.35f))
            .clip(shape)
            .background(FlightColors.Surface)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = flight.airline,
                    style = MaterialTheme.typography.titleSmall,
                    color = FlightColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                InfoChip(text = booking.status.name)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = flight.from.code,
                    style = MaterialTheme.typography.headlineMedium,
                    color = FlightColors.TextPrimary,
                )
                DashedFlightPath(modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                Text(
                    text = flight.to.code,
                    style = MaterialTheme.typography.headlineMedium,
                    color = FlightColors.TextPrimary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = flight.departureTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = flight.durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = FlightColors.Primary,
                )
                Text(
                    text = flight.arrivalTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        TearLine(onPositioned = { tearCenterY = it })

        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = flight.date,
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Seat ${booking.seatLabels.joinToString().ifBlank { "—" }}",
                style = MaterialTheme.typography.labelMedium,
                color = FlightColors.TextPrimary,
            )
        }
    }
}
