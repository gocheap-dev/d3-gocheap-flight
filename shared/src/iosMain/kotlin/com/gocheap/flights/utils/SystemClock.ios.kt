package com.gocheap.flights.utils

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

// `timeIntervalSince1970` is seconds as a Double; the epoch in milliseconds is well inside Long.
internal actual fun systemEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1_000.0).toLong()
