package com.gocheap.flights.data.model

/**
 * A plain calendar day, with just enough arithmetic for the date picker and the night/day counts.
 *
 * Every search in the app already stores its dates as the display strings the design calls for
 * ("19, dec 2022"), so this type parses and re-renders that exact format rather than introducing a
 * second representation that would have to be converted at every boundary.
 *
 * [today] is the app's only clock; everything dated is an offset from it.
 */
data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<CalendarDate> {

    /** The form the rest of the app stores and displays, e.g. "19, dec 2022". */
    val label: String get() = "$day, ${MonthAbbreviations[month - 1]} $year"

    /**
     * The form APIs and deeplinks want, e.g. "2022-12-19". Zero-padded, unlike [label], which is
     * shaped for the design rather than for a query string.
     */
    val isoDate: String get() = "$year-${month.padded()}-${day.padded()}"

    /** Heading above the grid, e.g. "December 2022". */
    val monthLabel: String get() = "$monthName $year"

    /** Just the month, for headings that set the month and the year in different weights. */
    val monthName: String get() = MonthNames[month - 1]

    /** 0 = Sunday. Sakamoto's method, exact for any Gregorian date. */
    val dayOfWeek: Int
        get() {
            val shift = if (month < 3) year - 1 else year
            return (shift + shift / 4 - shift / 100 + shift / 400 + MonthOffsets[month - 1] + day).mod(7)
        }

    override fun compareTo(other: CalendarDate): Int = when {
        year != other.year -> year.compareTo(other.year)
        month != other.month -> month.compareTo(other.month)
        else -> day.compareTo(other.day)
    }

    fun plusDays(days: Int): CalendarDate = fromEpochDay(toEpochDay() + days)

    /** Clamps the day when the target month is shorter — 31 Jan plus a month is 28/29 Feb. */
    fun plusMonths(months: Int): CalendarDate {
        val total = year * 12 + (month - 1) + months
        val targetYear = total.floorDiv(12)
        val targetMonth = total.mod(12) + 1
        return CalendarDate(targetYear, targetMonth, day.coerceAtMost(lengthOfMonth(targetYear, targetMonth)))
    }

    private fun Int.padded(): String = if (this < 10) "0$this" else toString()

    /** Whole days from this date to [other]; negative when [other] is earlier. */
    fun daysUntil(other: CalendarDate): Int = other.toEpochDay() - toEpochDay()

    /** Days since 1970-01-01, via Howard Hinnant's days-from-civil. */
    fun toEpochDay(): Int {
        val shiftedYear = if (month <= 2) year - 1 else year
        val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
        val yearOfEra = shiftedYear - era * 400
        val marchBasedMonth = (month + 9) % 12
        val dayOfYear = (153 * marchBasedMonth + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146097 + dayOfEra - 719468
    }

    companion object {
        private val MonthAbbreviations = listOf(
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec",
        )

        private val MonthNames = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )

        private val MonthOffsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)

        /** Sunday-first, matching the column order of the grid. */
        val WeekdayInitials = listOf("S", "M", "T", "W", "T", "F", "S")

        /**
         * Today, in the device's time zone. Every default date in the app is expressed as an offset
         * from this, so nothing is ever stale — the alternative, a fixed date, was how the search
         * cards ended up opening on December 2022.
         */
        fun today(): CalendarDate = systemToday()

        /** Inverse of [toEpochDay]; Hinnant's civil-from-days. */
        fun fromEpochDay(epochDay: Int): CalendarDate {
            val shifted = epochDay + 719468
            val era = (if (shifted >= 0) shifted else shifted - 146096) / 146097
            val dayOfEra = shifted - era * 146097
            val yearOfEra =
                (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
            val shiftedYear = yearOfEra + era * 400
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val marchBasedMonth = (5 * dayOfYear + 2) / 153
            val day = dayOfYear - (153 * marchBasedMonth + 2) / 5 + 1
            val month = if (marchBasedMonth < 10) marchBasedMonth + 3 else marchBasedMonth - 9
            return CalendarDate(if (month <= 2) shiftedYear + 1 else shiftedYear, month, day)
        }

        fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            else -> if (isLeapYear(year)) 29 else 28
        }

        fun isLeapYear(year: Int): Boolean =
            year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        /** Reads back [label]; returns null on anything that is not that exact shape. */
        fun parse(text: String?): CalendarDate? {
            if (text.isNullOrBlank()) return null
            val parts = text.split(",", limit = 2)
            if (parts.size != 2) return null
            val day = parts[0].trim().toIntOrNull() ?: return null
            val tail = parts[1].trim().split(" ").filter { it.isNotBlank() }
            if (tail.size != 2) return null
            val month = MonthAbbreviations.indexOfFirst { it.equals(tail[0], ignoreCase = true) } + 1
            if (month == 0) return null
            val year = tail[1].toIntOrNull() ?: return null
            if (day !in 1..lengthOfMonth(year, month)) return null
            return CalendarDate(year, month, day)
        }

        /** [parse] with a sensible stand-in, for the many places a date string is known-good. */
        fun parseOrFallback(text: String?): CalendarDate = parse(text) ?: today()
    }
}
