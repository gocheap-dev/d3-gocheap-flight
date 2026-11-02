package com.gocheap.travel.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gocheap.travel.ui.theme.FlightColors

/**
 * The one blue ramp the app uses, landing on the page colour at its last stop. That final stop is
 * what lets a band of any height end without a seam: wherever it stops, it has already become the
 * page.
 *
 * Size-relative, so it reads the same behind a 330dp home hero as behind a band only as tall as a
 * title bar.
 */
fun skyGradient(): Brush = Brush.verticalGradient(
    0f to FlightColors.SkyTop,
    0.58f to FlightColors.SkyMid,
    0.86f to FlightColors.SkyBottom,
    1f to FlightColors.Background,
)

/**
 * The blue band behind every search hero: a vertical gradient from a deep royal blue down to the
 * page colour, so the band has no edge of its own and the search card appears to float out of it.
 *
 * The reference keeps this surface clean — [showClouds] is left over for the screens that still
 * want the soft puffs, and is off by default.
 */
@Composable
fun SkyHeader(
    modifier: Modifier = Modifier,
    height: Dp = 420.dp,
    showPlane: Boolean = true,
    showClouds: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(skyGradient()),
    ) {
        if (showClouds) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawCloudBank() }
        }
        content()
    }
}

/** Overlapping translucent ellipses read as a cloud layer without needing a bitmap. */
private fun DrawScope.drawCloudBank() {
    val puffs = listOf(
        Triple(0.02f, 0.80f, 0.34f),
        Triple(0.18f, 0.72f, 0.26f),
        Triple(0.38f, 0.86f, 0.40f),
        Triple(0.66f, 0.76f, 0.30f),
        Triple(0.86f, 0.84f, 0.32f),
    )
    puffs.forEach { (xFraction, yFraction, widthFraction) ->
        val width = size.width * widthFraction
        drawOval(
            color = FlightColors.Cloud.copy(alpha = 0.18f),
            topLeft = Offset(size.width * xFraction, size.height * yFraction - width * 0.18f),
            size = Size(width, width * 0.42f),
        )
    }
}
