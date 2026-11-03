package com.gocheap.flights.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radii and spacing steps measured off the reference shot. */
object FlightShapes {
    val Field = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(28.dp)

    /** The reference's call to action is a full pill, not a rounded rectangle. */
    val Button = RoundedCornerShape(50)
    /**
     * Top corners only: the reference's bar is anchored to the bottom edge, not floating.
     *
     * The radius is named separately because the iOS bar is backed by a native blur view, and a
     * `Shape` is no use there — a `UIView` rounds itself off its layer's corner radius.
     */
    val BottomBarCorner = 28.dp
    val BottomBar = RoundedCornerShape(topStart = BottomBarCorner, topEnd = BottomBarCorner)
    val Pill = RoundedCornerShape(50)
}

object FlightSpacing {
    /** The gutter every screen holds its content off the edge by. */
    val ScreenHorizontal = 12.dp

    /**
     * What a card insets its own content by. Larger than the screen gutter on purpose — the card
     * already sits 12dp in, so matching the two would leave its contents almost on its edge.
     */
    val CardPadding = 16.dp
    val FieldVertical = 14.dp
    val SectionGap = 28.dp
    val ItemGap = 12.dp

    /**
     * The bottom bar's own height — the tab row, before the system navigation inset the bar
     * carries inside its white. Shared with the spacer that holds scrolling pages off it, so the
     * two cannot drift apart.
     */
    val BottomBarHeight = 74.dp

    /** How far the last row of a scrolling page stops short of the bottom bar. */
    val BottomBarGap = 24.dp
}
