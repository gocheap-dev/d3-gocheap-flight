package com.gocheap.travel.ui.hotel.guests

import com.gocheap.travel.data.model.StayRoom

/**
 * What the guest page collects and hands back: the rooms, and who is in each of them.
 *
 * The page used to collect two numbers — guests and rooms — which said nothing about how the party
 * was split or how old the children were. Both are priced on, so the selection is now the rooms
 * themselves and the counts are read off them.
 */
data class GuestSelection(
    val rooms: List<StayRoom>,
) {
    val roomCount: Int get() = rooms.size
    val guestCount: Int get() = rooms.sumOf { it.guests }

    companion object {
        const val MaxRooms = 8
        const val MinAdultsPerRoom = 1

        /** The supplier books four to a room, adults and children alike. */
        const val MaxGuestsPerRoom = 4

        /** Anyone 18 or over is an adult to the supplier, and is counted in that column instead. */
        const val MinChildAge = 0
        const val MaxChildAge = 17

        /**
         * Pulls any list of rooms inside the limits above, so a selection cannot be built in a
         * state the search would not accept — whichever side it arrived from, a saved search, a
         * route argument, or the page itself.
         */
        fun of(rooms: List<StayRoom>): GuestSelection = GuestSelection(
            rooms.take(MaxRooms).map { it.clamped() }.ifEmpty { listOf(StayRoom()) },
        )

        private fun StayRoom.clamped(): StayRoom {
            val clampedAdults = adults.coerceIn(MinAdultsPerRoom, MaxGuestsPerRoom)
            return StayRoom(
                adults = clampedAdults,
                childAges = childAges
                    .take(MaxGuestsPerRoom - clampedAdults)
                    .map { it.coerceIn(MinChildAge, MaxChildAge) },
            )
        }
    }
}

/**
 * The rooms as one route argument: rooms split by [RoomSeparator], and within a room the adult
 * count followed by each child's age, split by [FieldSeparator].
 *
 * So "2_1-5-7" is a double followed by a room holding one adult and children aged 5 and 7. A
 * compact string rather than the JSON the destination picker travels as, because this one goes out
 * in a route path, which takes neither slashes nor spaces.
 */
internal fun List<StayRoom>.encodeRooms(): String = joinToString(RoomSeparator) { room ->
    (listOf(room.adults) + room.childAges).joinToString(FieldSeparator)
}

/**
 * The inverse, falling back to a single default room for anything unreadable — the same shrug the
 * other pickers take, and the page has to open onto something either way.
 */
internal fun decodeStayRooms(raw: String?): List<StayRoom> {
    val encoded = raw?.takeIf { it.isNotBlank() } ?: return listOf(StayRoom())
    val rooms = encoded.split(RoomSeparator).mapNotNull { room ->
        val fields = room.split(FieldSeparator).map { it.toIntOrNull() }
        if (fields.isEmpty() || fields.any { it == null }) return@mapNotNull null
        StayRoom(
            adults = fields.first()!!,
            childAges = fields.drop(1).map { it!! },
        )
    }
    return GuestSelection.of(rooms).rooms
}

private const val RoomSeparator = "_"
private const val FieldSeparator = "-"
