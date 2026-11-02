package com.gocheap.travel.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * A tap target that answers with movement rather than a ripple: the control dips under the finger
 * and springs back when it is let go.
 *
 * **Apply this before the modifiers that paint the control.** The dip is a `graphicsLayer`, and a
 * layer only transforms what is drawn after it — placed at the end of a chain, past the
 * `background` and `border`, it would shrink the label while leaving the pill it sits on at full
 * size. Hit testing does not care where it sits, so the front of the chain costs nothing.
 *
 * Pass [pressedScale] `= 1f` for controls that already answer a tap with an animation of their own
 * — a second movement on top of theirs reads as a stutter. Nothing is layered in that case, so the
 * modifier is then only here to suppress the ripple.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    pressedScale: Float = DefaultPressedScale,
): Modifier {
    // Held here rather than left to `clickable` to create, because the press has to be read back
    // out to drive the dip — which is the whole point of passing a source in.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        // Springs rather than a tween so a quick tap still completes: a fixed duration played at
        // the speed of a real press only ever gets a few frames in before the release reverses it.
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )

    return this
        .then(
            if (pressedScale == 1f) {
                Modifier
            } else {
                Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            },
        )
        .clickable(
            interactionSource = interactionSource,
            // The ripple's replacement is the dip above, so there is nothing to draw here.
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/** Deep enough to read as a press at arm's length, shallow enough not to look like a bounce. */
const val DefaultPressedScale = 0.96f
