package com.gocheap.flights.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gocheap.flights.ui.theme.FlightColors
import kotlin.math.roundToInt

/**
 * The glass, blurred by Compose rather than by UIKit — see [GlassSurface] for why a
 * `UIVisualEffectView` cannot be fed here.
 *
 * Each frame the bar re-records the slice of [backdrop] that lies behind it into its own layer,
 * carrying a [BlurEffect], and draws that as its background. The translate is what picks the slice:
 * the backdrop was recorded in root coordinates, so shifting it by this bar's own position lines
 * the page back up under the tabs. A tint goes over the top, because a plain blur reads as a smear
 * — the lightening is what makes it read as a pane of glass.
 *
 * Recording the bar's own slice rather than blurring a full-screen copy keeps the cost to the
 * handful of rows actually on screen behind it.
 */
@Composable
internal actual fun GlassSurface(
    topCornerRadius: Dp,
    backdrop: GraphicsLayer,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val frosted = rememberGraphicsLayer()
    // Where this bar sits on screen, which is what the backdrop has to be shifted by. Read from
    // layout rather than assumed: the bar is placed by its caller, not by this function.
    //
    // Null until the bar has been placed. It matters that this is not simply zero: the bar draws
    // once before layout reports back, and a zero offset samples the top-left of the page, so the
    // bar flashed a slice of the blue header every time it slid into view.
    var origin by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
            .drawBehind {
                val at = origin
                if (at == null) {
                    // Pre-layout: stand in with the opaque panel Android uses. One flat frame is
                    // invisible next to a frame of the wrong part of the screen.
                    drawRect(color = FlightColors.Surface)
                    return@drawBehind
                }
                val bleed = BlurRadius.toPx()
                frosted.renderEffect = BlurEffect(
                    radiusX = bleed,
                    radiusY = bleed,
                    edgeTreatment = TileMode.Clamp,
                )
                // Recorded a blur-radius taller than the bar at each end. Without the overhang the
                // top edge has nothing above it to sample and clamps instead, smearing whatever
                // row of pixels happens to sit on the boundary down into the glass.
                frosted.record(
                    IntSize(
                        width = size.width.roundToInt(),
                        height = (size.height + bleed * 2f).roundToInt(),
                    ),
                ) {
                    translate(left = -at.x, top = -at.y + bleed) { drawLayer(backdrop) }
                }
                translate(top = -bleed) { drawLayer(frosted) }
                drawRect(color = Tint)
            },
    ) {
        content()
    }
}

/** Wide enough that the page reads as colour and light behind the bar rather than as shapes. */
private val BlurRadius = 20.dp

/** Lifts the blur towards white so the near-black icons keep their contrast against it. */
private val Tint = FlightColors.Surface.copy(alpha = 0.62f)
