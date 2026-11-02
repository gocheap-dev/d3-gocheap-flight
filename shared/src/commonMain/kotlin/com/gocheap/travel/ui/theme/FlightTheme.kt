package com.gocheap.travel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import flighttp.shared.generated.resources.Res
import flighttp.shared.generated.resources.plus_jakartasans_bold
import flighttp.shared.generated.resources.plus_jakartasans_extra_bold
import flighttp.shared.generated.resources.plus_jakartasans_medium
import flighttp.shared.generated.resources.plus_jakartasans_regular
import flighttp.shared.generated.resources.plus_jakartasans_semi_bold
import org.jetbrains.compose.resources.Font

@Composable
fun FlightTheme(
    content: @Composable () -> Unit,
) {
    val fontFamily = FontFamily(
        Font(Res.font.plus_jakartasans_regular, FontWeight.Normal),
        Font(Res.font.plus_jakartasans_medium, FontWeight.Medium),
        Font(Res.font.plus_jakartasans_semi_bold, FontWeight.SemiBold),
        Font(Res.font.plus_jakartasans_bold, FontWeight.Bold),
        Font(Res.font.plus_jakartasans_extra_bold, FontWeight.ExtraBold),
    )
    val typography = remember(fontFamily) { flightTypography(fontFamily) }

    MaterialTheme(
        colorScheme = FlightColorScheme,
        typography = typography,
        content = content,
    )
}

private val FlightColorScheme = lightColorScheme(
    primary = FlightColors.Primary,
    onPrimary = FlightColors.OnPrimary,
    primaryContainer = FlightColors.PrimarySoft,
    onPrimaryContainer = FlightColors.Primary,
    background = FlightColors.Background,
    onBackground = FlightColors.TextPrimary,
    surface = FlightColors.Surface,
    onSurface = FlightColors.TextPrimary,
    surfaceVariant = FlightColors.SurfaceMuted,
    onSurfaceVariant = FlightColors.TextSecondary,
    outline = FlightColors.Border,
    outlineVariant = FlightColors.Divider,
    error = FlightColors.Danger,
)

/**
 * Calibrated against the reference shot on a 375pt-wide canvas, then taken down a step at the top
 * of the ramp: a 23sp hero, 18sp section heads, 15sp city names, over an unchanged 11-14sp support
 * ramp. Material slots are mapped onto those.
 *
 * The large end was costing the search tabs a screen. A two-line hero at 27/34 is 64dp before
 * anything under it is drawn, and with the card below it that pushed the popular-locations grid
 * off the bottom entirely — the page opened on a heading and one card and had to be scrolled to
 * find out there was anything else. The support ramp is left alone: it is already at 11-14sp and
 * taking it lower trades legibility for pixels that are not there to win.
 */
private fun flightTypography(fontFamily: FontFamily): Typography {
    fun style(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
        fontFamily = fontFamily,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
    )

    return Typography(
        displayLarge = style(23, 29, FontWeight.ExtraBold),
        displayMedium = style(21, 27, FontWeight.ExtraBold),
        displaySmall = style(19, 25, FontWeight.Bold),
        headlineLarge = style(18, 24, FontWeight.Bold),
        headlineMedium = style(17, 23, FontWeight.Bold),
        headlineSmall = style(17, 22, FontWeight.Bold),
        titleLarge = style(15, 20, FontWeight.Bold),
        titleMedium = style(14, 20, FontWeight.SemiBold),
        titleSmall = style(13, 18, FontWeight.SemiBold),
        bodyLarge = style(14, 20, FontWeight.Medium),
        bodyMedium = style(13, 18, FontWeight.Medium),
        bodySmall = style(12, 16, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.Bold),
        labelMedium = style(12, 16, FontWeight.SemiBold),
        labelSmall = style(11, 14, FontWeight.Medium),
    )
}
