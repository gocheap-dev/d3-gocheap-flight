package com.gocheap.travel.analytics.crash

import android.util.Log
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** The tag the whole integration logs under: `adb logcat -s Crashlytics`. */
private const val LogTag = "Crashlytics"

/**
 * The SDK, or null on a build that has no Firebase app for it to report through.
 *
 * `getInstance()` throws rather than returning null when there is no default `FirebaseApp` — the
 * ordinary state of a checkout without `google-services.json` or the `FIREBASE_*` values in
 * `local.properties` — so every call below goes through here instead of calling it directly.
 *
 * Not cached: the instance is a singleton inside the SDK already, and caching the *failure* would
 * mean a call made before `FlightTpApplication.ensureFirebase()` permanently disabled reporting
 * for the rest of the process.
 */
private fun crashlytics(): FirebaseCrashlytics? = try {
    FirebaseCrashlytics.getInstance()
} catch (error: Throwable) {
    Log.d(LogTag, "Not reporting: no Firebase app. ${error.message}")
    null
}

/**
 * Nothing to install.
 *
 * Crashlytics ships a startup provider that installs its `Thread.UncaughtExceptionHandler` before
 * `Application.onCreate` runs, so by the time anything here is called the handler is already in
 * place and an uncaught exception — Kotlin's included, since they are all `Throwable` on this
 * platform — is already being reported. This only confirms the SDK is reachable, so that a build
 * with no Firebase app says so once at launch rather than silently at every later call.
 */
internal actual fun startCrashReporting() {
    if (crashlytics() != null) Log.i(LogTag, "Initialised.")
}

internal actual fun logCrashBreadcrumb(message: String) {
    try {
        crashlytics()?.log(message)
    } catch (error: Throwable) {
        Log.w(LogTag, "Dropped breadcrumb: ${error.message}", error)
    }
}

internal actual fun setCrashKey(name: String, value: String) {
    try {
        crashlytics()?.setCustomKey(name, value)
    } catch (error: Throwable) {
        Log.w(LogTag, "Dropped key '$name': ${error.message}", error)
    }
}

internal actual fun setCrashUserId(id: String) {
    try {
        crashlytics()?.setUserId(id)
    } catch (error: Throwable) {
        Log.w(LogTag, "Dropped user id: ${error.message}", error)
    }
}

/**
 * Files a handled error as a non-fatal.
 *
 * The [Throwable] goes over as itself rather than as a message, which is what lets Crashlytics
 * group these by stack the way it groups real crashes. [origin] rides along as a key on this one
 * report — the `recordException` overload that takes keys, rather than `setCustomKey` — so that it
 * says where *this* error came from instead of staying pinned to every later report as well.
 */
internal actual fun recordCrashError(error: Throwable, origin: String?) {
    try {
        val crashlytics = crashlytics() ?: return
        if (origin == null) {
            crashlytics.recordException(error)
        } else {
            crashlytics.recordException(
                error,
                CustomKeysAndValues.Builder().putString("error_origin", origin).build(),
            )
        }
    } catch (failure: Throwable) {
        Log.w(LogTag, "Dropped non-fatal: ${failure.message}", failure)
    }
}
