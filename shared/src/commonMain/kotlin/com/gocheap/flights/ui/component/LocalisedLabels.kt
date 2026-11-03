package com.gocheap.flights.ui.component

import androidx.compose.runtime.Composable
import com.gocheap.flights.data.model.CabinClass
import com.gocheap.flights.data.model.CalendarDate
import com.gocheap.flights.ui.home.travellers.TravellerSelection
import com.gocheap.flights.ui.hotel.guests.GuestSelection
import flighttp.shared.generated.resources.Res
import flighttp.shared.generated.resources.cabin_business
import flighttp.shared.generated.resources.cabin_economy
import flighttp.shared.generated.resources.cabin_first
import flighttp.shared.generated.resources.cabin_premium_economy
import flighttp.shared.generated.resources.dot_join
import flighttp.shared.generated.resources.guests_count
import flighttp.shared.generated.resources.month_long_01
import flighttp.shared.generated.resources.month_long_02
import flighttp.shared.generated.resources.month_long_03
import flighttp.shared.generated.resources.month_long_04
import flighttp.shared.generated.resources.month_long_05
import flighttp.shared.generated.resources.month_long_06
import flighttp.shared.generated.resources.month_long_07
import flighttp.shared.generated.resources.month_long_08
import flighttp.shared.generated.resources.month_long_09
import flighttp.shared.generated.resources.month_long_10
import flighttp.shared.generated.resources.month_long_11
import flighttp.shared.generated.resources.month_long_12
import flighttp.shared.generated.resources.month_short_01
import flighttp.shared.generated.resources.month_short_02
import flighttp.shared.generated.resources.month_short_03
import flighttp.shared.generated.resources.month_short_04
import flighttp.shared.generated.resources.month_short_05
import flighttp.shared.generated.resources.month_short_06
import flighttp.shared.generated.resources.month_short_07
import flighttp.shared.generated.resources.month_short_08
import flighttp.shared.generated.resources.month_short_09
import flighttp.shared.generated.resources.month_short_10
import flighttp.shared.generated.resources.month_short_11
import flighttp.shared.generated.resources.month_short_12
import flighttp.shared.generated.resources.rooms_count
import flighttp.shared.generated.resources.travellers_count
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The reader-facing forms of the values the app stores in English.
 *
 * Several model types carry a `label` that is not display text at all: [CalendarDate.label] is the
 * format searches are *stored and parsed back* in, and [CabinClass.label] is what goes on the wire.
 * Translating either in place would break a round trip or a request, so the translation happens
 * here instead — at the point of drawing, where a composition is available and nothing is parsed
 * back afterwards.
 */

/** "4, nov 2026" as the reader's language writes it. Unparseable input is handed back untouched. */
@Composable
internal fun storedDateLabel(stored: String?): String {
    val text = stored.orEmpty()
    val date = CalendarDate.parse(text) ?: return text
    return "${date.day}, ${stringResource(MonthShortResources[date.month - 1])} ${date.year}"
}

/** "December 2022" — the heading above a month in the calendar. */
@Composable
internal fun monthHeading(date: CalendarDate): String =
    stringResource(MonthLongResources[date.month - 1])

/** Just the month name, for headings that set the month and the year in different weights. */
@Composable
internal fun monthName(date: CalendarDate): String =
    stringResource(MonthLongResources[date.month - 1])

internal val CabinClass.displayLabel: StringResource
    get() = when (this) {
        CabinClass.Economy -> Res.string.cabin_economy
        CabinClass.PremiumEconomy -> Res.string.cabin_premium_economy
        CabinClass.Business -> Res.string.cabin_business
        CabinClass.First -> Res.string.cabin_first
    }

/** "3 travellers · Economy", with the plural chosen by the language rather than by `== 1`. */
@Composable
internal fun travellerLabel(selection: TravellerSelection): String = stringResource(
    Res.string.dot_join,
    pluralStringResource(Res.plurals.travellers_count, selection.count, selection.count),
    stringResource(selection.cabinClass.displayLabel),
)

/** "3 guests · 2 rooms", the same form the stay search card shows. */
@Composable
internal fun guestsAndRoomsLabel(guests: Int, rooms: Int): String = stringResource(
    Res.string.dot_join,
    pluralStringResource(Res.plurals.guests_count, guests, guests),
    pluralStringResource(Res.plurals.rooms_count, rooms, rooms),
)

@Composable
internal fun guestsAndRoomsLabel(selection: GuestSelection): String =
    guestsAndRoomsLabel(guests = selection.guestCount, rooms = selection.roomCount)

/** Indexed by month number minus one, so January is first. */
private val MonthShortResources = listOf(
    Res.string.month_short_01, Res.string.month_short_02, Res.string.month_short_03,
    Res.string.month_short_04, Res.string.month_short_05, Res.string.month_short_06,
    Res.string.month_short_07, Res.string.month_short_08, Res.string.month_short_09,
    Res.string.month_short_10, Res.string.month_short_11, Res.string.month_short_12,
)

private val MonthLongResources = listOf(
    Res.string.month_long_01, Res.string.month_long_02, Res.string.month_long_03,
    Res.string.month_long_04, Res.string.month_long_05, Res.string.month_long_06,
    Res.string.month_long_07, Res.string.month_long_08, Res.string.month_long_09,
    Res.string.month_long_10, Res.string.month_long_11, Res.string.month_long_12,
)
