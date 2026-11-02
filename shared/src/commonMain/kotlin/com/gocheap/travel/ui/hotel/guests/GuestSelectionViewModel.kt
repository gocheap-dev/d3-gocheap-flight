package com.gocheap.travel.ui.hotel.guests

import androidx.lifecycle.ViewModel
import com.gocheap.travel.data.model.StayRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import flighttp.shared.generated.resources.at_most_guests_per_room
import flighttp.shared.generated.resources.at_most_rooms
import flighttp.shared.generated.resources.choose_an_age_for_every_child
import flighttp.shared.generated.resources.every_room_needs_an_adult
import flighttp.shared.generated.resources.room_has_no_children_to_remove

/**
 * One room as the page edits it, which is not quite [StayRoom]: a child added here has no age yet,
 * and that gap is the whole point — it is what the page asks the user to fill, and what stops the
 * selection being handed back half-written.
 */
data class RoomFormState(
    val adults: Int = 1,
    val childAges: List<Int?> = emptyList(),
) {
    val children: Int get() = childAges.size
    val totalGuests: Int get() = adults + children

    val canIncreaseAdults: Boolean get() = totalGuests < GuestSelection.MaxGuestsPerRoom
    val canDecreaseAdults: Boolean get() = adults > GuestSelection.MinAdultsPerRoom
    val canIncreaseChildren: Boolean get() = totalGuests < GuestSelection.MaxGuestsPerRoom
    val canDecreaseChildren: Boolean get() = children > 0

    /** Every child in this room has an age the supplier would take. */
    val isComplete: Boolean
        get() = childAges.all { age ->
            age != null && age in GuestSelection.MinChildAge..GuestSelection.MaxChildAge
        }
}

data class GuestSelectionUiState(
    val rooms: List<RoomFormState> = listOf(RoomFormState()),
    val snackbarMessage: StringResource? = null,
    val snackbarMessageId: Int = 0,
) {
    val roomCount: Int get() = rooms.size
    val guestCount: Int get() = rooms.sumOf { it.totalGuests }
    val canAddRoom: Boolean get() = rooms.size < GuestSelection.MaxRooms

    /** Nothing can be handed back while a child is still ageless. */
    val isComplete: Boolean get() = rooms.all { it.isComplete }

}

/**
 * Holds the rooms while they are being edited, and the one rule behind each button: a room takes
 * four guests, needs an adult, and a search takes eight rooms.
 *
 * Every limit answers with a message rather than a silent no-op, because a disabled button that
 * does not say why is the thing this page used to get wrong. The buttons are still disabled at the
 * edges — the messages are for the taps that land anyway, which is every tap on iOS where the whole
 * row is one hit target.
 */
class GuestSelectionViewModel(
    initialRooms: List<StayRoom> = listOf(StayRoom()),
) : ViewModel() {
    private val _state = MutableStateFlow(
        GuestSelectionUiState(rooms = GuestSelection.of(initialRooms).rooms.map { it.toForm() }),
    )
    val state: StateFlow<GuestSelectionUiState> = _state.asStateFlow()

    fun onAddRoom() {
        _state.update { current ->
            if (!current.canAddRoom) {
                current.withMessage(Res.string.at_most_rooms)
            } else {
                current.copy(rooms = current.rooms + RoomFormState())
            }
        }
    }

    /** The first room is the stay itself and cannot go; the rest are removable. */
    fun onRemoveRoom(roomIndex: Int) {
        _state.update { current ->
            if (roomIndex !in current.rooms.indices || current.rooms.size == 1) {
                current
            } else {
                current.copy(
                    rooms = current.rooms.filterIndexed { index, _ -> index != roomIndex },
                )
            }
        }
    }

    fun onIncreaseAdults(roomIndex: Int) {
        updateRoom(roomIndex, guard = { it.canIncreaseAdults }, message = MaxGuestsMessage) {
            it.copy(adults = it.adults + 1)
        }
    }

    fun onDecreaseAdults(roomIndex: Int) {
        updateRoom(roomIndex, guard = { it.canDecreaseAdults }, message = MinAdultsMessage) {
            it.copy(adults = it.adults - 1)
        }
    }

    /** A new child arrives without an age, which [onChildAgePicked] then fills. */
    fun onIncreaseChildren(roomIndex: Int) {
        updateRoom(roomIndex, guard = { it.canIncreaseChildren }, message = MaxGuestsMessage) {
            it.copy(childAges = it.childAges + null)
        }
    }

    /** Drops the last child in the room, and the age that was picked for them with it. */
    fun onDecreaseChildren(roomIndex: Int) {
        updateRoom(roomIndex, guard = { it.canDecreaseChildren }, message = NoChildrenMessage) {
            it.copy(childAges = it.childAges.dropLast(1))
        }
    }

    fun onChildAgePicked(roomIndex: Int, childIndex: Int, age: Int) {
        updateRoom(roomIndex) { room ->
            if (childIndex !in room.childAges.indices) {
                room
            } else {
                room.copy(
                    childAges = room.childAges.mapIndexed { index, current ->
                        if (index == childIndex) {
                            age.coerceIn(GuestSelection.MinChildAge, GuestSelection.MaxChildAge)
                        } else {
                            current
                        }
                    },
                )
            }
        }
    }

    /**
     * Hands the rooms back, or says what is missing.
     *
     * The button stays tappable while a child has no age rather than greying out, so the tap that
     * means "I'm done" is answered with the reason instead of with nothing.
     */
    fun onConfirm(onPicked: (GuestSelection) -> Unit) {
        val current = state.value
        if (!current.isComplete) {
            _state.update { it.withMessage(Res.string.choose_an_age_for_every_child) }
            return
        }
        onPicked(GuestSelection.of(current.rooms.map { it.toStayRoom() }))
    }

    fun onSnackbarMessageShown() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun updateRoom(
        roomIndex: Int,
        guard: (RoomFormState) -> Boolean = { true },
        message: StringResource? = null,
        transform: (RoomFormState) -> RoomFormState,
    ) {
        _state.update { current ->
            val room = current.rooms.getOrNull(roomIndex) ?: return@update current
            if (!guard(room)) {
                return@update if (message == null) current else current.withMessage(message)
            }
            current.copy(
                rooms = current.rooms.mapIndexed { index, existing ->
                    if (index == roomIndex) transform(existing) else existing
                },
            )
        }
    }

    private companion object {
        val MaxGuestsMessage =
            Res.string.at_most_guests_per_room
        val MinAdultsMessage = Res.string.every_room_needs_an_adult
        val NoChildrenMessage = Res.string.room_has_no_children_to_remove
    }
}

/** A saved room opens the page as it was booked; its ages are already known. */
private fun StayRoom.toForm(): RoomFormState = RoomFormState(
    adults = adults,
    childAges = childAges,
)

/** Only called once the room is complete, so the ages are there to be read. */
private fun RoomFormState.toStayRoom(): StayRoom = StayRoom(
    adults = adults,
    childAges = childAges.filterNotNull(),
)

private fun GuestSelectionUiState.withMessage(message: StringResource): GuestSelectionUiState = copy(
    snackbarMessage = message,
    // Bumped so the same message twice in a row still reaches the host, which keys on this.
    snackbarMessageId = snackbarMessageId + 1,
)
