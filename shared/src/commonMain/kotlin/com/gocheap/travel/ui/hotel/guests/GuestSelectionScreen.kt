package com.gocheap.travel.ui.hotel.guests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gocheap.travel.data.model.StayRoom
import com.gocheap.travel.ui.component.FlightIcons
import com.gocheap.travel.ui.component.PrimaryButton
import com.gocheap.travel.ui.component.InnerPage
import com.gocheap.travel.ui.component.SecondaryButton
import com.gocheap.travel.ui.component.StepperRow
import com.gocheap.travel.ui.theme.FlightColors
import com.gocheap.travel.ui.theme.FlightSpacing
import com.gocheap.travel.ui.theme.FlightShapes
import com.gocheap.travel.ui.theme.FlightTheme
import flighttp.shared.generated.resources.Res
import com.gocheap.travel.ui.component.guestsAndRoomsLabel
import org.jetbrains.compose.resources.stringResource
import flighttp.shared.generated.resources.add_room
import flighttp.shared.generated.resources.add_room_2
import flighttp.shared.generated.resources.adults
import flighttp.shared.generated.resources.age_at_check_out
import flighttp.shared.generated.resources.cancel
import flighttp.shared.generated.resources.child_s_age
import flighttp.shared.generated.resources.children
import flighttp.shared.generated.resources.confirm
import flighttp.shared.generated.resources.how_old_they_will_be_at_check
import flighttp.shared.generated.resources.n_18_and_over
import flighttp.shared.generated.resources.rooms_and_guests
import flighttp.shared.generated.resources.tap_to_choose_an_age
import org.jetbrains.compose.resources.getString
import flighttp.shared.generated.resources.age_label
import flighttp.shared.generated.resources.aged_between
import flighttp.shared.generated.resources.child_numbered
import flighttp.shared.generated.resources.choose_age_for
import flighttp.shared.generated.resources.remove_room_numbered
import flighttp.shared.generated.resources.room_numbered

/**
 * The rooms and guests page, pushed from the hotel search card.
 *
 * The party is built a room at a time — adults, children, and each child's age — rather than as a
 * guest count with the rooms derived from it. The supplier prices a stay per room and per child's
 * age, so the split is part of the search rather than a presentation detail.
 */
@Composable
fun GuestSelectionScreen(
    initialRooms: List<StayRoom>,
    onBack: () -> Unit,
    onConfirm: (GuestSelection) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuestSelectionViewModel = viewModel { GuestSelectionViewModel(initialRooms) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessageId) {
        val message = state.snackbarMessage?.let { getString(it) } ?: return@LaunchedEffect
        viewModel.onSnackbarMessageShown()
        snackbarHostState.showSnackbar(message)
    }

    GuestSelectionContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAddRoom = viewModel::onAddRoom,
        onRemoveRoom = viewModel::onRemoveRoom,
        onIncreaseAdults = viewModel::onIncreaseAdults,
        onDecreaseAdults = viewModel::onDecreaseAdults,
        onIncreaseChildren = viewModel::onIncreaseChildren,
        onDecreaseChildren = viewModel::onDecreaseChildren,
        onChildAgePicked = viewModel::onChildAgePicked,
        onConfirm = { viewModel.onConfirm(onConfirm) },
        modifier = modifier,
    )
}

@Composable
private fun GuestSelectionContent(
    state: GuestSelectionUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddRoom: () -> Unit,
    onRemoveRoom: (Int) -> Unit,
    onIncreaseAdults: (Int) -> Unit,
    onDecreaseAdults: (Int) -> Unit,
    onIncreaseChildren: (Int) -> Unit,
    onDecreaseChildren: (Int) -> Unit,
    onChildAgePicked: (roomIndex: Int, childIndex: Int, age: Int) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which child the age sheet is open for, if any. Local because it is a question the page asks
    // and answers itself; only the chosen age reaches the ViewModel.
    var agePickerTarget by remember { mutableStateOf<ChildAgeTarget?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        InnerPage(
            title = stringResource(Res.string.rooms_and_guests),
            onBack = onBack,
        ) {

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = FlightSpacing.ScreenHorizontal, end = FlightSpacing.ScreenHorizontal, bottom = 20.dp),
            ) {
                itemsIndexed(state.rooms, key = { index, _ -> "room-$index" }) { index, room ->
                    RoomCard(
                        roomIndex = index,
                        room = room,
                        // The first room is the stay itself; the rest arrived by tapping "Add
                        // room", so only those offer a way back out.
                        canRemove = index > 0,
                        onRemove = { onRemoveRoom(index) },
                        onIncreaseAdults = { onIncreaseAdults(index) },
                        onDecreaseAdults = { onDecreaseAdults(index) },
                        onIncreaseChildren = { onIncreaseChildren(index) },
                        onDecreaseChildren = { onDecreaseChildren(index) },
                        onPickChildAge = { childIndex ->
                            agePickerTarget = ChildAgeTarget(index, childIndex)
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                item {
                    AddRoomButton(enabled = state.canAddRoom, onClick = onAddRoom)
                }
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
                    text = guestsAndRoomsLabel(guests = state.guestCount, rooms = state.roomCount),
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

    agePickerTarget?.let { target ->
        ChildAgeDialog(
            onDismiss = { agePickerTarget = null },
            onAgeSelected = { age ->
                onChildAgePicked(target.roomIndex, target.childIndex, age)
                agePickerTarget = null
            },
        )
    }
}

@Composable
private fun RoomCard(
    roomIndex: Int,
    room: RoomFormState,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onIncreaseAdults: () -> Unit,
    onDecreaseAdults: () -> Unit,
    onIncreaseChildren: () -> Unit,
    onDecreaseChildren: () -> Unit,
    onPickChildAge: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FlightShapes.Card)
            .background(FlightColors.Surface)
            .border(width = 1.dp, color = FlightColors.Border, shape = FlightShapes.Card)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.room_numbered, roomIndex + 1),
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.Primary,
                modifier = Modifier.weight(1f),
            )
            if (canRemove) {
                Icon(
                    imageVector = FlightIcons.Close,
                    contentDescription = stringResource(Res.string.remove_room_numbered, roomIndex + 1),
                    tint = FlightColors.TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onRemove),
                )
            }
        }

        StepperRow(
            icon = FlightIcons.PersonOutlined,
            title = stringResource(Res.string.adults),
            subtitle = stringResource(Res.string.n_18_and_over),
            value = room.adults,
            canDecrease = room.canDecreaseAdults,
            canIncrease = room.canIncreaseAdults,
            onDecrease = onDecreaseAdults,
            onIncrease = onIncreaseAdults,
        )

        HorizontalDivider(thickness = 1.dp, color = FlightColors.Divider)

        StepperRow(
            icon = FlightIcons.People,
            title = stringResource(Res.string.children),
            subtitle = stringResource(Res.string.aged_between, GuestSelection.MinChildAge, GuestSelection.MaxChildAge),
            value = room.children,
            canDecrease = room.canDecreaseChildren,
            canIncrease = room.canIncreaseChildren,
            onDecrease = onDecreaseChildren,
            onIncrease = onIncreaseChildren,
        )

        if (room.children > 0) {
            HorizontalDivider(thickness = 1.dp, color = FlightColors.Divider)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.age_at_check_out),
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            room.childAges.forEachIndexed { childIndex, age ->
                ChildAgeRow(
                    label = stringResource(Res.string.child_numbered, childIndex + 1),
                    age = age,
                    onClick = { onPickChildAge(childIndex) },
                )
                if (childIndex < room.childAges.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * A child with no age yet is drawn in the warning colour rather than greyed out: it is the one
 * thing standing between the page and Confirm, so it has to read as unfinished, not as optional.
 */
@Composable
private fun ChildAgeRow(
    label: String,
    age: Int?,
    onClick: () -> Unit,
) {
    val isMissing = age == null

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = FlightColors.TextPrimary,
            )
            if (isMissing) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.tap_to_choose_an_age),
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.Danger,
                )
            }
        }
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(38.dp)
                .clip(FlightShapes.Field)
                .border(
                    width = 1.dp,
                    color = if (isMissing) FlightColors.Danger else FlightColors.BorderStrong,
                    shape = FlightShapes.Field,
                )
                .clickable(onClickLabel = stringResource(Res.string.choose_age_for, label), onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = age?.toString() ?: stringResource(Res.string.age_label),
                style = MaterialTheme.typography.titleMedium,
                color = if (isMissing) FlightColors.Danger else FlightColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun AddRoomButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // Still tappable at the ceiling, so the tap is answered with the limit rather than with
    // nothing; the colour is what says it will not add another.
    val contentColor = if (enabled) FlightColors.Primary else FlightColors.TextTertiary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(FlightShapes.Button)
            .border(
                width = 1.dp,
                color = if (enabled) FlightColors.Primary else FlightColors.Border,
                shape = FlightShapes.Button,
            )
            .clickable(onClickLabel = stringResource(Res.string.add_room_2), onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.add_room),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The age picker. Only offers 0 to 17 — anyone older is an adult to the supplier, and is counted in
 * that column instead, so there is no such thing as an out-of-range pick here.
 */
@Composable
private fun ChildAgeDialog(
    onDismiss: () -> Unit,
    onAgeSelected: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.child_s_age),
                style = MaterialTheme.typography.titleLarge,
                color = FlightColors.TextPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.how_old_they_will_be_at_check),
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (GuestSelection.MinChildAge..GuestSelection.MaxChildAge).forEach { age ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(FlightShapes.Field)
                                .background(FlightColors.SurfaceMuted)
                                .clickable { onAgeSelected(age) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = age.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = FlightColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel), color = FlightColors.Primary)
            }
        },
        shape = FlightShapes.Sheet,
        containerColor = FlightColors.Surface,
    )
}

/** Which child the age dialog is open for. */
private data class ChildAgeTarget(
    val roomIndex: Int,
    val childIndex: Int,
)

@Preview
@Composable
private fun GuestSelectionScreenPreview() {
    FlightTheme {
        GuestSelectionContent(
            state = GuestSelectionUiState(
                rooms = listOf(
                    RoomFormState(adults = 2),
                    RoomFormState(adults = 1, childAges = listOf(7, null)),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAddRoom = {},
            onRemoveRoom = {},
            onIncreaseAdults = {},
            onDecreaseAdults = {},
            onIncreaseChildren = {},
            onDecreaseChildren = {},
            onChildAgePicked = { _, _, _ -> },
            onConfirm = {},
        )
    }
}
