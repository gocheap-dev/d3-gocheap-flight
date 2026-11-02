package com.gocheap.travel.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Capped, because on iOS the inset is pure home-indicator clearance. The reported 34pt is the
 * safe area Apple reserves for a bar that draws nothing there; this bar's glass already runs to
 * the bottom edge, so the inset only has to keep the labels off the indicator rather than off the
 * whole band — and taking all of it was leaving more empty space under the labels than the labels
 * and icons occupy together.
 *
 * Still read from the window rather than hardcoded: a device without a home indicator reports
 * zero, and this should collapse with it rather than invent a gap.
 */
@Composable
internal actual fun bottomBarInset(): Dp {
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return minOf(inset, MaxIndicatorClearance)
}

/** Clears the indicator, which sits within roughly 10pt of the bottom edge. */
private val MaxIndicatorClearance = 8.dp
