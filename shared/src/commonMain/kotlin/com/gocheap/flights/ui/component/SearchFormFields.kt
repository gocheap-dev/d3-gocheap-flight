package com.gocheap.flights.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.gocheap.flights.data.model.Airport
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightShapes
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.swap_origin_and_destination

/**
 * The field used throughout the booking form: its glyph at the leading edge, then a small grey
 * label with the value beneath it, and a hair rule closing the row off — the reference draws its
 * form as underlined rows rather than boxes, so the fields read as one stack inside the card
 * instead of five separate tiles.
 *
 * The glyph leads rather than trails because it says *what the field is*, and that is the thing
 * you want to find when scanning the card rather than after reading the value.
 *
 * [showDivider] is off for the last field in a stack, where the rule would sit right above the
 * action button with nothing between them.
 */
@Composable
fun LabeledFieldBox(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Clipped before the click so the ripple stays inside the row rather than running
                // out under the rule below it.
                .clip(FlightShapes.Field)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 4.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(FieldIconGap))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                )
                Spacer(Modifier.height(2.dp))
                content()
            }
        }
        if (showDivider) {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(thickness = 1.dp, color = FlightColors.Divider)
        }
    }
}

/**
 * The glyph at a field's leading edge. Sized and tinted to sit a step below the value it labels —
 * two date fields share a row, so anything larger starts eating the date.
 */
@Composable
fun FieldLeadingIcon(
    icon: ImageVector,
    tint: Color = FlightColors.TextSecondary,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(FieldIconSize),
    )
}

private val FieldIconSize = 20.dp
private val FieldIconGap = 10.dp

/**
 * One clock for a choice and whatever that choice adds or removes, so the tab and the field it
 * reveals read as a single movement rather than two things that happened to fire together.
 */
object SearchFieldMotion {
    const val DurationMillis = 260
    val Easing = FastOutSlowInEasing
}

/**
 * Two fields sharing a row with a hair rule between them, as the reference sets Departure beside
 * Amount.
 *
 * [secondVisible] collapses the second column to nothing and hands its width back to the first —
 * a one-way flight has no return date. [second] being null means there is no second column at all
 * and nothing to animate, which is the stay card's single date pair.
 *
 * The underline is drawn here rather than by the two fields, so it runs unbroken across the row
 * instead of stopping either side of the gutter.
 */
@Composable
fun SplitFieldRow(
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    secondVisible: Boolean = true,
    second: (@Composable () -> Unit)? = null,
    first: @Composable () -> Unit,
) {
    // Deliberately not delegated. Every reader below sits inside a measure or a layer lambda, so
    // the row re-lays out and repaints as the value moves without ever recomposing. Read it with
    // `by` instead and the fields inside get rebuilt sixty times a second, which is the difference
    // between this gliding and it stuttering.
    val expansion = animateFloatAsState(
        targetValue = if (second != null && secondVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = SearchFieldMotion.DurationMillis,
            easing = SearchFieldMotion.Easing,
        ),
        label = "splitFieldExpansion",
    )
    // Derived so it flips twice per toggle rather than every frame: dropping the spent column out
    // of the tree then costs two recompositions, not sixteen, and it leaves while invisible.
    // Dropping it matters beyond the frame budget — a column left composed at zero width is still
    // read out by a screen reader.
    val collapsed by remember { derivedStateOf { expansion.value <= 0f } }
    val ruleColor = FlightColors.Divider

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) { first() }
            if (second != null && !collapsed) {
                Box(
                    modifier = Modifier
                        // One layer doing both jobs: the fade and the cut-off. Set from a lambda,
                        // so a new alpha repaints the layer without recomposing what is inside it.
                        .graphicsLayer {
                            alpha = expansion.value
                            clip = true
                        }
                        .layout { measurable, constraints ->
                            // Measured at the width it will settle on — half the row, plus the
                            // gutter it brings with it — whatever it is worth right now, then cut
                            // off at the width opened so far. The field never re-measures, so its
                            // text never reflows and every glyph is already where it ends up as it
                            // is uncovered.
                            //
                            // This is the only child of the row without a weight, so the width
                            // offered here is the row's own.
                            val settled =
                                ((constraints.maxWidth + SplitGutter.roundToPx()) / 2)
                                    .coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = settled, maxWidth = settled),
                            )
                            val opened = (settled * expansion.value)
                                .roundToInt()
                                .coerceIn(0, settled)
                            layout(opened, placeable.height) { placeable.place(0, 0) }
                        }
                        // Painted, not laid out. As a node the rule would need the row measured
                        // for its intrinsic height before it could fill it, and that is an extra
                        // measure pass every frame for a line one pixel wide.
                        .drawBehind {
                            val top = RuleTopInset.toPx()
                            drawRect(
                                color = ruleColor,
                                topLeft = Offset(x = SplitGutterPadding.toPx(), y = top),
                                size = Size(
                                    width = RuleWidth.toPx(),
                                    height = (size.height - top - RuleBottomInset.toPx())
                                        .coerceAtLeast(0f),
                                ),
                            )
                        }
                        .padding(start = SplitGutter),
                ) {
                    second()
                }
            }
        }
        if (showDivider) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(thickness = 1.dp, color = FlightColors.Divider)
        }
    }
}

private val SplitGutterPadding = 10.dp
private val RuleWidth = 1.dp
private val RuleTopInset = 8.dp
private val RuleBottomInset = 6.dp

/** Both gutters plus the rule between them: what the row loses to the split. */
private val SplitGutter = SplitGutterPadding * 2 + RuleWidth

/** From/To row: the city with its IATA code, and the full airport name beneath. */
@Composable
fun AirportField(
    label: String,
    icon: ImageVector,
    airport: Airport,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    /**
     * Shown in place of an unpicked airport, greyed, as an invitation to fill the field rather
     * than a statement about where the trip goes — which is what a prefilled route would be. The
     * same treatment [IconValueField] gives the stay card's destination.
     */
    placeholder: String = "",
) {
    LabeledFieldBox(
        label = label,
        modifier = modifier,
        onClick = onClick,
        showDivider = showDivider,
        leading = { FieldLeadingIcon(icon) },
    ) {
        if (airport.isChosen) {
            Text(
                text = "${airport.city} (${airport.code})",
                style = MaterialTheme.typography.headlineLarge,
                color = FlightColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = airport.name,
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            // One line where a chosen airport draws two, padded to the height those two come to —
            // so the card does not resize as its fields are filled in.
            Text(
                text = placeholder,
                style = MaterialTheme.typography.headlineLarge,
                color = FlightColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * Pill selector for the choices that change the *shape* of the form rather than a value in it —
 * a trip's return leg and a rental's drop-off being the two that do. A switch states a fact and
 * hides the alternative; two tabs put both on the card and let one be chosen, which is what the
 * choice actually is.
 *
 * Styled after the reference's filter chips: the live tab is a solid blue pill, the rest sit quiet
 * on a track translucent enough that the glass behind the card still reads through it. The pill
 * slides between tabs on [SearchFieldMotion], the same clock as the field the choice adds or
 * removes, so the two read as one gesture.
 *
 * The labels are drawn twice — once resting, once in the selected colour clipped to exactly where
 * the pill is. Cross-fading their colour on a timer instead looks like a flicker: mid-travel the
 * incoming label has already turned white while it is still over the pale track, and the outgoing
 * one has gone grey while it is still over the blue, so for a few frames neither can be read.
 * Tying the highlight to the pill's own edges makes that impossible by construction.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = options.size.coerceAtLeast(1)
    val haptics = LocalHapticFeedback.current
    // Not delegated: read in the draw lambdas below, the pill's travel repaints without
    // recomposing the rows around it.
    val thumb = animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, count - 1).toFloat(),
        animationSpec = tween(
            durationMillis = SearchFieldMotion.DurationMillis,
            easing = SearchFieldMotion.Easing,
        ),
        label = "segmentedThumb",
    )
    val thumbColor = FlightColors.Primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Pill)
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.70f), FlightShapes.Pill)
            .padding(4.dp),
    ) {
        // Sized off the label rows rather than the other way round, so the pill never takes part
        // in the row's measurement and is free to sit between two tabs mid-travel.
        Canvas(modifier = Modifier.matchParentSize()) {
            val segment = size.width / count
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(x = segment * thumb.value, y = 0f),
                size = Size(width = segment, height = size.height),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }

        // Resting copy. This layer carries the taps; the one above it has no pointer input, so
        // presses fall straight through.
        TabLabelRow(
            options = options,
            color = FlightColors.TextSecondary,
            selectedIndex = selectedIndex,
            onSelect = { index ->
                // Only when the selection actually moves. Re-tapping the live tab changes nothing
                // on screen, and a tick with no movement behind it reads as a glitch.
                if (index != selectedIndex) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                onSelect(index)
            },
        )

        // The same copy in the selected colour, shown only across the pill's own width.
        TabLabelRow(
            options = options,
            color = FlightColors.OnPrimary,
            selectedIndex = selectedIndex,
            onSelect = null,
            modifier = Modifier
                // A second copy of the labels would otherwise be read out twice.
                .clearAndSetSemantics {}
                .drawWithContent {
                    val segment = size.width / count
                    val left = segment * thumb.value
                    clipRect(left = left, right = left + segment) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}

/**
 * One row of tab labels. Both layers of [SegmentedTabs] use this with identical content and
 * weights, which is what keeps the highlighted copy registered exactly over the resting copy.
 */
@Composable
private fun TabLabelRow(
    options: List<String>,
    color: Color,
    selectedIndex: Int,
    onSelect: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(FlightShapes.Pill)
                    .then(
                        if (onSelect == null) {
                            Modifier
                        } else {
                            Modifier.selectable(
                                selected = index == selectedIndex,
                                interactionSource = remember { MutableInteractionSource() },
                                // The travelling pill is the answer to a tap here; a ripple
                                // underneath it just muddies the one moving part.
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(index) },
                            )
                        },
                    )
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The circular swap control straddling the From and To fields — white on the card, as the
 * reference draws it, with the arrows in blue.
 *
 * Every tap turns the arrows another half circle, the same way round each time — the two fields
 * change under it in one frame, and the turn is what carries the eye from the old pair to the new
 * one. A half turn rather than a whole one because the glyph is an up arrow beside a down arrow:
 * at 180° each has taken the other's place, which is the swap itself, and the icon is upright
 * again at rest.
 *
 * On the same bouncy spring as the bottom bar's lift, so it overshoots a little and settles
 * instead of stopping dead.
 */
@Composable
fun SwapButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Counted rather than toggled: a toggle unwinds on the second tap, which reads as undoing the
    // swap when it has in fact done another one.
    var halfTurns by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val rotation by animateFloatAsState(
        targetValue = halfTurns * 180f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
    )

    Box(
        modifier = modifier
            .size(46.dp)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                ambientColor = Color(0xFF143C6B),
                spotColor = Color(0xFF143C6B),
            )
            .clip(CircleShape)
            .background(FlightColors.Surface)
            .pressable(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    halfTurns++
                    onClick()
                },
                // The half turn below is already this button's answer to a tap. A dip on top of it
                // would fight the spring rather than add to it.
                pressedScale = 1f,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = FlightIcons.SwapVert,
            contentDescription = stringResource(Res.string.swap_origin_and_destination),
            tint = FlightColors.Primary,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation),
        )
    }
}

/** Compact variant used on the results header where vertical room is tight. */
@Composable
fun AirportSummaryRow(
    from: Airport,
    to: Airport,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = from.code,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Icon(
            imageVector = FlightIcons.Flight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp).rotate(90f),
        )
        Text(
            text = to.code,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
    }
}
