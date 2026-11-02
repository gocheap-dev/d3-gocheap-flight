package com.gocheap.travel.ui.home.results.api

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.gocheap.travel.analytics.ga4.AnalyticsEvent
import com.gocheap.travel.analytics.ga4.TrackSheetView
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gocheap.travel.data.model.TripSearch
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.PrimaryButton
import com.gocheap.travel.ui.component.pressable
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightShapes
import com.gocheap.travel.ui.theme.FlightSpacing
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.pluralStringResource
import flighttp.shared.generated.resources.travellers_count
import flighttp.shared.generated.resources.dot_join
import com.gocheap.travel.ui.component.storedDateLabel
import com.gocheap.travel.ui.component.displayLabel
import flighttp.shared.generated.resources.where_to
import flighttp.shared.generated.resources.where_from
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.close
import flighttp.shared.generated.resources.modify_search
import flighttp.shared.generated.resources.departure
import flighttp.shared.generated.resources.from_label
import flighttp.shared.generated.resources.return_label
import flighttp.shared.generated.resources.search_flights_2
import flighttp.shared.generated.resources.swap_origin_and_destination
import flighttp.shared.generated.resources.to_label
import flighttp.shared.generated.resources.travellers
import flighttp.shared.generated.resources.add_date
import flighttp.shared.generated.resources.one_way
import flighttp.shared.generated.resources.round_trip

/**
 * Change the trip without leaving the results — the reference app's "Modify Search".
 *
 * Everything the home search card collects, in the same order: trip type, the two ends with a
 * swap between them, the dates, the party. Pressing Search re-runs from scratch; dismissing
 * throws the edits away and leaves the results untouched.
 *
 * It is the same set of pickers the home card opens, not copies of them — tapping From here
 * pushes the identical location page the search card pushes, so a place found one way is found
 * the same way the other. That is why the three field rows take callbacks rather than drawing
 * anything themselves.
 *
 * A bottom sheet where the reference uses a sheet pulled down from the top. The content and the
 * order are the reference's; the direction is this app's, because every other sheet here arrives
 * from the bottom and one that did not would read as a different app's screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModifySearchSheet(
    draft: TripSearch,
    onTripTypeChange: (Boolean) -> Unit,
    onSwap: () -> Unit,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit,
    onPickDates: () -> Unit,
    onPickTravellers: () -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackSheetView(AnalyticsEvent.MODIFY_SEARCH_SCREEN)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.modify_search),
                    style = MaterialTheme.typography.headlineMedium,
                    color = FlightColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = FlightIcons.Close,
                    contentDescription = stringResource(Res.string.close),
                    tint = FlightColors.TextSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .pressable(onClick = onDismiss, pressedScale = 1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            TripTypeToggle(isRoundTrip = draft.isRoundTrip, onChange = onTripTypeChange)

            Spacer(Modifier.height(12.dp))
            RouteFields(
                draft = draft,
                onSwap = onSwap,
                onPickOrigin = onPickOrigin,
                onPickDestination = onPickDestination,
            )

            Spacer(Modifier.height(10.dp))
            // Plainly conditional rather than wrapped in an AnimatedVisibility. Inside a Row that
            // one measures at full width while it is laying out, which squeezes the weighted
            // sibling beside it to nothing — the departure field vanished entirely and only the
            // return showed. A fade is not worth a field that is not there.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetField(
                    icon = FlightIcons.CalendarOutlined,
                    label = stringResource(Res.string.departure),
                    value = storedDateLabel(draft.departureDate),
                    onClick = onPickDates,
                    modifier = Modifier.weight(1f),
                )
                // Both dates come off one calendar page, so the return field opens the same picker
                // rather than a second one — which is also why it is not disabled on a one-way:
                // it is simply not shown.
                if (draft.isRoundTrip) {
                    SheetField(
                        icon = FlightIcons.CalendarOutlined,
                        label = stringResource(Res.string.return_label),
                        value = storedDateLabel(draft.returnDate)
                            .ifBlank { stringResource(Res.string.add_date) },
                        onClick = onPickDates,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            SheetField(
                icon = FlightIcons.PeopleOutlined,
                label = stringResource(Res.string.travellers),
                value = draft.partyLabel(),
                onClick = onPickTravellers,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = stringResource(Res.string.search_flights_2),
                onClick = onSearch,
                // A search needs both ends. The button says so by going flat rather than by
                // letting the tap through to an error.
                enabled = draft.from.isChosen && draft.to.isChosen,
            )
        }
    }
}

/** Round trip / One way, as a two-segment switch. */
@Composable
private fun TripTypeToggle(
    isRoundTrip: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FlightShapes.Pill)
            .background(FlightColors.SurfaceMuted)
            .padding(3.dp),
    ) {
        ToggleSegment(stringResource(Res.string.round_trip), isRoundTrip, { onChange(true) }, Modifier.weight(1f))
        ToggleSegment(stringResource(Res.string.one_way), !isRoundTrip, { onChange(false) }, Modifier.weight(1f))
    }
}

@Composable
private fun ToggleSegment(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .pressable(onClick = onClick, pressedScale = 1f)
            .clip(FlightShapes.Pill)
            .background(if (isSelected) FlightColors.Surface else androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) FlightColors.TextPrimary else FlightColors.TextSecondary,
        )
    }
}

/**
 * From and To, with the swap sitting between them.
 *
 * The swap is drawn over the divider rather than beside the fields, so it reads as acting on the
 * pair rather than on either one — and it is the only control here that changes the trip without
 * opening another page.
 */
@Composable
private fun RouteFields(
    draft: TripSearch,
    onSwap: () -> Unit,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetField(
                icon = FlightIcons.FlightTakeoffOutlined,
                label = stringResource(Res.string.from_label),
                value = draft.from.fieldLabel(stringResource(Res.string.where_from)),
                onClick = onPickOrigin,
                modifier = Modifier.fillMaxWidth(),
                trailingGap = SwapDiameter,
            )
            SheetField(
                icon = FlightIcons.FlightLandOutlined,
                label = stringResource(Res.string.to_label),
                value = draft.to.fieldLabel(stringResource(Res.string.where_to)),
                onClick = onPickDestination,
                modifier = Modifier.fillMaxWidth(),
                trailingGap = SwapDiameter,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .pressable(onClick = onSwap)
                .shadow(4.dp, CircleShape, spotColor = FlightColors.CardShadow)
                .size(SwapDiameter)
                .clip(CircleShape)
                .background(FlightColors.Surface)
                .border(1.dp, FlightColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FlightIcons.SwapVert,
                contentDescription = stringResource(Res.string.swap_origin_and_destination),
                tint = FlightColors.Primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** One tappable row: icon, the field's name, and whatever it currently holds. */
@Composable
private fun SheetField(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingGap: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Row(
        modifier = modifier
            .pressable(onClick = onClick)
            .clip(FlightShapes.Field)
            .background(FlightColors.SurfaceMuted)
            .border(1.dp, FlightColors.Border, FlightShapes.Field)
            .padding(horizontal = 14.dp, vertical = FlightSpacing.FieldVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FlightColors.Primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = FlightColors.TextSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = FlightColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Room for the swap button to sit over, so a long city name does not run underneath it.
        if (trailingGap > 0.dp) Spacer(Modifier.width(trailingGap))
    }
}

private val SwapDiameter = 38.dp

/** "LON · London", or the placeholder where this end has not been chosen yet. */
private fun com.gocheap.travel.data.model.Airport.fieldLabel(placeholder: String): String = when {
    !isChosen -> placeholder
    city.isNotBlank() -> "$code · $city"
    else -> code
}

/** "1 traveller · Economy". */
@Composable
private fun TripSearch.partyLabel(): String = stringResource(
    Res.string.dot_join,
    pluralStringResource(Res.plurals.travellers_count, passengers, passengers),
    stringResource(cabinClass.displayLabel),
)
