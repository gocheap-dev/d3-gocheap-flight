package com.gocheap.travel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp
import com.gocheap.travel.ui.theme.FlightColors

/**
 * Android keeps the reference's solid white bar, so the backdrop goes unused here. Frosting it
 * would mean `RenderEffect`, which is API 31 and up — the bar would be glass on new phones and
 * flat white on the rest, and the tab labels are a light grey that was picked against white.
 */
@Composable
internal actual fun GlassSurface(
    topCornerRadius: Dp,
    @Suppress("UNUSED_PARAMETER") backdrop: GraphicsLayer,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
            .background(FlightColors.Surface),
        content = content,
    )
}
