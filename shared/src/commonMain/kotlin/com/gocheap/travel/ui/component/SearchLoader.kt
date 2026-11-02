package com.gocheap.travel.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.comparing_prices_across_rental_suppliers

/**
 * Held over a results page until it is worth looking at.
 *
 * Stands in for the reference app's Lottie loader, which this project has no animation runtime for —
 * hence one composable with the vertical's icon and wording passed in, rather than an animation file
 * per vertical.
 *
 * The bar is driven by the page's real progress rather than a timer, so a slow network reads as slow
 * instead of as finished-then-frozen. [progress] is still only a floor, though: a web view can
 * report nothing at all between "started" and "finished", so the bar also creeps forward on its own
 * — see [barProgress] — rather than sitting at zero for the whole load and then vanishing.
 */
@Composable
internal fun SearchLoader(
    label: String,
    caption: String,
    icon: ImageVector,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "loader")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val bar = barProgress(progress)

    Box(
        modifier = modifier.background(FlightColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(scale)
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
            Spacer(Modifier.height(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = FlightColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = FlightColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { bar },
                modifier = Modifier.fillMaxWidth().clip(CircleShape).height(4.dp),
                color = FlightColors.Primary,
                trackColor = FlightColors.Divider,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

/**
 * What the bar actually draws: the higher of the page's reported [progress] and a creep that eases
 * towards [CREEP_CEILING] on its own.
 *
 * Both halves are animated so the bar slides rather than jumps, and taking the higher of the two
 * keeps it monotonic — a web view reports 0 again on every redirect, and a bar that slid backwards
 * would read as the search having restarted.
 *
 * The creep stops short of full: only the real page finishing is allowed to fill the bar, otherwise
 * it would promise results that have not arrived.
 */
@Composable
private fun barProgress(progress: Float): Float {
    val reported = remember { Animatable(0f) }
    val creep = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        creep.animateTo(
            targetValue = CREEP_CEILING,
            animationSpec = tween(CREEP_MILLIS, easing = LinearOutSlowInEasing),
        )
    }
    LaunchedEffect(progress) {
        reported.animateTo(progress.coerceIn(0f, 1f), tween(300))
    }

    return maxOf(reported.value, creep.value)
}

/** How far the bar is allowed to travel on its own, with nothing heard from the page. */
private const val CREEP_CEILING = 0.9f

/** Long enough that a page loading normally overtakes the creep rather than following it. */
private const val CREEP_MILLIS = 12_000

@Preview
@Composable
private fun SearchLoaderPreview() {
    FlightTheme {
        SearchLoader(
            label = "Searching cars in San Francisco",
            caption = stringResource(Res.string.comparing_prices_across_rental_suppliers),
            icon = FlightIcons.CarOutlined,
            progress = 0.4f,
        )
    }
}
