package com.gocheap.flights.ui.home.results.api

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gocheap.flights.data.model.flight.ads.InlineAdItem
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.component.pressable

/**
 * One inline ad, as a card in the flight results list.
 *
 * The reference app's card, in this app's own shell: it sits on the same surface, shadow and
 * corner radius as [FlightResultCard], because an ad that is obviously a different component
 * reads as something gone wrong with the list rather than as a placement in it.
 *
 * **Everything on it is the host's.** The logo, the headline, the body and the button label all
 * come off [ad], and all of them are nullable — the host sends a creative with whatever fields it
 * has. Each falls back to something that still reads as an ad rather than as a blank: see
 * [adSiteName] for the logo's, and the card simply omits a line it has no text for. The one thing
 * not taken from the host is the "AD" mark, which is this app's.
 *
 * **The impression is reported from here, not from the click.** [onDisplayed] fires when the card
 * enters composition, keyed on the ad, which is the moment the view the host is paid for actually
 * happened. The guard against reporting it twice is `AdsRepository.trackImpression`'s, not this
 * composable's: a list recomposes freely and nothing a `LaunchedEffect` can key on survives a
 * scroll out and back.
 */
@Composable
internal fun FlightAdCard(
    ad: InlineAdItem,
    /**
     * The supplier's site for the user's country, which the ad's own logo URL is relative to —
     * the same value `AdsRepository` resolves its links against. Blank when the country has none,
     * in which case the logo is dropped and the name stands in for it.
     */
    baseDomain: String,
    onClick: () -> Unit,
    onDisplayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the ad rather than on Unit: one card can be recycled onto a different ad as the
    // list scrolls, and a `LaunchedEffect(Unit)` would report the first one and never the second.
    LaunchedEffect(ad.impressionUrl, ad.site) { onDisplayed() }

    val logo = remember(ad.logoUrl, baseDomain) { adLogoUrl(ad.logoUrl, baseDomain) }
    val chip = remember(ad.logoUrl) { adLogoBackground(ad.logoUrl) }
    val name = remember(ad.companyName, baseDomain) { adSiteName(ad.companyName, baseDomain) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .shadow(
                elevation = 10.dp,
                shape = FlightShapes.Card,
                spotColor = FlightColors.CardShadow,
            )
            .clip(FlightShapes.Card)
            .background(FlightColors.Surface)
            .padding(FlightSpacing.CardPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(chip ?: FlightColors.SurfaceMuted)
                    .width(100.dp)
                    .height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (logo == null) {
                    // The supplier's name set in the chip, which is what the reference app falls
                    // back to: a blank box reads as a failed image, a name reads as a brand.
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = FlightColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                } else {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 88.dp, height = 32.dp),
                    )
                }
            }

            AdMark()
        }

        Spacer(Modifier.height(20.dp))

        ad.headline?.takeIf(String::isNotBlank)?.let { headline ->
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = FlightColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
        }

        ad.description?.takeIf(String::isNotBlank)?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(12.dp))
                .background(FlightColors.Action)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // The host's own call to action where it sent one — which creative wording
                // converts is a question only this field can answer.
                text = ad.bookingButtonText?.takeIf(String::isNotBlank) ?: DefaultCallToAction,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = FlightColors.OnAction,
            )
        }
    }
}

/**
 * The "AD" mark.
 *
 * Required rather than decorative: a paid placement in a list of search results has to be
 * identifiable as one. Quiet — a hairline box at low opacity, the reference app's treatment — but
 * never conditional on anything the host sends.
 */
@Composable
private fun AdMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(
                width = 0.8.dp,
                color = FlightColors.TextTertiary,
                shape = RoundedCornerShape(5.dp),
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = "AD",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = FlightColors.TextTertiary,
        )
    }
}

/** What goes on the button when the host sent no wording of its own. */
private const val DefaultCallToAction = "View deal"

/**
 * The ad's logo, ready to load, or null if there is nothing to show.
 *
 * Two things happen to it:
 *
 * - **The image service's padding is dropped.** The host sizes the logo for a larger box than
 *   this card gives it and bakes the padding into the URL as `padX`/`padY`. The reference app
 *   strips the one literal it has seen — `&padX=5&padY=5` — which stops working the moment the
 *   host picks different numbers or puts the parameter first; [withoutQueryParams] drops them by
 *   name instead.
 * - **It is resolved against [baseDomain]**, because like every other URL on an ad it is relative
 *   to the supplier's site rather than to the ad host. Without a domain there is nothing to
 *   resolve it against, so the card falls back to the name rather than requesting a broken URL.
 *
 * These are usually SVG, which is why the app's image loader registers a decoder for it — see
 * [com.gocheap.flights.App]. Without that the fetch succeeds and the decode quietly yields
 * nothing, which looks exactly like an ad with no logo.
 */
internal fun adLogoUrl(logoUrl: String?, baseDomain: String): String? {
    val path = logoUrl?.trim()?.takeIf(String::isNotBlank)?.withoutQueryParams("padX", "padY")
        ?: return null
    if (path.startsWith("http://", ignoreCase = true) ||
        path.startsWith("https://", ignoreCase = true)
    ) {
        return path
    }
    val domain = baseDomain.trim().takeIf(String::isNotBlank) ?: return null
    return domain.removeSuffix("/") + "/" + path.removePrefix("/")
}

/**
 * The URL with the named query parameters removed, whatever their values and wherever they sit.
 *
 * String surgery rather than a URL parser because these arrive as relative paths, which no URL
 * type will take, and because a parser would re-encode the rest — and the one parameter this card
 * reads back, `bgColor=%23RRGGBB`, depends on its escaping surviving untouched.
 */
private fun String.withoutQueryParams(vararg names: String): String {
    val query = substringAfter('?', "").takeIf(String::isNotEmpty) ?: return this
    val kept = query
        .split('&')
        .filter { param -> names.none { param == it || param.startsWith("$it=") } }
    return substringBefore('?') + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
}

/**
 * The colour the logo is meant to sit on, read out of the logo URL itself.
 *
 * The host's image service takes the brand's background as a `bgColor=%23RRGGBB` parameter — the
 * `%23` is an escaped `#` — and a logo drawn for a coloured background disappears on a white one.
 * So the card reads the parameter back rather than guessing.
 *
 * Null for an ad whose URL carries no such parameter, or carries one that is not six hex digits,
 * which the card renders on its own muted surface instead.
 */
internal fun adLogoBackground(logoUrl: String?): Color? {
    val encoded = logoUrl?.substringAfter("bgColor=%23", "")?.take(6) ?: return null
    if (encoded.length != 6 || !encoded.all { it.isHexDigit() }) return null
    val rgb = encoded.toLongOrNull(radix = 16) ?: return null
    // Opaque: the parameter carries no alpha, and a colour with none is fully transparent.
    return Color(0xFF000000L or rgb)
}

/**
 * What to call the advertiser when there is no logo to show.
 *
 * The company name where the host sent one. Otherwise the supplier site's own second-level label
 * — `https://www.kayak.co.uk/` becomes "Kayak" — which is the reference app's fallback and is
 * right because the ads come from that supplier's own inventory.
 */
internal fun adSiteName(companyName: String?, baseDomain: String): String =
    companyName?.trim()?.takeIf(String::isNotBlank)
        ?: baseDomain
            .substringAfter("://")
            .substringBefore("/")
            .removePrefix("www.")
            .substringBefore(".")
            .takeIf(String::isNotBlank)
            ?.replaceFirstChar(Char::uppercaseChar)
        ?: UnnamedAdvertiser

/** Last resort, for an ad with no name and a country with no supplier site. */
private const val UnnamedAdvertiser = "Sponsored"

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
