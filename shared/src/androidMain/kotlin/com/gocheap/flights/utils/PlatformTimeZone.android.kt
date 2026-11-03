package com.gocheap.flights.utils

import java.util.TimeZone

/**
 * `java.util.TimeZone` rather than `java.time.ZoneId`: both give the same identifier here, and this
 * one needs no desugaring to reach the module's minSdk.
 */
internal actual fun currentTimeZoneId(): String =
    runCatching { TimeZone.getDefault().id }.getOrNull()?.ifBlank { null } ?: "UTC"
