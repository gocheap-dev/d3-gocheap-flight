package com.gocheap.travel.ui.home.results.api

import com.gocheap.travel.data.model.flight.api.CurrencyInfo
import androidx.compose.runtime.Composable
import flighttp.shared.generated.resources.Res
import flighttp.shared.generated.resources.day_offset_plus
import flighttp.shared.generated.resources.direct
import flighttp.shared.generated.resources.duration_hours
import flighttp.shared.generated.resources.duration_hours_minutes
import flighttp.shared.generated.resources.duration_minutes
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
import flighttp.shared.generated.resources.results_count
import flighttp.shared.generated.resources.stops_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formatting for the in-app results screen, done by hand.
 *
 * Common code has no locale-aware number or date formatter, and reaching for each platform's own
 * would be worse than not having one: the backend has already said how this search's prices are
 * punctuated and in which currency, and a platform formatter would ignore all of it and format to
 * the *device's* locale instead — "€1,234.50" on a UK phone for a search priced in Germany.
 *
 * So these take the backend at its word. [CurrencyInfo] carries the symbol, which side it sits on,
 * whether a space separates it and both separators; [formatPrice] uses exactly those.
 *
 * Times are rendered in UTC, which is what makes them airport-local — see [formatClockTime].
 */

/**
 * "$1,234", or "1 234,50 €" — however [currency] says this search's prices are written.
 *
 * Falls back to plain grouped digits with no symbol when the backend has not said: a bare number
 * reads as a price, and guessing a symbol would state a currency that may be wrong.
 */
internal fun formatPrice(amount: Double?, currency: CurrencyInfo?): String {
    val value = amount ?: return Dash
    if (currency == null) return groupDigits(value.roundToLong().toString(), ",")

    val digits = currency.decimalDigits.coerceIn(0, 4)
    val scaled = abs(value).scaledTo(digits)
    val whole = groupDigits(scaled.first, currency.thousandsSeparator)
    val number = if (digits > 0) "$whole${currency.decimalSeparator}${scaled.second}" else whole
    val signed = if (value < 0) "-$number" else number

    val gap = if (currency.spaceBetweenAmountAndSymbol) " " else ""
    return if (currency.symbolOnLeft) "${currency.symbol}$gap$signed" else "$signed$gap${currency.symbol}"
}

/**
 * "$1,234" with no decimals, whatever the currency's usual precision.
 *
 * What a results row shows. A list of fares is scanned down rather than read across, and ".00"
 * repeated forty times is noise in the one column the eye is actually comparing.
 */
internal fun formatPriceCompact(amount: Double?, currency: CurrencyInfo?): String =
    formatPrice(amount, currency?.copy(decimalDigits = 0))

/**
 * "7h 25m", "45m", "2h" — localised. [minutes] is what the backend reports durations in.
 *
 * Composable because the unit words are translated; the arithmetic that splits the minutes is
 * still plain and still below.
 */
@Composable
internal fun durationLabel(minutes: Int?): String {
    val total = minutes?.takeIf { it > 0 } ?: return Dash
    val hours = total / 60
    val rest = total % 60
    return when {
        hours == 0 -> stringResource(Res.string.duration_minutes, rest)
        rest == 0 -> stringResource(Res.string.duration_hours, hours)
        else -> stringResource(Res.string.duration_hours_minutes, hours, rest)
    }
}

/**
 * "08:25", from an epoch second.
 *
 * Rendered in UTC on purpose. The backend's `...TimeAirport` fields are already shifted so that
 * reading them as UTC gives the time on the clock at that airport, which is the only time a
 * traveller cares about — a flight leaves at the time printed on the boarding pass regardless of
 * where the phone showing it happens to be.
 *
 * **This is a deliberate departure from the reference app**, whose `SimpleDateFormat` carries no
 * time zone and so renders in the *device's*. That shows the right time only to a user sitting in
 * UTC, and silently shifts every departure by the traveller's own offset otherwise.
 */
internal fun formatClockTime(epochSeconds: Long?): String {
    val seconds = epochSeconds ?: return "--:--"
    val intoDay = seconds.floorMod(SecondsPerDay)
    val hours = intoDay / 3600
    val minutes = (intoDay % 3600) / 60
    return "${hours.pad2()}:${minutes.pad2()}"
}

/**
 * "08:25", from a number of seconds into a day — for the time-window sliders.
 *
 * Distinct from [formatClockTime], which wraps at midnight because it is rendering a moment in
 * time: an epoch second of 86 400 is the start of the second day, and "00:00" is the right answer
 * for it.
 *
 * A *window* is not a moment, and the top of its range is the end of the day rather than the
 * start of the next one. Wrapping it the same way renders a full day as "00:00 – 00:00", which
 * reads as a window that excludes everything — the opposite of what an untouched slider means.
 */
internal fun formatTimeOfDay(secondsIntoDay: Long): String {
    val clamped = secondsIntoDay.coerceIn(0L, SecondsPerDay)
    if (clamped == SecondsPerDay) return "24:00"
    val hours = clamped / 3600
    val minutes = (clamped % 3600) / 60
    return "${hours.pad2()}:${minutes.pad2()}"
}

/**
 * The month and day an epoch second falls on, read in UTC for the same reason [formatClockTime] is.
 *
 * Returns the parts rather than the text so the calendar arithmetic stays testable without a
 * composition — getting a leap day wrong here would be invisible in a rendered string.
 */
internal fun dayMonthParts(epochSeconds: Long?): Pair<Int, Int>? {
    val seconds = epochSeconds ?: return null
    val days = seconds.floorDiv(SecondsPerDay)
    val (_, month, day) = civilFromDays(days)
    return month to day
}

/** "14 Mar" — the month name translated, the day as the calendar gives it. */
@Composable
internal fun dayMonthLabel(epochSeconds: Long?): String {
    val (month, day) = dayMonthParts(epochSeconds) ?: return ""
    return "$day ${stringResource(MonthNameResources[month - 1])}"
}

/** "Direct", "1 stop", "2 stops" — the plural chosen by the language, not by `== 1`. */
@Composable
internal fun stopsLabel(stopCount: Int?): String = when {
    stopCount == null -> ""
    stopCount == 0 -> stringResource(Res.string.direct)
    else -> pluralStringResource(Res.plurals.stops_count, stopCount, stopCount)
}

/** The "+1" beside an arrival that lands the next day. Blank when it lands the same day. */
@Composable
internal fun dayOffsetLabel(days: Int?): String = when {
    days == null || days == 0 -> ""
    days > 0 -> stringResource(Res.string.day_offset_plus, days)
    else -> days.toString()
}

/** "284 results", "1 result", and nothing at all before the first page lands. */
@Composable
internal fun resultCountLabel(count: Int): String =
    if (count <= 0) "" else pluralStringResource(Res.plurals.results_count, count, count)

// --- the arithmetic the above is built on ---------------------------------------------------

private const val SecondsPerDay = 86_400L

/** Indexed by month number minus one, so January is first. */
private val MonthNameResources = listOf(
    Res.string.month_short_01, Res.string.month_short_02, Res.string.month_short_03,
    Res.string.month_short_04, Res.string.month_short_05, Res.string.month_short_06,
    Res.string.month_short_07, Res.string.month_short_08, Res.string.month_short_09,
    Res.string.month_short_10, Res.string.month_short_11, Res.string.month_short_12,
)

/** What a price or duration shows when there is nothing to show. */
private const val Dash = "\u2014"

/** `%02d`, which common code has no format string for. */
private fun Long.pad2(): String = if (this < 10) "0$this" else toString()

/**
 * Kotlin's `%` keeps the sign of the dividend, so `-1 % 86400` is `-1` rather than `86399`.
 *
 * Only reachable on a timestamp before 1970, which no flight has — but the two helpers below are
 * where an off-by-a-day would be invisible rather than obvious, so they do the arithmetic that
 * holds either way.
 */
private fun Long.floorMod(divisor: Long): Long = ((this % divisor) + divisor) % divisor

private fun Long.floorDiv(divisor: Long): Long {
    val quotient = this / divisor
    return if (this % divisor != 0L && (this xor divisor) < 0) quotient - 1 else quotient
}

/**
 * Days since the epoch to a (year, month, day) triple, by Howard Hinnant's `civil_from_days`.
 *
 * The standard branch-free form of this conversion: shift the epoch to March 1st of year 0 so the
 * leap day lands at the end of the year rather than in the middle of it, then the month lengths
 * fall into a repeating pattern that closes-forms. Correct for every proleptic Gregorian date,
 * which is more than this screen needs and no more code than a version that is not.
 */
private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val shifted = days + 719_468
    val era = (if (shifted >= 0) shifted else shifted - 146_096) / 146_097
    val dayOfEra = shifted - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val year = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPrime = (5 * dayOfYear + 2) / 153
    val day = (dayOfYear - (153 * monthPrime + 2) / 5 + 1).toInt()
    val month = (if (monthPrime < 10) monthPrime + 3 else monthPrime - 9).toInt()
    return Triple((if (month <= 2) year + 1 else year).toInt(), month, day)
}

/**
 * A positive number split into its whole part and exactly [digits] decimal places, both as digit
 * strings, rounding the fraction rather than truncating it.
 *
 * String work rather than `toString()` on a scaled `Double`: that renders large values in
 * scientific notation and small ones with a floating-point tail, and a fare is neither.
 */
private fun Double.scaledTo(digits: Int): Pair<String, String> {
    var factor = 1L
    repeat(digits) { factor *= 10 }
    val scaled = (this * factor).roundToLong()
    val whole = (scaled / factor).toString()
    if (digits == 0) return whole to ""
    // Padded from the left: 5 cents is ".05", not ".5".
    val fraction = (scaled % factor).toString().padStart(digits, '0')
    return whole to fraction
}

/** "1234567" -> "1,234,567", with whatever [separator] the backend named. Blank separator = none. */
private fun groupDigits(digits: String, separator: String): String {
    if (separator.isEmpty() || digits.length <= 3) return digits
    return digits.reversed()
        .chunked(3)
        .joinToString(separator.reversed())
        .reversed()
}
