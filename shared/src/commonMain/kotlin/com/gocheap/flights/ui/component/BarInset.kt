package com.gocheap.flights.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * How much clearance the bottom bar leaves below its tabs for the system's own gesture furniture.
 *
 * This is not simply the navigation-bar inset, because the two platforms put very different things
 * in it. Android may be reserving room for three real buttons, and anything less than the full
 * inset there puts the tabs underneath them. iOS is only ever reserving room for the home
 * indicator — a thin line sitting within about 10pt of the edge — so handing the bar the whole
 * 34pt inset buys nothing and leaves a dead band under the labels taller than the labels are.
 */
@Composable
internal expect fun bottomBarInset(): Dp
