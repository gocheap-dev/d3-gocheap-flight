package com.gocheap.travel.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.DefaultNavTransitions

/**
 * The motion spec for the nav graph, built on the Material 3 easing set.
 *
 * Two kinds of transition, because the two kinds of navigation mean different things:
 *
 * - **Push / pop** (Home -> results -> detail) is a hierarchy change, and the two platforms stage
 *   it differently — see [NavigatesNatively].
 *
 *   Android tiles the screens edge to edge and slides both the full width on one shared spec:
 *   that only looks seamless while they stay touching, and giving the halves different easings
 *   would tear a gap open between them mid-flight.
 *
 *   iOS hands the job to the navigation library's own iOS transitions rather than approximating
 *   them here. UIKit's push is a parallax — the page on top travels the full width while the one
 *   beneath covers a quarter of it and dims, so going back looks like lifting the top page off
 *   one that was there all along. The dimming in particular is not something this file could
 *   reproduce: it is a veil the library draws between the two pages, and a transition built out
 *   of slides and fades has nowhere to put it.
 *
 *   Delegating also keeps the back *gesture* right. On iOS the pan from the start edge is always
 *   reported as a back event, so the page tracks the finger, and the library's gesture spec is
 *   linear where its animated one is eased — the page has to keep pace with the finger exactly,
 *   and an eased curve would have it drift ahead and lag behind within a single drag.
 * - **Tab switches** are lateral moves between siblings, so they travel sideways in the direction
 *   the bar moved: tapping a tab further right brings its page in from the right.
 *
 *   Opacity is deliberately left out of this one. Every tab screen paints an opaque background, so
 *   two of them sliding edge-to-edge cover the container on every single frame — there is no point
 *   in the transition where anything behind them can show through. Fading them instead is what put
 *   a flash in the middle of the swap: a sequential fade has the outgoing page reach zero before
 *   the incoming one starts, so for a few frames the host background was all that was painted, and
 *   an overlapping cross-fade is no better — two layers at 50% composite to 75%, so the whole
 *   screen dips towards the background instead of flashing to it. Translation alone has neither
 *   problem, and it is the cheapest thing to animate on both platforms: no alpha means no
 *   offscreen compositing layer per page.
 */
internal object NavMotion {
    /** Long enough to read as movement, short enough that the UI still feels immediate. */
    private const val PushDurationMillis = 350

    /** Tab switches run a touch shorter than a push: a lateral move should feel lighter. */
    private const val TabDurationMillis = 300

    /** Material 3 "emphasized": a slow, confident start and a long settle. */
    private val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    private val PushSpec: FiniteAnimationSpec<IntOffset> =
        tween(PushDurationMillis, easing = Emphasized)

    /**
     * The library's iOS pop pair. Built by handing [DefaultNavTransitions] its own enter and exit
     * back: those functions return the caller's transition untouched unless they are given the
     * defaults, which is how `NavHost` lets you override the push without losing the matching pop.
     * Passing the defaults is therefore the way to ask for the pop that goes with them.
     */
    private val nativePopEnter =
        DefaultNavTransitions.popEnterTransition(DefaultNavTransitions.enterTransition)
    private val nativePopExit =
        DefaultNavTransitions.popExitTransition(DefaultNavTransitions.exitTransition)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition =
        if (NavigatesNatively) {
            DefaultNavTransitions.enterTransition(this)
        } else {
            slideIntoContainer(towards = SlideDirection.Left, animationSpec = PushSpec)
        }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(): ExitTransition =
        if (NavigatesNatively) {
            DefaultNavTransitions.exitTransition(this)
        } else {
            slideOutOfContainer(towards = SlideDirection.Left, animationSpec = PushSpec)
        }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
        if (NavigatesNatively) {
            nativePopEnter(this)
        } else {
            slideIntoContainer(towards = SlideDirection.Right, animationSpec = PushSpec)
        }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
        if (NavigatesNatively) {
            nativePopExit(this)
        } else {
            slideOutOfContainer(towards = SlideDirection.Right, animationSpec = PushSpec)
        }

    /**
     * The back *gesture* is a separate pair of callbacks from [popEnter]/[popExit], and the
     * library's default for it is `scaleOut(targetScale = 0.7f)` — the page shrinks into the
     * distance. These override it with whatever the button-driven pop does, so back looks the same
     * however it was triggered and never zooms.
     *
     * On Android that means the page leaves to the right whichever edge the gesture came from.
     * Reading the edge and sending the page the other way — following the finger — sounds right
     * and is not: Android takes the gesture from both edges, so the same back left the page on
     * one side or the other depending on which hand did it, and the one screen could be seen
     * closing in both directions. Back means one thing, so it gets one direction, and it is the
     * one the back button already used.
     *
     * iOS keeps the edge, because it only ever sends back from the start edge — there is no other
     * case to disagree about — and because the page tracks the finger there rather than playing a
     * fixed animation, with the spec deciding only how it settles once the finger is lifted.
     */
    fun AnimatedContentTransitionScope<NavBackStackEntry>.predictivePopEnter(
        swipeEdge: Int,
    ): EnterTransition =
        if (NavigatesNatively) {
            DefaultNavTransitions.predictivePopEnterTransition(this, swipeEdge)
        } else {
            slideIntoContainer(towards = SlideDirection.Right, animationSpec = PushSpec)
        }

    fun AnimatedContentTransitionScope<NavBackStackEntry>.predictivePopExit(
        swipeEdge: Int,
    ): ExitTransition =
        if (NavigatesNatively) {
            DefaultNavTransitions.predictivePopExitTransition(this, swipeEdge)
        } else {
            slideOutOfContainer(towards = SlideDirection.Right, animationSpec = PushSpec)
        }

    private val TabSpec: FiniteAnimationSpec<IntOffset> =
        tween(TabDurationMillis, easing = Emphasized)

    /**
     * Both halves take the same direction and the same spec, which is what makes the pair tile
     * seamlessly: `slideIntoContainer` and `slideOutOfContainer` measure against the container, so
     * the incoming page sits exactly against the outgoing one's edge and the two move as one sheet.
     * Giving them different easings would open a gap between them mid-flight.
     */
    fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(
        movingRight: Boolean,
    ): EnterTransition = slideIntoContainer(towards = movingRight.along(), animationSpec = TabSpec)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(
        movingRight: Boolean,
    ): ExitTransition = slideOutOfContainer(towards = movingRight.along(), animationSpec = TabSpec)

    /** Moving towards a tab further right means the pages travel leftwards, and vice versa. */
    private fun Boolean.along(): SlideDirection =
        if (this) SlideDirection.Left else SlideDirection.Right

    /** The floating bar rides in on the same curve as everything else. */
    val BarDurationMillis = PushDurationMillis
    val BarEasing = Emphasized
}
