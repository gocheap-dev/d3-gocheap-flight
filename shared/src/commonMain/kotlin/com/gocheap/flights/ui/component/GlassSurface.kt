package com.gocheap.flights.ui.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * The panel the bottom bar draws its tabs on: solid on Android, frosted glass on iOS.
 *
 * [backdrop] is the page content, already recorded by whoever drew it. The glass needs it because
 * it cannot sample the screen on its own — a blur has to be given something to blur.
 *
 * A `UIVisualEffectView` is the obvious way to ask iOS for this and it does not work here. It
 * frosts whatever sits behind it *in the native view hierarchy*, but Compose renders the whole app
 * into one Metal canvas, so a native view is either below that canvas — where the only thing
 * behind it is the window's flat background, and the bar comes out an even grey no matter what is
 * on screen — or above it, where it covers the tabs it is supposed to sit behind. There is no
 * ordering that leaves it both fed and underneath, so the blur is done in Compose instead.
 *
 * It takes a [topCornerRadius] rather than a `Shape` only because that is all either side needs.
 *
 * Whatever [content] draws lands on top of the panel on both platforms.
 */
@Composable
internal expect fun GlassSurface(
    topCornerRadius: Dp,
    backdrop: GraphicsLayer,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
)
