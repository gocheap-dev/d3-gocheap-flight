package com.gocheap.flights.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gocheap.flights.data.model.LocationSuggestion
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.clear_recent_search_item
import flighttp.shared.generated.resources.date_range
import flighttp.shared.generated.resources.days_count
import flighttp.shared.generated.resources.dot_join
import flighttp.shared.generated.resources.dot_join_3
import flighttp.shared.generated.resources.guests_count
import flighttp.shared.generated.resources.nights_count
import flighttp.shared.generated.resources.one_way
import flighttp.shared.generated.resources.rental_subtitle
import org.jetbrains.compose.resources.pluralStringResource

/** One card in the "Recent Searches" row. [id] is the key its tab resolves back to a real search. */
data class RecentSearchItem(
    val id: String,
    val title: String,
    val subtitle: RecentSearchSubtitle,
)

/**
 * The second line of a recent-search card, as its parts rather than as a sentence.
 *
 * The tabs build these outside a composition, where a resource cannot be resolved — and the dates
 * they hold are stored in English besides. Keeping the parts apart until [recentSubtitleText]
 * draws them is what lets the line read in the user's language and the stored date stay parseable.
 */
sealed interface RecentSearchSubtitle {
    /** Already reader-facing — a place name off the supplier, say. */
    data class Text(val value: String) : RecentSearchSubtitle

    /** A trip: the dates it was run for, or the single date and "One way". */
    data class Trip(val departure: String, val returnDate: String?) : RecentSearchSubtitle

    data class Rental(val pickupDate: String, val pickupTime: String, val days: Int) :
        RecentSearchSubtitle

    data class Stay(val checkInDate: String, val nights: Int, val guests: Int) :
        RecentSearchSubtitle
}

@Composable
internal fun recentSubtitleText(subtitle: RecentSearchSubtitle): String = when (subtitle) {
    is RecentSearchSubtitle.Text -> subtitle.value
    is RecentSearchSubtitle.Trip -> subtitle.returnDate?.let { back ->
        stringResource(
            Res.string.date_range,
            storedDateLabel(subtitle.departure),
            storedDateLabel(back),
        )
    } ?: stringResource(
        Res.string.dot_join,
        storedDateLabel(subtitle.departure),
        stringResource(Res.string.one_way),
    )

    is RecentSearchSubtitle.Rental -> stringResource(
        Res.string.rental_subtitle,
        storedDateLabel(subtitle.pickupDate),
        subtitle.pickupTime,
        pluralStringResource(Res.plurals.days_count, subtitle.days, subtitle.days),
    )

    is RecentSearchSubtitle.Stay -> stringResource(
        Res.string.dot_join_3,
        storedDateLabel(subtitle.checkInDate),
        pluralStringResource(Res.plurals.nights_count, subtitle.nights, subtitle.nights),
        pluralStringResource(Res.plurals.guests_count, subtitle.guests, subtitle.guests),
    )
}

/**
 * The searches already run on this tab, most recent first.
 *
 * Callers are expected to skip the whole section — header included — while [items] is empty; this
 * row draws nothing on its own in that case.
 *
 * Both rows scroll horizontally and pad their *contents* rather than themselves, so the cards run
 * off the screen edge instead of stopping short at a 20dp gutter.
 *
 * [onClear] drops the one card it is called with. The row holds no state of its own, so the caller
 * forgetting that search is what makes the card go — which is also what keeps it gone across a
 * relaunch.
 */
@Composable
fun RecentSearchRow(
    items: List<RecentSearchItem>,
    onClick: (RecentSearchItem) -> Unit,
    onClear: (RecentSearchItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = CardShadowGutter),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = { it.id }) { item ->
            RecentSearchCard(
                item = item,
                onClick = { onClick(item) },
                onClear = { onClear(item) },
            )
        }
    }
}

/**
 * Destinations the supplier covers, offered as a shortcut into the search card above — two to a
 * row, down the page.
 *
 * Laid out by hand from [Column] and [Row] rather than with a `LazyVerticalGrid`: all three tabs
 * put this inside a `verticalScroll` column, which measures its children against an unbounded
 * height, and a lazy grid refuses that outright. Nothing is lost by it — the supplier sends six
 * rows, so there is no recycling worth having, and this way the whole grid scrolls with the page
 * instead of trapping a scroller inside one.
 */
@Composable
fun PopularLocationGrid(
    items: List<LocationSuggestion>,
    onClick: (LocationSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = CardShadowGutter),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
    ) {
        items.chunked(GridColumns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                row.forEach { location ->
                    PopularLocationCard(
                        location = location,
                        onClick = { onClick(location) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // An odd last row keeps its card to one column rather than letting it stretch
                // across both, so the grid reads as a grid down to the final item.
                repeat(GridColumns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A saved search drawn as a ticket stub, after the reference's "Saved trips" row: the route across
 * the top, a dashed tear with a bite out of either side, and the dates on the counterfoil below.
 *
 * The card carries whatever two lines its tab put in [RecentSearchItem] — a flight route and its
 * dates, a city and its nights — so the stub shape is the only thing the three tabs share here.
 */
@Composable
private fun RecentSearchCard(
    item: RecentSearchItem,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    // Measured from the laid-out tear line so the notches always cut level with the dashes.
    var tearCenterY by remember { mutableStateOf(0f) }
    val shape = remember(tearCenterY) {
        BoardingPassShape(notchCenterY = tearCenterY, cornerRadius = 20.dp, notchRadius = 11.dp)
    }

    Box(modifier = Modifier.width(248.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 10.dp, shape = shape, spotColor = FlightColors.CardShadow)
                .clip(shape)
                .background(FlightColors.Surface)
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(FlightColors.PrimarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = FlightIcons.Clock,
                        contentDescription = null,
                        tint = FlightColors.Primary,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = FlightColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Room kept clear on the right for the clear button sitting over the card.
                    modifier = Modifier.weight(1f).padding(end = 18.dp),
                )
            }

            TearLine(onPositioned = { tearCenterY = it })

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = recentSubtitleText(item.subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlightColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = FlightIcons.ChevronRight,
                    contentDescription = null,
                    tint = FlightColors.TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Its own clickable over the card's: a tap on the cross clears the card rather than running
        // the search, which is the enclosing Column's job. Named for the card it clears, so a
        // screen reader moving along the row says which search is going.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .clip(CircleShape)
                .background(FlightColors.SurfaceMuted)
                .clickable(
                    onClickLabel = stringResource(Res.string.clear_recent_search_item, item.title),
                    onClick = onClear,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FlightIcons.Close,
                contentDescription = stringResource(Res.string.clear_recent_search_item, item.title),
                tint = FlightColors.TextSecondary,
                modifier = Modifier.size(20.dp).padding(4.dp),
            )
        }
    }
}

/**
 * One tile in the grid: the photograph across the top, the place named underneath.
 *
 * Stacked rather than side by side. At half the screen's width there is not room for a thumbnail
 * and two lines of text in one row without the city name running out of it, and the picture is
 * worth more space than a 56dp square gave it anyway.
 *
 * Falls back to the icon tile when the row arrived without a photograph, drawn at the same height
 * as one so a list with a gap in it still lines up.
 */
@Composable
private fun PopularLocationCard(
    location: LocationSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = FlightShapes.Card, spotColor = FlightColors.CardShadow)
            .clip(FlightShapes.Card)
            .background(FlightColors.Surface)
            .clickable(onClick = onClick),
    ) {
        if (location.imageUrl != null) {
            AsyncImage(
                model = location.imageUrl,
                contentDescription = null,
                // Crops rather than stretches: the supplier's squares and anything a later refresh
                // of the list might carry both have to fill the same band.
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ImageHeight)
                    // Stands in for the photo until it arrives, so the grid does not reflow when it
                    // does — and stays as-is if the image never loads at all.
                    .background(FlightColors.SurfaceMuted),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ImageHeight)
                    .background(FlightColors.PrimarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FlightIcons.Place,
                    contentDescription = null,
                    tint = FlightColors.Primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = location.title,
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = location.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val ScreenGutter = FlightSpacing.ScreenHorizontal

/** Vertical breathing room inside the scroller so the cards' shadows are not clipped away. */
private val CardShadowGutter = 6.dp

/** The popular-locations grid: two to a row, with the same gap between them as down them. */
private const val GridColumns = 2
private val GridSpacing = 14.dp

/** Tall enough to be a photograph rather than a thumbnail at half the screen's width. */
private val ImageHeight = 104.dp


@Preview
@Composable
private fun RecentSearchCardPreview() {
    FlightTheme {
        RecentSearchCard(
            item = RecentSearchItem(
                id = "1",
                title = "SFO → JFK",
                subtitle = RecentSearchSubtitle.Text("Oct 15, 10:00 · 3 days"),
            ),
            onClick = {},
            onClear = {},
        )
    }
}
