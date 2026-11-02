package com.gocheap.travel.utils

/**
 * The little of a logger this module needs, because `android.util.Log` is not reachable from common
 * code and the sign-up it has to report on runs there.
 *
 * Two levels only. `users/add` either produced a user id or it did not, and anything finer would be
 * a logging framework — which this does not need, since what is being traced is one call that
 * happens once per install.
 */
internal expect fun logDebug(tag: String, message: String)

/** The failure half: a lost sign-up, which is worth a level that survives a filtered logcat. */
internal expect fun logWarning(tag: String, message: String, error: Throwable? = null)
