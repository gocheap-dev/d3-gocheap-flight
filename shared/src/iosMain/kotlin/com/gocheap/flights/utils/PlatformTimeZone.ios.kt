package com.gocheap.flights.utils

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

/**
 * `name` rather than `abbreviation`: the first is the IANA identifier the endpoint wants, the
 * second is `IST` or `GMT+5:30`, which is ambiguous between zones.
 *
 * `localTimeZone` is an Objective-C category on NSTimeZone, so Kotlin/Native maps it to an
 * extension and it has to be imported by name — the same arrangement as
 * `UIApplication.registerForRemoteNotifications`.
 */
internal actual fun currentTimeZoneId(): String =
    runCatching { NSTimeZone.localTimeZone.name }.getOrNull()?.ifBlank { null } ?: "UTC"
