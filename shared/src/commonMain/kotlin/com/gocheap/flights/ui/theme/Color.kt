package com.gocheap.flights.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette read off the "Plan your trip" reference: a royal blue carrying the hero and every accent,
 * near-black for the primary action, white cards floating on a pale blue page, and a narrow grey
 * ramp for supporting copy.
 *
 * The action colour is deliberately separate from [Primary]: the reference puts its call to action
 * in black and keeps blue for state and emphasis, so the two cannot share one slot.
 */
object FlightColors {
    val Primary = Color(0xFF2F6BF6)
    val PrimaryPressed = Color(0xFF1F55DB)
    val PrimarySoft = Color(0xFFE7EFFE)

    /** Near-black pill button and the text that sits on it. */
    val Action = Color(0xFF0B0B0F)
    val ActionPressed = Color(0xFF26262D)
    val OnAction = Color(0xFFFFFFFF)

    /** Hero gradient behind the heading, read top-to-bottom off the reference. */
    val SkyTop = Color(0xFF1F4FE0)
    val SkyMid = Color(0xFF3A6BEE)
    val SkyBottom = Color(0xFF7FA5F5)
    val Cloud = Color(0xFFFFFFFF)

    val Background = Color(0xFFF2F5FC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF4F6FA)

    val TextPrimary = Color(0xFF101114)
    val TextSecondary = Color(0xFF8A8F98)
    val TextTertiary = Color(0xFFB4B9C2)
    val OnPrimary = Color(0xFFFFFFFF)

    /** What every raised card casts. A deep navy rather than black, to suit the page's blue. */
    val CardShadow = Color(0xFF143C6B)

    val Border = Color(0xFFE8EBF1)
    val BorderStrong = Color(0xFFD5DAE3)
    val Divider = Color(0xFFEDF0F5)

    val Success = Color(0xFF2FBF71)
    val Warning = Color(0xFFF7A94A)
    val Danger = Color(0xFFE5484D)

    /** Seat-map states. */
    val SeatAvailable = Color(0xFFEDF2F7)
    val SeatSelected = Primary
    val SeatTaken = Color(0xFFD6D8DD)
}
