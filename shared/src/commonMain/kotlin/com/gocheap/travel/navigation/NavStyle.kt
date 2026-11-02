package com.gocheap.travel.navigation

/**
 * Whether this platform's own navigation transitions should be used instead of the app's.
 *
 * iOS has a push and pop everyone there can recognise — the page on top travelling the full width
 * while the one beneath covers a quarter of it and dims — and the navigation library already
 * ships it, gesture spec included. Reaching for that rather than approximating it here is the
 * difference between a back swipe that feels native and one that merely moves in the right
 * direction.
 *
 * Android has its own conventions and its own custom motion in [NavMotion], so it opts out.
 */
internal expect val NavigatesNatively: Boolean
