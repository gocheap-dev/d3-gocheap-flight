package com.gocheap.flights.analytics.crash

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

/**
 * Where the iOS app plugs Crashlytics in — the same seam, and for the same reason, as
 * [com.gocheap.flights.analytics.ga4.AnalyticsHandoff].
 *
 * Firebase's iOS SDK is a CocoaPods dependency of the app target and this module cannot reference
 * it: the Kotlin/Native build has no way to see a pod, and a shared module that every screen lives
 * in has no business depending on one. So the app registers a sink at launch, written in Swift, and
 * this is what it registers into.
 *
 * Null until it does, which is also the state of a checkout built without pods and of previews and
 * tests. Nothing is queued against that: a breadcrumb is only worth anything beside the crash it
 * precedes, and the crash handler Crashlytics installs for itself is already running by then.
 */
object CrashHandoff {

    /**
     * Written once from Swift at launch and read on whatever thread reported. No synchronisation
     * beyond what the Kotlin/Native memory model already gives a shared object — the same
     * arrangement `AnalyticsHandoff.sink` is under.
     */
    var sink: CrashReportingSink? = null
}

/**
 * What the Swift side implements.
 *
 * Top-level rather than nested in [CrashHandoff] so it reaches Swift under the name written here
 * rather than one the compiler assembles — the same reasoning as
 * [com.gocheap.flights.analytics.ga4.AnalyticsSink].
 *
 * [record] takes a flattened exception rather than a [Throwable] because there is nothing on the
 * Swift side that can read one: Crashlytics models a non-fatal as a name, a reason and a list of
 * frames, and the conversion is done here so that the bridge stays a bridge.
 */
interface CrashReportingSink {
    fun log(message: String)
    fun setKey(name: String, value: String)
    fun setUserId(id: String)
    fun record(name: String, reason: String, stackTrace: List<String>)
}

/**
 * Routes an uncaught Kotlin exception into Crashlytics before the process goes down.
 *
 * This is the one thing the platform SDK cannot do for itself here. Kotlin/Native does not convert
 * an uncaught exception into an Objective-C one — it prints the trace and aborts — so what
 * Crashlytics' own handler sees is a bare `SIGABRT` inside the Kotlin runtime, with none of the
 * Kotlin stack that says which of this app's functions threw. Reporting it here first is what makes
 * those reports readable, and it is why `start()` exists on iOS at all.
 *
 * The hook must end the process exactly as it would have ended without this. Hence
 * [terminateWithUnhandledException], which is the runtime's own path for an unhandled exception —
 * printing the trace and aborting — rather than a plain return, which would let the throwing
 * coroutine or callback carry on in an undefined state.
 *
 * The report is best effort: Crashlytics writes a non-fatal to disk and uploads it on the next
 * launch, and whether the write finishes before the abort is not something this can wait on. A
 * report that usually arrives is worth a great deal more than the nothing that arrives today.
 */
@OptIn(ExperimentalNativeApi::class)
internal actual fun startCrashReporting() {
    setUnhandledExceptionHook { error ->
        try {
            reportThrowable(error, origin = "unhandled")
        } catch (_: Throwable) {
            // Nothing useful left to do: the process is going down either way, and a reporter that
            // threw must not be what decides how.
        }
        terminateWithUnhandledException(error)
    }
}

internal actual fun logCrashBreadcrumb(message: String) {
    val sink = CrashHandoff.sink ?: return
    try {
        sink.log(message)
    } catch (error: Throwable) {
        println("[Crashlytics] Dropped breadcrumb: ${error.message}")
    }
}

internal actual fun setCrashKey(name: String, value: String) {
    val sink = CrashHandoff.sink ?: return
    try {
        sink.setKey(name, value)
    } catch (error: Throwable) {
        println("[Crashlytics] Dropped key '$name': ${error.message}")
    }
}

internal actual fun setCrashUserId(id: String) {
    val sink = CrashHandoff.sink ?: return
    try {
        sink.setUserId(id)
    } catch (error: Throwable) {
        println("[Crashlytics] Dropped user id: ${error.message}")
    }
}

internal actual fun recordCrashError(error: Throwable, origin: String?) {
    try {
        reportThrowable(error, origin)
    } catch (failure: Throwable) {
        println("[Crashlytics] Dropped non-fatal: ${failure.message}")
    }
}

/**
 * One [Throwable] as the three things Crashlytics models a non-fatal with.
 *
 * The name carries [origin] where there is one, because that is what the console groups by: without
 * it every error from the shared networking layer is one row named `IOException`, which is true and
 * useless. The reason is the message, falling back to the class name — an exception thrown with no
 * message is common and a blank row is worse than a repeated one.
 */
private fun reportThrowable(error: Throwable, origin: String?) {
    val sink = CrashHandoff.sink ?: return
    val type = error::class.simpleName ?: "Throwable"
    sink.record(
        name = if (origin.isNullOrEmpty()) type else "$type at $origin",
        reason = error.message ?: type,
        stackTrace = error.stackTraceToStrings(),
    )
}

/**
 * The Kotlin stack, as the lines Crashlytics will show.
 *
 * Causes are walked and appended rather than dropped: the top of a wrapped exception is usually
 * the wrapper's own construction site, and the frame that actually failed is in the cause.
 * [MaxFrames] caps the result — Crashlytics truncates a long trace on its own, and this keeps which
 * end gets cut a decision made here rather than there.
 */
@OptIn(ExperimentalNativeApi::class)
private fun Throwable.stackTraceToStrings(): List<String> = buildList {
    var current: Throwable? = this@stackTraceToStrings
    var depth = 0
    while (current != null && size < MaxFrames && depth < MaxCauses) {
        if (depth > 0) add("Caused by: ${current::class.simpleName}: ${current.message}")
        for (frame in current.getStackTrace()) {
            if (size >= MaxFrames) break
            add(frame)
        }
        current = current.cause
        depth++
    }
}

private const val MaxFrames = 64
private const val MaxCauses = 5
