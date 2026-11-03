package com.gocheap.flights.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import flighttp.shared.generated.resources.Res
import flighttp.shared.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource

/**
 * The app's own mark, on the tile it is always drawn on.
 *
 * One composable rather than the same `Box` + `Image` written out at each of the three places this
 * appears — the splash, the onboarding header and the About page — because the three differ only
 * in how big they are and what is behind them. They used to share a generic aeroplane glyph
 * instead, and drifted: one was rotated 45°, the others were not, and the padding around the glyph
 * was a different fraction of the tile in each.
 *
 * [LogoFraction] is what keeps them consistent now. The artwork is landscape and
 * [ContentScale.Fit] inside a square box fits it to the width, so the mark is always the same
 * proportion of the tile whatever [size] is, and is centred in the leftover height.
 */
@Composable
fun AppLogoMark(
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.app_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size * LogoFraction),
        )
    }
}

/**
 * How much of the tile the mark takes up.
 *
 * The same proportion the round launcher icon uses, so the icon on the home screen and the mark on
 * the splash read as the same thing rather than as two sizes of it.
 */
private const val LogoFraction = 0.62f
