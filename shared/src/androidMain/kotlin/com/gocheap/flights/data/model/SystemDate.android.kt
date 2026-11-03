package com.gocheap.flights.data.model

import java.time.LocalDate

// minSdk is 26, so java.time is available without desugaring.
internal actual fun systemToday(): CalendarDate = LocalDate.now().let {
    CalendarDate(year = it.year, month = it.monthValue, day = it.dayOfMonth)
}
