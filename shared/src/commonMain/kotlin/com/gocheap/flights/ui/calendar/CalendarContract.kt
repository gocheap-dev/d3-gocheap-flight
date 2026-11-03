package com.gocheap.flights.ui.calendar

/** Whether the page collects one date or a span. */
enum class CalendarMode {
    Single,
    Range,
    ;

    companion object {
        /** Route arguments are strings; anything unrecognised falls back to a single date. */
        fun fromRoute(raw: String?): CalendarMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Single
    }
}

/** What a search screen asks the calendar page for. */
data class CalendarRequest(
    val title: String,
    val mode: CalendarMode,
    val start: String?,
    val end: String?,
    /**
     * Collect a time alongside each date. Rentals need one — a desk opens and closes — while
     * flights and hotel stays are booked by the day.
     */
    val withTime: Boolean = false,
    val startTime: String? = null,
    val endTime: String? = null,
    /**
     * What the gap between the two dates is counted in. A stay is measured in nights; a rental and
     * a return flight are measured in days.
     */
    val spanUnit: String = "nights",
)

/**
 * What the calendar page hands back, as the same date strings every search already stores.
 * [end] is null in [CalendarMode.Single]; the times are null unless the request asked for them.
 */
data class DateSelection(
    val start: String,
    val end: String?,
    val startTime: String? = null,
    val endTime: String? = null,
)

/** The clock slots the pickers offer. */
object TimeSlots {
    /** Half-hour steps across the day, the granularity rental desks work in. */
    val All: List<String> = List(48) { slot ->
        val hour = (slot / 2).toString().padStart(2, '0')
        val minute = if (slot % 2 == 0) "00" else "30"
        "$hour:$minute"
    }

    const val Default = "10:00"

    /** Anything not on the half hour is not a slot we offer, so it falls back to the default. */
    fun normalise(value: String?): String = value?.takeIf { it in All } ?: Default
}
