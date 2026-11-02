package com.gocheap.travel.utils

/**
 * Milliseconds since the Unix epoch, from the device's own clock.
 *
 * The wall clock, not a monotonic one, because what reads it is a timestamp that outlives the
 * process — see `AdsRepository`'s stored session. A monotonic source resets on every launch and
 * cannot say how old something written last time is.
 *
 * That it is the wall clock has one consequence worth naming: a user who moves their clock
 * backwards makes a stored timestamp look like it is in the future, and one who moves it forwards
 * ages it instantly. Both are handled where it is read rather than here — a nonsensical age is
 * treated as expired, which costs one extra call and nothing else.
 */
internal expect fun systemEpochMillis(): Long
