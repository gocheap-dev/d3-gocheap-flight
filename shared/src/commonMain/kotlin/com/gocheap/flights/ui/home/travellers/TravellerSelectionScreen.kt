package com.gocheap.flights.ui.home.travellers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.flights.data.model.CabinClass
import com.gocheap.flights.ui.component.FlightIcons
import com.gocheap.flights.ui.component.PrimaryButton
import com.gocheap.flights.ui.component.InnerPage
import com.gocheap.flights.ui.component.SecondaryButton
import com.gocheap.flights.ui.component.StepperRow
import com.gocheap.flights.ui.theme.FlightColors
import com.gocheap.flights.ui.theme.FlightSpacing
import com.gocheap.flights.ui.theme.FlightShapes
import com.gocheap.flights.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import com.gocheap.flights.ui.component.travellerLabel
import com.gocheap.flights.ui.component.displayLabel
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.adults
import flighttp.shared.generated.resources.aged_2_to_17_in_their_own
import flighttp.shared.generated.resources.cabin
import flighttp.shared.generated.resources.children
import flighttp.shared.generated.resources.infants
import flighttp.shared.generated.resources.n_18_and_over
import flighttp.shared.generated.resources.travellers
import flighttp.shared.generated.resources.under_2_on_an_adult_s_lap
import flighttp.shared.generated.resources.cancel
import flighttp.shared.generated.resources.confirm
import org.jetbrains.compose.resources.getString

/**
 * The travellers and cabin page, pushed from the flight search card.
 *
 * The party is built the way an airline fares it — adults, children in seats, infants on laps —
 * and the cabin is chosen here too, rather than being fixed at economy for every search. Built on
 * the same [StepperRow] and confirm-footer shape as the hotel guests page, so the two read as one
 * family.
 */
@Composable
fun TravellerSelectionScreen(
    initial: TravellerSelection,
    onBack: () -> Unit,
    onConfirm: (TravellerSelection) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TravellerSelectionViewModel = viewModel { TravellerSelectionViewModel(initial) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessageId) {
        val message = state.snackbarMessage?.let { getString(it) } ?: return@LaunchedEffect
        viewModel.onSnackbarMessageShown()
        snackbarHostState.showSnackbar(message)
    }

    TravellerSelectionContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onIncreaseAdults = viewModel::onIncreaseAdults,
        onDecreaseAdults = viewModel::onDecreaseAdults,
        onIncreaseChildren = viewModel::onIncreaseChildren,
        onDecreaseChildren = viewModel::onDecreaseChildren,
        onIncreaseInfants = viewModel::onIncreaseInfants,
        onDecreaseInfants = viewModel::onDecreaseInfants,
        onCabinClassPicked = viewModel::onCabinClassPicked,
        onConfirm = { viewModel.onConfirm(onConfirm) },
        modifier = modifier,
    )
}

@Composable
private fun TravellerSelectionContent(
    state: TravellerSelectionUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onIncreaseAdults: () -> Unit,
    onDecreaseAdults: () -> Unit,
    onIncreaseChildren: () -> Unit,
    onDecreaseChildren: () -> Unit,
    onIncreaseInfants: () -> Unit,
    onDecreaseInfants: () -> Unit,
    onCabinClassPicked: (CabinClass) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selection = state.selection

    Box(modifier = modifier.fillMaxSize()) {
        InnerPage(
            title = stringResource(Res.string.travellers),
            onBack = onBack,
        ) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FlightSpacing.ScreenHorizontal),
            ) {
                StepperRow(
                    icon = FlightIcons.PersonOutlined,
                    title = stringResource(Res.string.adults),
                    subtitle = stringResource(Res.string.n_18_and_over),
                    value = selection.adults,
                    canDecrease = selection.canRemoveAdult,
                    canIncrease = selection.canAddAdult,
                    onDecrease = onDecreaseAdults,
                    onIncrease = onIncreaseAdults,
                )

                HorizontalDivider(thickness = 1.dp, color = FlightColors.Divider)

                StepperRow(
                    icon = FlightIcons.People,
                    title = stringResource(Res.string.children),
                    subtitle = stringResource(Res.string.aged_2_to_17_in_their_own),
                    value = selection.children,
                    canDecrease = selection.canRemoveChild,
                    canIncrease = selection.canAddChild,
                    onDecrease = onDecreaseChildren,
                    onIncrease = onIncreaseChildren,
                )

                HorizontalDivider(thickness = 1.dp, color = FlightColors.Divider)

                StepperRow(
                    icon = FlightIcons.ChildFriendly,
                    title = stringResource(Res.string.infants),
                    subtitle = stringResource(Res.string.under_2_on_an_adult_s_lap),
                    value = selection.infants,
                    canDecrease = selection.canRemoveInfant,
                    canIncrease = selection.canAddInfant,
                    onDecrease = onDecreaseInfants,
                    onIncrease = onIncreaseInfants,
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(Res.string.cabin),
                    style = MaterialTheme.typography.titleLarge,
                    color = FlightColors.TextPrimary,
                )

                Spacer(Modifier.height(14.dp))

                // Two to a row, in the order the cabins sit on the plane.
                CabinClass.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { cabin ->
                            CabinChip(
                                cabinClass = cabin,
                                isSelected = cabin == selection.cabinClass,
                                onClick = { onCabinClassPicked(cabin) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(8.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FlightColors.Surface)
                    .padding(horizontal = FlightSpacing.ScreenHorizontal, vertical = 16.dp),
            ) {
                HorizontalDivider(thickness = 1.dp, color = FlightColors.Divider)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = travellerLabel(state.selection),
                    style = MaterialTheme.typography.titleMedium,
                    color = FlightColors.TextPrimary,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecondaryButton(
                        text = stringResource(Res.string.cancel),
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = stringResource(Res.string.confirm),
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp),
        )
    }
}

@Composable
private fun CabinChip(
    cabinClass: CabinClass,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(FlightShapes.Field)
            .background(if (isSelected) FlightColors.Primary else FlightColors.SurfaceMuted)
            .border(
                width = 1.dp,
                color = if (isSelected) FlightColors.Primary else FlightColors.Border,
                shape = FlightShapes.Field,
            )
            .clickable(onClickLabel = stringResource(cabinClass.displayLabel), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(cabinClass.displayLabel),
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) FlightColors.OnPrimary else FlightColors.TextPrimary,
        )
    }
}

@Preview
@Composable
private fun TravellerSelectionScreenPreview() {
    FlightTheme {
        TravellerSelectionContent(
            state = TravellerSelectionUiState(
                selection = TravellerSelection(
                    adults = 2,
                    children = 1,
                    infants = 1,
                    cabinClass = CabinClass.Business,
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onIncreaseAdults = {},
            onDecreaseAdults = {},
            onIncreaseChildren = {},
            onDecreaseChildren = {},
            onIncreaseInfants = {},
            onDecreaseInfants = {},
            onCabinClassPicked = {},
            onConfirm = {},
        )
    }
}
