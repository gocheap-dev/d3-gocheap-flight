package com.gocheap.flights.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.flights.ui.component.AppLogoMark
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.profile.rememberProfileActions
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.utils.Constants
import flighttp.shared.generated.resources.Res
import flighttp.shared.generated.resources.make_your_flight_more_memorable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.compose.resources.stringResource

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = viewModel { SplashViewModel() },
    onSplashFinished: (SplashDestination) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        val destination = state.destination ?: return@LaunchedEffect
        onSplashFinished(destination)
    }

    SplashScreenContent(modifier = modifier)
}

/**
 * The launch screen: a night sky, a flight path drawn across it, and the mark landing in the
 * middle of it.
 *
 * Everything is on a timeline that finishes comfortably inside the splash's own duration, because
 * a screen still animating when the app navigates away reads as a dropped frame rather than as a
 * flourish. The delays are constants at the foot of this file, so the order can be read in one
 * place instead of inferred from six call sites.
 */
@Composable
private fun SplashScreenContent(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val target = if (started) 1f else 0f
    val appVersion = rememberProfileActions().appVersion

    // The mark arrives with a little overshoot — it is the one element that should feel like it
    // lands rather than fades. Everything else uses plain easing, so the bounce stays its own.
    val markScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "markScale",
    )
    val markAlpha by animateFloatAsState(target, tween(MarkFadeMillis), label = "markAlpha")
    val pathProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(PathDrawMillis, easing = FastOutSlowInEasing),
        label = "pathProgress",
    )
    val nameAlpha by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(TextFadeMillis, delayMillis = NameDelayMillis),
        label = "nameAlpha",
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(TextFadeMillis, delayMillis = TaglineDelayMillis),
        label = "taglineAlpha",
    )
    val footerAlpha by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(TextFadeMillis, delayMillis = FooterDelayMillis),
        label = "footerAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to FlightColors.SkyTop,
                    0.55f to FlightColors.Primary,
                    1f to FlightColors.SkyMid,
                ),
            ),
    ) {
        StarField()
        FlightPath(progress = pathProgress)

        // A soft bloom under the mark, so the white tile sits in the sky rather than on top of it.
        // Between the path and the mark on purpose — the plane passes in front of the stars and
        // behind the glow.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.16f * markAlpha),
                        1f to Color.Transparent,
                        radius = GlowRadiusPx,
                    ),
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppLogoMark(
                size = 112.dp,
                modifier = Modifier.graphicsLayer {
                    scaleX = markScale
                    scaleY = markScale
                    alpha = markAlpha
                },
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = Constants.App.DisplayName,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .riseIn(nameAlpha),
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(Res.string.make_your_flight_more_memorable),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 40.dp)
                    .riseIn(taglineAlpha),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
                .graphicsLayer { alpha = footerAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoadingLine()
            Spacer(Modifier.height(14.dp))
            Text(
                // Bare, rather than the About page's "App version %s": this is a corner of a
                // launch screen, and the number is for whoever reads a bug report off a
                // screenshot. A lone `v` carries in every language the app ships in.
                text = "v$appVersion",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

/**
 * Fades in while sliding up a few pixels.
 *
 * The slide is driven by the same value as the fade rather than animated separately, so the two
 * cannot come apart — and the distance reaches zero exactly as the text reaches full opacity.
 */
private fun Modifier.riseIn(alpha: Float): Modifier = graphicsLayer {
    this.alpha = alpha
    translationY = (1f - alpha) * RiseDistancePx
}

/**
 * The sky behind everything.
 *
 * Positions come from a fixed seed rather than being drawn fresh each composition: a star that
 * jumps when the screen recomposes is a bug the eye catches immediately, and this screen
 * recomposes on every animation frame.
 *
 * The twinkle is one shared clock with a per-star phase offset rather than one animation each.
 * Forty-two independent animations on the first frame of the app is a cost with nothing to show
 * for it, and a sine of the same clock is indistinguishable on screen.
 */
@Composable
private fun StarField() {
    val stars = remember {
        val random = Random(StarSeed)
        List(StarCount) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = random.nextFloat() * 1.6f + 0.8f,
                phase = random.nextFloat(),
            )
        }
    }

    val clock by rememberInfiniteTransition(label = "stars").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(TwinkleMillis, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "twinkle",
    )

    Canvas(Modifier.fillMaxSize()) {
        for (star in stars) {
            val wave = sin((clock + star.phase) * 2f * PI.toFloat()) * 0.5f + 0.5f
            drawCircle(
                color = Color.White.copy(alpha = 0.18f + wave * 0.32f),
                radius = star.radius.dp.toPx(),
                center = Offset(star.x * size.width, star.y * size.height),
            )
        }
    }
}

private data class Star(val x: Float, val y: Float, val radius: Float, val phase: Float)

/**
 * The dotted arc, and the plane drawing it.
 *
 * A [PathMeasure] over a single curve does both halves: `getSegment` is the trail behind the
 * plane, and `getPosition`/`getTangent` at the same distance put the plane on the end of it facing
 * the way it is travelling. Rotating by the tangent rather than by a hand-tuned angle is what
 * keeps the nose on the curve the whole way across instead of only at the ends.
 */
@Composable
private fun FlightPath(progress: Float) {
    val plane = rememberVectorPainter(FlightIcons.Flight)
    // Both are written into on every frame rather than rebuilt, which is what `getSegment`'s
    // destination parameter is for.
    val measure = remember { PathMeasure() }
    val trail = remember { Path() }

    Canvas(Modifier.fillMaxSize()) {
        measure.setPath(arcAcross(size), forceClosed = false)
        val total = measure.length
        if (total <= 0f) return@Canvas

        val travelled = total * progress

        trail.reset()
        measure.getSegment(0f, travelled, trail, startWithMoveTo = true)
        drawPath(
            path = trail,
            color = Color.White.copy(alpha = 0.38f),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(7.dp.toPx(), 9.dp.toPx()),
                ),
            ),
        )

        if (progress <= 0f) return@Canvas

        val position = measure.getPosition(travelled)
        val tangent = measure.getTangent(travelled)
        // The glyph's nose points up, which is -90° in atan2's terms, so the tangent's own angle
        // is turned by +90 to bring the plane onto the curve.
        val heading = atan2(tangent.y, tangent.x) * 180f / PI.toFloat() + 90f

        val side = PlaneSizeDp.dp.toPx()
        translate(left = position.x - side / 2f, top = position.y - side / 2f) {
            rotate(degrees = heading, pivot = Offset(side / 2f, side / 2f)) {
                with(plane) {
                    draw(
                        size = Size(side, side),
                        alpha = progress,
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                }
            }
        }
    }
}

/**
 * The curve the plane follows: in low on the left, up over the mark, out high on the right.
 *
 * Fractions of the canvas rather than absolute dp, so it reads the same on a short phone and a
 * tall one — a fixed curve would sit across the title on one and off the top of the other.
 *
 * It starts off the left edge so the plane is never seen to appear, but ends inside the right
 * one: the path finishes about 450ms before the splash does, and a plane that had flown off the
 * screen would leave that last moment — the one a screenshot catches — as a bare dotted line.
 */
private fun arcAcross(size: Size): Path = Path().apply {
    moveTo(-size.width * 0.10f, size.height * 0.48f)
    // The end sits only a little below the control point, so the tangent there is close to
    // level: finish any lower and the plane comes to rest nosing down, which reads as a descent
    // rather than as the cruise it is meant to be.
    quadraticTo(
        size.width * 0.42f, size.height * 0.10f,
        size.width * 0.88f, size.height * 0.17f,
    )
}

/**
 * An indeterminate bar, drawn rather than taken from Material.
 *
 * Material3's own indicator has changed shape more than once across the alphas this project
 * tracks, and a launch screen is the worst place to find out that a dependency bump restyled
 * something. Two rectangles and one looping float cannot.
 */
@Composable
private fun LoadingLine() {
    val sweep by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(LoadingSweepMillis, easing = FastOutSlowInEasing),
            RepeatMode.Restart,
        ),
        label = "sweep",
    )

    Canvas(
        Modifier
            .width(LoadingWidthDp.dp)
            .height(LoadingHeightDp.dp)
            .clip(RoundedCornerShape(LoadingHeightDp.dp)),
    ) {
        drawRect(Color.White.copy(alpha = 0.22f))
        val highlight = size.width * 0.34f
        // Travels from fully off the left to fully off the right, so the bar is never seen to
        // stop and restart at either end.
        drawRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(-highlight + (size.width + highlight) * sweep, 0f),
            size = Size(highlight, size.height),
        )
    }
}

/* ----------------------------------- The timeline ----------------------------------- */

/**
 * The plane crosses first; everything else arrives under it.
 *
 * Sized against `SplashViewModel.SplashDurationMillis` — the last thing to land does so at
 * [FooterDelayMillis] + [TextFadeMillis], and the arc finishes at [PathDrawMillis], both of which
 * sit a beat inside it. Shortening the splash means shortening these too, or the screen navigates
 * away while the plane is still mid-flight.
 */
private const val PathDrawMillis = 820
private const val MarkFadeMillis = 380
private const val TextFadeMillis = 300
private const val NameDelayMillis = 250
private const val TaglineDelayMillis = 360
private const val FooterDelayMillis = 500

private const val TwinkleMillis = 3200
/**
 * How long the highlight takes to cross the bar.
 *
 * Longer than the ~700ms the footer is actually on screen, deliberately. The highlight enters
 * from off the left edge and leaves off the right one, so a pass that does not finish inside the
 * splash is simply a bar still travelling — there is no loop point to be caught short of. A
 * quicker sweep did complete, and read as hurried beside everything else settling into place.
 */
private const val LoadingSweepMillis = 1250

private const val PlaneSizeDp = 26f
private const val LoadingWidthDp = 128f
private const val LoadingHeightDp = 3f

/** How far the text travels while it fades up. Pixels, because `graphicsLayer` works in them. */
private const val RiseDistancePx = 18f

/** Wide enough that the bloom has no visible edge on any phone. */
private const val GlowRadiusPx = 620f

private const val StarCount = 42
private const val StarSeed = 20260301
