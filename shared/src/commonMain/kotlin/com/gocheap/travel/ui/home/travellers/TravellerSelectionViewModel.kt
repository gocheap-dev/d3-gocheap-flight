package com.gocheap.travel.ui.home.travellers

import androidx.lifecycle.ViewModel
import com.gocheap.travel.data.model.CabinClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import flighttp.shared.generated.resources.at_most_adults
import flighttp.shared.generated.resources.at_most_children_and_infants
import flighttp.shared.generated.resources.at_most_travellers
import flighttp.shared.generated.resources.booking_needs_an_adult
import flighttp.shared.generated.resources.infant_on_lap_one_each
import flighttp.shared.generated.resources.no_children_to_remove
import flighttp.shared.generated.resources.no_infants_to_remove

data class TravellerSelectionUiState(
    val selection: TravellerSelection = TravellerSelection(),
    val snackbarMessage: StringResource? = null,
    val snackbarMessageId: Int = 0,
) {
}

/**
 * Holds the party while it is being built, and the rule behind each button: eight adults, fifteen
 * travellers, seven children and infants between them, and never more infants than laps.
 *
 * Every limit answers with the reason it stopped rather than a silent no-op — which of the four
 * rules a tap ran into is not obvious from the counts, and a button that greys out without saying
 * why is the thing this page used to get wrong.
 */
class TravellerSelectionViewModel(
    initial: TravellerSelection = TravellerSelection(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        TravellerSelectionUiState(
            selection = TravellerSelection.of(
                adults = initial.adults,
                children = initial.children,
                infants = initial.infants,
                cabinClass = initial.cabinClass,
            ),
        ),
    )
    val state: StateFlow<TravellerSelectionUiState> = _state.asStateFlow()

    fun onIncreaseAdults() {
        update(
            allowed = { it.canAddAdult },
            // Which ceiling was hit decides which of the two it is told about.
            message = {
                if (it.adults >= TravellerSelection.MaxAdults) MaxAdultsMessage else MaxTravellersMessage
            },
        ) { it.copy(adults = it.adults + 1) }
    }

    /**
     * Taking an adult away takes a lap with it, so an infant leaves with them rather than being
     * left on a booking that no longer has room to carry them.
     */
    fun onDecreaseAdults() {
        update(allowed = { it.canRemoveAdult }, message = { MinAdultsMessage }) {
            val adults = it.adults - 1
            it.copy(adults = adults, infants = it.infants.coerceAtMost(adults))
        }
    }

    fun onIncreaseChildren() {
        update(
            allowed = { it.canAddChild },
            message = {
                if (it.count >= TravellerSelection.MaxTravellers) {
                    MaxTravellersMessage
                } else {
                    MaxChildrenMessage
                }
            },
        ) { it.copy(children = it.children + 1) }
    }

    fun onDecreaseChildren() {
        update(allowed = { it.canRemoveChild }, message = { NoChildrenMessage }) {
            it.copy(children = it.children - 1)
        }
    }

    fun onIncreaseInfants() {
        update(
            allowed = { it.canAddInfant },
            message = {
                when {
                    it.infants >= it.adults -> InfantsPerAdultMessage
                    it.count >= TravellerSelection.MaxTravellers -> MaxTravellersMessage
                    else -> MaxChildrenMessage
                }
            },
        ) { it.copy(infants = it.infants + 1) }
    }

    fun onDecreaseInfants() {
        update(allowed = { it.canRemoveInfant }, message = { NoInfantsMessage }) {
            it.copy(infants = it.infants - 1)
        }
    }

    fun onCabinClassPicked(cabinClass: CabinClass) {
        _state.update { it.copy(selection = it.selection.copy(cabinClass = cabinClass)) }
    }

    /** Nothing here can be left half-written, so Confirm only ever hands the party back. */
    fun onConfirm(onPicked: (TravellerSelection) -> Unit) {
        onPicked(state.value.selection)
    }

    fun onSnackbarMessageShown() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun update(
        allowed: (TravellerSelection) -> Boolean,
        message: (TravellerSelection) -> StringResource,
        transform: (TravellerSelection) -> TravellerSelection,
    ) {
        _state.update { current ->
            val selection = current.selection
            if (!allowed(selection)) {
                return@update current.withMessage(message(selection))
            }
            current.copy(selection = transform(selection))
        }
    }

    private companion object {
        val MaxTravellersMessage =
            Res.string.at_most_travellers
        val MaxAdultsMessage =
            Res.string.at_most_adults
        val MinAdultsMessage = Res.string.booking_needs_an_adult
        val MaxChildrenMessage =
            Res.string.at_most_children_and_infants
        val InfantsPerAdultMessage = Res.string.infant_on_lap_one_each
        val NoChildrenMessage = Res.string.no_children_to_remove
        val NoInfantsMessage = Res.string.no_infants_to_remove
    }
}

private fun TravellerSelectionUiState.withMessage(message: StringResource): TravellerSelectionUiState = copy(
    snackbarMessage = message,
    // Bumped so the same message twice in a row still reaches the host, which keys on this.
    snackbarMessageId = snackbarMessageId + 1,
)
