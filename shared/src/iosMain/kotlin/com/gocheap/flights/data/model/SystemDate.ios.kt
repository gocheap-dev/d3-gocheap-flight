package com.gocheap.flights.data.model

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

internal actual fun systemToday(): CalendarDate {
    val components = NSCalendar.currentCalendar.components(
        unitFlags = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        fromDate = NSDate(),
    )
    return CalendarDate(
        year = components.year.toInt(),
        month = components.month.toInt(),
        day = components.day.toInt(),
    )
}
