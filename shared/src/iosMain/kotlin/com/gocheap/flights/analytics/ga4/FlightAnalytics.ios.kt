package com.gocheap.flights.analytics.ga4

import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

internal actual val platformAnalyticsName: String = "ios"

/**
 * Where the iOS app plugs Firebase Analytics in — the same seam, and for the same reason, as
 * [com.gocheap.flights.sdk.FlightResultsHandoff].
 *
 * Firebase's iOS SDK is a CocoaPods dependency of the app target, and this module cannot reference
 * it: the Kotlin/Native build has no way to see a pod, and a shared module that every screen lives
 * in has no business depending on one. So the app registers a sink at launch, written in Swift, and
 * this is what it registers into.
 *
 * Null until it does, which is also the state of a checkout built without pods and of previews and
 * tests. Events fired before then are dropped rather than queued: a measurement that arrives
 * minutes late under a different session is worse than one that never arrives, and the only events
 * ahead of `FlightAnalyticsBridge.start()` are the launch itself.
 */
object AnalyticsHandoff {

    /**
     * Written once from Swift at launch and read on whatever thread fired the event. No
     * synchronisation beyond what the Kotlin/Native memory model already gives a shared object —
     * the same arrangement `FlightResultsHandoff.handler` is under.
     */
    var sink: AnalyticsSink? = null
}

/**
 * What the Swift side implements: one event, by name, with its parameters.
 *
 * Top-level rather than nested in [AnalyticsHandoff] so it reaches Swift under the name written
 * here rather than one the compiler assembles — the same reasoning as [com.gocheap.flights.sdk.FlightResultsHandler].
 *
 * The map arrives in Swift as `[String: Any]` holding `String` and `KotlinLong`/`KotlinDouble`,
 * all of which Firebase takes as they are.
 */
fun interface AnalyticsSink {
    fun log(name: String, parameters: Map<String, Any>)
}

/**
 * Hands one event to whatever Swift registered.
 *
 * Wrapped the way the Android half is, and for the same reason: this runs inside the search and
 * checkout paths, and nothing a measurement SDK does should be able to cost a user their booking.
 */
internal actual fun logGa4Event(name: String, params: Map<String, Any>) {
    val sink = AnalyticsHandoff.sink ?: return
    try {
        sink.log(name, params)
    } catch (error: Throwable) {
        // No platform logger here beyond print: this module's own logWarning is for the shared
        // code paths, and a dropped analytics event is not worth routing through one.
        println("[Ga4] Dropped '$name': ${error.message}")
    }
}

/**
 * Calls [onBackgrounded] each time iOS puts the app into the background.
 *
 * `UIApplicationDidEnterBackgroundNotification` rather than `willResignActive`, which also fires
 * for a notification banner, Control Centre and an incoming call — none of which is the user
 * leaving. The observer is never removed: it lives as long as the process, which is exactly how
 * long the event is wanted for.
 */
internal actual fun observeAppBackgrounded(onBackgrounded: () -> Unit) {
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        // The main queue because UIKit posts this on the main thread and a measurement call is not
        // worth a hop off it.
        queue = NSOperationQueue.mainQueue,
        usingBlock = { _: NSNotification? -> onBackgrounded() },
    )
}
