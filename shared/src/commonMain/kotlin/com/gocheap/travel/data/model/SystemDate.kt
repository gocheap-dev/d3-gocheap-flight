package com.gocheap.travel.data.model

/**
 * Today, in the device's own time zone.
 *
 * Each platform's own calendar does the conversion rather than this module doing epoch arithmetic:
 * "which civil day is it right now" depends on the zone offset *at this instant*, which is a DST
 * question, and getting it wrong shows up as an off-by-one-day only for users near midnight.
 */
internal expect fun systemToday(): CalendarDate
