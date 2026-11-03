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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.gocheap.flights.analytics.ga4.AnalyticsEvent
import com.gocheap.flights.analytics.ga4.TrackSheetView
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gocheap.flights.data.model.flight.api.CurrencyInfo
import com.gocheap.flights.data.model.flight.api.FlightDeal
import com.gocheap.flights.data.model.flight.api.FlightOption
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.pressable
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightSpacing
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.book_this_flight
import flighttp.shared.generated.resources.no_booking_sites_are_offering_this_flight
import flighttp.shared.generated.resources.booking_site_single
import flighttp.shared.generated.resources.cheapest_badge
import flighttp.shared.generated.resources.separate_tickets
import org.jetbrains.compose.resources.pluralStringResource
import flighttp.shared.generated.resources.booking_sites_count

/**
 * One bookable offer, with the one thing about its provider the row has to show.
 *
 * [isSplit] belongs to the provider rather than the deal, so flattening the two levels into one
 * list would lose it — and it is the single most important thing on the row, because a split fare
 * is usually the cheapest and carries a risk the others do not.
 */
internal data class FlightOffer(
    val deal: FlightDeal,
    val isSplit: Boolean,
)

/**
 * Every way this itinerary can actually be booked, cheapest first.
 *
 * Flattened across providers so the list is one price-ordered set rather than groups the user has
 * to compare across, and filtered to deals that carry a link — one that does not cannot be booked,
 * and a row that goes nowhere is worse than no row.
 *
 * Shared by the sheet and by the results list's tap handler, which has to know how many offers
 * there are before deciding whether a sheet is worth opening at all. Two copies of this would be
 * two opinions about what counts as bookable, and the list would then disagree with the sheet it
 * opens.
 */
internal fun FlightOption.bookableOffers(): List<FlightOffer> = providers
    .orEmpty()
    .flatMap { provider ->
        provider.deals.orEmpty().map { deal -> FlightOffer(deal, provider.isSplit == true) }
    }
    .filter { !it.deal.deeplink.isNullOrBlank() }
    .sortedBy { it.deal.price ?: Double.MAX_VALUE }

/**
 * Who will sell this itinerary, and for how much.
 *
 * A sheet rather than a page: picking a seller is a step inside choosing a flight, not a
 * destination of its own, and the card the user tapped stays visible behind it.
 *
 * A split fare — two separate tickets assembled into one journey — is labelled as such. It is
 * usually the cheapest row in the list and it carries a risk the others do not: a missed
 * connection between two unrelated tickets is the traveller's problem, not the airline's. The
 * badge is the only warning the user gets before they leave the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlightDealsSheet(
    option: FlightOption,
    currency: CurrencyInfo?,
    onSelect: (FlightDeal) -> Unit,
    onDismiss: () -> Unit,
) {
    TrackSheetView(AnalyticsEvent.DEALS_SCREEN)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val offers = option.bookableOffers()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FlightColors.Surface,
        shape = FlightShapes.Sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlightSpacing.CardPadding)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = stringResource(Res.string.book_this_flight),
                style = MaterialTheme.typography.headlineMedium,
                color = FlightColors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = pluralStringResource(Res.plurals.booking_sites_count, offers.size, offers.size),
                style = MaterialTheme.typography.labelSmall,
                color = FlightColors.TextSecondary,
            )
            Spacer(Modifier.height(14.dp))

            if (offers.isEmpty()) {
                // Every offer on this itinerary came without a link to open. Nothing to do but say
                // so — a row that cannot be booked is worse than no row.
                Text(
                    text = stringResource(Res.string.no_booking_sites_are_offering_this_flight),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlightColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = SheetMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(offers.size) { index ->
                        val offer = offers[index]
                        DealRow(
                            deal = offer.deal,
                            currency = currency,
                            isSplit = offer.isSplit,
                            // Only the first row is the cheapest, and the list is sorted by price.
                            isCheapest = index == 0 && offers.size > 1,
                            onClick = { onSelect(offer.deal) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DealRow(
    deal: FlightDeal,
    currency: CurrencyInfo?,
    isSplit: Boolean,
    isCheapest: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(FlightShapes.Field)
            .background(FlightColors.Surface)
            .border(1.dp, FlightColors.Border, FlightShapes.Field)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(FlightColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FlightIcons.Globe,
                contentDescription = null,
                tint = FlightColors.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
            if (!deal.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = deal.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deal.name?.takeIf(String::isNotBlank) ?: stringResource(Res.string.booking_site_single),
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val notes = buildList {
                if (isCheapest) add(stringResource(Res.string.cheapest_badge))
                if (isSplit) add(stringResource(Res.string.separate_tickets))
                deal.fareFamily?.displayName?.takeIf(String::isNotBlank)?.let(::add)
            }
            if (notes.isNotEmpty()) {
                Text(
                    text = notes.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    // Amber where the fare is split, because that note is a caveat rather than a
                    // feature and should not read like the "Cheapest" beside it.
                    color = if (isSplit) FlightColors.Warning else FlightColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        Text(
            text = formatPriceCompact(deal.price, currency),
            style = MaterialTheme.typography.headlineSmall,
            color = FlightColors.TextPrimary,
        )
        Icon(
            imageVector = FlightIcons.ChevronRight,
            contentDescription = null,
            tint = FlightColors.TextTertiary,
            modifier = Modifier.size(18.dp).padding(start = 2.dp),
        )
    }
}

private val SheetMaxHeight = 420.dp
