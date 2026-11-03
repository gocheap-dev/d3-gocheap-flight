package com.gocheap.flights.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gocheap.flights.data.model.Flight
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightSpacing
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.back

/**
 * The panel the search form sits on, floated over the hero: solid white, raised on a shadow.
 *
 * The fill is deliberately opaque. It used to wash from a translucent tint at the top down to
 * white, which looked like frosted glass in a mockup but not on a device: an elevated shape casts
 * its shadow *behind* itself, so wherever the fill was see-through the card's own shadow showed
 * through the panel and read as a grey band across the top third — on Android and iOS alike. A
 * partly transparent surface and an elevation shadow cannot both be had; the shadow is what lifts
 * the card off the hero, so the transparency is what goes.
 *
 * No border either. Against a white fill the old white-to-transparent rim had nothing to catch,
 * and only put a seam along the top edge.
 *
 * It is raised on the same shadow as the recent-search and popular-location cards below it —
 * `FlightColors.CardShadow`, cast by the platform — so the three read as one family of cards on
 * one page rather than as a hero with a treatment of its own.
 */
fun Modifier.glassCard(shape: Shape = FlightShapes.Sheet): Modifier = this
    .shadow(elevation = GlassCardElevation, shape = shape, spotColor = FlightColors.CardShadow)
    .clip(shape)
    .background(FlightColors.Surface)

/** Matched to the recent-search card's lift, which is the taller of the two rows beneath it. */
private val GlassCardElevation = 10.dp

/**
 * Full-width action button: the reference draws it as a near-black pill with bold white copy.
 *
 * Laid out here rather than with Material's `Button`, which draws a ripple its API gives no way to
 * turn off — the press is carried by [pressable]'s dip instead. The colours are the ones
 * `ButtonDefaults` was being handed, kept as they were.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    // A button mid-request is not pressable, the same as a disabled one: the tap has already been
    // taken and the spinner is the acknowledgement.
    val active = enabled && !loading

    Box(
        modifier = modifier
            .pressable(onClick = onClick, enabled = active)
            .fillMaxWidth()
            .height(56.dp)
            .clip(FlightShapes.Button)
            .background(
                if (active) FlightColors.Action else FlightColors.Action.copy(alpha = 0.35f),
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = FlightColors.OnAction,
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = if (active) {
                    FlightColors.OnAction
                } else {
                    FlightColors.OnAction.copy(alpha = 0.8f)
                },
            )
        }
    }
}

/** Secondary, outlined variant for destructive or low-emphasis actions. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .pressable(onClick = onClick)
            .fillMaxWidth()
            .height(56.dp)
            .clip(FlightShapes.Button)
            .border(1.5.dp, FlightColors.BorderStrong, FlightShapes.Button),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = FlightColors.TextPrimary,
        )
    }
}

/** Section title with an optional trailing action, used above every list in the app. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = FlightColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = FlightColors.TextSecondary,
                modifier = Modifier
                    .clip(FlightShapes.Pill)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** Back arrow plus centred title, used on every pushed destination. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = FlightColors.TextPrimary,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // On a coloured header the button is the outline alone; on a pale one it is the white
        // disc the reference draws, which needs no outline at all.
        val onColouredHeader = contentColor == Color.White
        CircleIconButton(
            icon = FlightIcons.ArrowBack,
            contentDescription = stringResource(Res.string.back),
            onClick = onBack,
            tint = contentColor,
            containerColor = if (onColouredHeader) Color.Transparent else FlightColors.Surface,
            borderColor = if (onColouredHeader) {
                Color.White.copy(alpha = 0.6f)
            } else {
                FlightColors.Border
            },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            action?.invoke()
        }
    }
}

/** The rounded-square outlined icon button, e.g. the bell in the home hero. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FlightColors.TextPrimary,
    borderColor: Color = FlightColors.Border,
    containerColor: Color = FlightColors.Surface,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .pressable(onClick = onClick)
            .size(42.dp)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Dot — dashed rule — plane — dashed rule — dot. The plane sits at the midpoint, nose to the right.
 */
@Composable
fun DashedFlightPath(
    modifier: Modifier = Modifier,
    color: Color = FlightColors.Primary,
    lineColor: Color = FlightColors.BorderStrong,
) {
    Row(
        modifier = modifier.height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteDot(color)
        DashedLine(modifier = Modifier.weight(1f), color = lineColor)
        Icon(
            imageVector = FlightIcons.Flight,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp).rotate(90f),
        )
        DashedLine(modifier = Modifier.weight(1f), color = lineColor)
        RouteDot(color)
    }
}

@Composable
private fun RouteDot(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun DashedLine(
    modifier: Modifier = Modifier,
    color: Color = FlightColors.BorderStrong,
) {
    Canvas(modifier = modifier.height(2.dp).padding(horizontal = 6.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)),
        )
    }
}

/**
 * The upcoming-flight card from the shot: codes on the shoulders, cities and duration beneath,
 * then departure/arrival times either side of the dashed path.
 */
@Composable
fun FlightRouteCard(
    flight: Flight,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Card)
            .border(1.dp, FlightColors.Border, FlightShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = flight.from.code,
                style = MaterialTheme.typography.headlineLarge,
                color = FlightColors.TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = flight.to.code,
                style = MaterialTheme.typography.headlineLarge,
                color = FlightColors.TextPrimary,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = flight.from.city,
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = flight.durationLabel,
                style = MaterialTheme.typography.labelMedium,
                color = FlightColors.Primary,
            )
            Text(
                text = flight.to.city,
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.TextSecondary,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = flight.departureTime,
                style = MaterialTheme.typography.headlineSmall,
                color = FlightColors.TextPrimary,
            )
            DashedFlightPath(modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(
                text = flight.arrivalTime,
                style = MaterialTheme.typography.headlineSmall,
                color = FlightColors.TextPrimary,
            )
        }
    }
}

/** Small blue-tinted chip used for cabin class, stops, and other metadata. */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = FlightColors.PrimarySoft,
    contentColor: Color = FlightColors.Primary,
) {
    Row(
        modifier = modifier
            .clip(FlightShapes.Pill)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

/** Label-over-value pair used in the detail and checkout summaries. */
@Composable
fun DetailStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FlightColors.TextSecondary,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = FlightColors.TextPrimary,
        )
    }
}

/** Centred empty state for the tickets and favourites tabs. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(FlightColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FlightColors.Primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = FlightColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = FlightColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Width-filling spacer that keeps content clear of the floating bottom bar.
 *
 * The bar is overlaid on the page rather than laid out above it, so a scrolling page has to
 * reserve the room itself. What it has to reserve is the bar's own height *plus* the navigation
 * inset the bar carries inside its white — and that inset is measured here rather than guessed,
 * because it is around 24dp under gesture navigation and around 48dp under three buttons. The
 * fixed figure this used to be cleared the gesture case and left the last row of the page cut off
 * behind the bar on every three-button device.
 */
@Composable
fun BottomBarSpacer() {
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Spacer(
        Modifier.height(
            FlightSpacing.BottomBarHeight + navigationInset + FlightSpacing.BottomBarGap,
        ),
    )
}
