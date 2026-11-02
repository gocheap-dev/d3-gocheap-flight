package com.gocheap.travel.ui.car.location

import com.gocheap.travel.data.model.car.CarLocation
import com.gocheap.travel.ui.home.location.LocationTargets
import kotlinx.serialization.json.Json
import flighttp.shared.generated.resources.Res
import org.jetbrains.compose.resources.StringResource
import flighttp.shared.generated.resources.drop_off_location_2
import flighttp.shared.generated.resources.pick_up_location_2

/** What the rental location page hands back: the row that was tapped, plus the field it fills. */
data class CarLocationSelection(
    /** One of [LocationTargets.Pickup] or [LocationTargets.Dropoff], echoed back untouched. */
    val target: String,
    val location: CarLocation,
)

/**
 * The page's own heading, derived from the field it was opened for rather than carried in the
 * route: both ends of a one-way rental push the same destination, and the target already says
 * which one is being filled.
 *
 * Named after the field rather than asked as a question the way the sheet did — a top bar gives it
 * one centred line, which "Where do you want to pick up?" would spend on an ellipsis.
 */
internal fun carLocationTitle(target: String): StringResource = when (target) {
    LocationTargets.Dropoff -> Res.string.drop_off_location_2
    else -> Res.string.pick_up_location_2
}

/**
 * The picked row travels back through the caller's saved state, which only takes values it can
 * write out — so it goes as its own JSON rather than as the bare id the flight picker sends. These
 * rows come from the supplier's autocomplete, not a catalogue this app ships, so an id alone would
 * leave the Cars card with nothing to resolve it against.
 */
internal fun CarLocation.encodeSelection(): String =
    SelectionJson.encodeToString(CarLocation.serializer(), this)

/** Null for anything unreadable — the same shrug the recents store takes, for the same reason. */
internal fun decodeCarLocation(raw: String?): CarLocation? {
    val encoded = raw ?: return null
    return try {
        SelectionJson.decodeFromString(CarLocation.serializer(), encoded)
    } catch (error: Exception) {
        null
    }
}

private val SelectionJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
