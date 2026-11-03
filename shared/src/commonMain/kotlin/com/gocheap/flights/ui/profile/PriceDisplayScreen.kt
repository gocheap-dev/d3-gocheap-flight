package com.gocheap.flights.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.MaterialTheme
import com.gocheap.flights.data.settings.AppSettingsState
import com.gocheap.flights.data.settings.FlightPriceDisplay
import com.gocheap.flights.data.settings.StayPriceDisplay
import com.gocheap.flights.ui.component.InnerPage
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.chosen_once_and_used_everywhere_a_price
import flighttp.shared.generated.resources.display_prices
import flighttp.shared.generated.resources.every_night_added_up
import flighttp.shared.generated.resources.flights
import flighttp.shared.generated.resources.hotels
import flighttp.shared.generated.resources.one_night_of_the_stay
import flighttp.shared.generated.resources.one_seat_whatever_the_party_size
import flighttp.shared.generated.resources.prices_for_all_passengers
import flighttp.shared.generated.resources.prices_for_the_whole_stay
import flighttp.shared.generated.resources.prices_per_night
import flighttp.shared.generated.resources.price_display_all_passengers
import flighttp.shared.generated.resources.price_display_per_night
import flighttp.shared.generated.resources.price_display_per_passenger
import flighttp.shared.generated.resources.price_display_whole_stay
import flighttp.shared.generated.resources.prices_per_passenger
import flighttp.shared.generated.resources.the_fare_multiplied_by_everyone_flying

/**
 * How prices are quoted, as the SDK's "Display prices" page collects it: one choice for flights and
 * one for stays, each applying the moment it is tapped rather than behind a confirm.
 *
 * The flights choice is honoured wherever this app prints a fare — see `PriceFormat`. The stays
 * choice is carried for the same reason the SDK carries it, and takes effect on any stay price the
 * app renders itself; the supplier's own results page quotes in whichever form it chooses.
 */
@Composable
fun PriceDisplayScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()

    PriceDisplayContent(
        settings = settings,
        onBack = onBack,
        onFlightChoice = viewModel::onFlightPricesPicked,
        onStayChoice = viewModel::onStayPricesPicked,
        modifier = modifier,
    )
}

@Composable
private fun PriceDisplayContent(
    settings: AppSettingsState,
    onBack: () -> Unit,
    onFlightChoice: (FlightPriceDisplay) -> Unit,
    onStayChoice: (StayPriceDisplay) -> Unit,
    modifier: Modifier = Modifier,
) {
    InnerPage(
        title = stringResource(Res.string.display_prices),
        onBack = onBack,
        modifier = modifier,
    ) {

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FlightSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(Res.string.chosen_once_and_used_everywhere_a_price),
                style = MaterialTheme.typography.bodySmall,
                color = FlightColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            SettingsSectionHeader(title = stringResource(Res.string.flights))

            SettingsChoiceRow(
                title = stringResource(Res.string.prices_per_passenger),
                subtitle = stringResource(Res.string.one_seat_whatever_the_party_size),
                isSelected = settings.flightPrices == FlightPriceDisplay.PerPassenger,
                onClick = { onFlightChoice(FlightPriceDisplay.PerPassenger) },
            )
            Spacer(Modifier.height(10.dp))
            SettingsChoiceRow(
                title = stringResource(Res.string.prices_for_all_passengers),
                subtitle = stringResource(Res.string.the_fare_multiplied_by_everyone_flying),
                isSelected = settings.flightPrices == FlightPriceDisplay.AllPassengers,
                onClick = { onFlightChoice(FlightPriceDisplay.AllPassengers) },
            )

            Spacer(Modifier.height(18.dp))
            SettingsSectionHeader(title = stringResource(Res.string.hotels))

            SettingsChoiceRow(
                title = stringResource(Res.string.prices_per_night),
                subtitle = stringResource(Res.string.one_night_of_the_stay),
                isSelected = settings.stayPrices == StayPriceDisplay.PerNight,
                onClick = { onStayChoice(StayPriceDisplay.PerNight) },
            )
            Spacer(Modifier.height(10.dp))
            SettingsChoiceRow(
                title = stringResource(Res.string.prices_for_the_whole_stay),
                subtitle = stringResource(Res.string.every_night_added_up),
                isSelected = settings.stayPrices == StayPriceDisplay.WholeStay,
                onClick = { onStayChoice(StayPriceDisplay.WholeStay) },
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Preview
@Composable
private fun PriceDisplayScreenPreview() {
    FlightTheme {
        PriceDisplayContent(
            settings = AppSettingsState(stayPrices = StayPriceDisplay.WholeStay),
            onBack = {},
            onFlightChoice = {},
            onStayChoice = {},
        )
    }
}

/**
 * The short name each choice goes by on the Profile row that previews it.
 *
 * Separate from the picker's own titles above: that page has room for the whole sentence
 * ("Prices per passenger"), the row that reads the choice back does not.
 *
 * The resource rather than the text, so the mapping is reachable from outside a composition —
 * [com.gocheap.flights.ui.profile.ProfileScreen] resolves it.
 */
internal val FlightPriceDisplay.summaryLabel: StringResource
    get() = when (this) {
        FlightPriceDisplay.PerPassenger -> Res.string.price_display_per_passenger
        FlightPriceDisplay.AllPassengers -> Res.string.price_display_all_passengers
    }

internal val StayPriceDisplay.summaryLabel: StringResource
    get() = when (this) {
        StayPriceDisplay.PerNight -> Res.string.price_display_per_night
        StayPriceDisplay.WholeStay -> Res.string.price_display_whole_stay
    }
