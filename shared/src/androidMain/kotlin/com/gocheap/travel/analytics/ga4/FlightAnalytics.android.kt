package com.gocheap.travel.analytics.ga4

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.gocheap.travel.data.storage.AndroidAppContext
import com.google.firebase.analytics.FirebaseAnalytics

/** The tag the whole integration logs under: `adb logcat -s Ga4`. */
private const val LogTag = "Ga4"

internal actual val platformAnalyticsName: String = "android"

/**
 * Sends one event through Firebase Analytics.
 *
 * Two things can stop an event and neither is an error worth taking the app down for: the shared
 * module may not have been handed a Context yet — `initFlightStorage` does that from
 * `FlightTpApplication.onCreate`, so only something logged before then is affected — and the build
 * may have no Firebase app configured at all, which is the ordinary state of a checkout without
 * `google-services.json` or the `FIREBASE_*` values in `local.properties`.
 *
 * Both end up here as a log line and a dropped event. The `catch` is deliberately `Throwable`: this
 * runs inside the search and checkout paths, and a measurement SDK must not be able to cost a user
 * their booking.
 */
internal actual fun logGa4Event(name: String, params: Map<String, Any>) {
    val context = AndroidAppContext.requireOrNull()
    if (context == null) {
        Log.d(LogTag, "Dropped '$name': no Context yet. Call initFlightStorage() first.")
        return
    }
    try {
        FirebaseAnalytics.getInstance(context).logEvent(name, params.toBundle())
    } catch (error: Throwable) {
        Log.w(LogTag, "Dropped '$name': ${error.message}", error)
    }
}

/**
 * The parameter map as the SDK takes it.
 *
 * Numbers stay numbers — `putLong`/`putDouble` rather than their text — so a report can average a
 * price or a party size. The shared half has already dropped the nulls and clipped the lengths, so
 * everything arriving here is known to be one of these three types; `toString` is the backstop for
 * a type added to the shared sanitiser without this being updated.
 */
private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
    for ((key, value) in this@toBundle) {
        when (value) {
            is String -> putString(key, value)
            is Long -> putLong(key, value)
            is Double -> putDouble(key, value)
            else -> putString(key, value.toString())
        }
    }
}

/**
 * Calls [onBackgrounded] when the last Activity stops without one taking its place.
 *
 * `ProcessLifecycleOwner` would be the usual way and is one dependency heavier; this app has a
 * single Activity plus whatever the Travelpayouts SDK opens, so counting started Activities says
 * the same thing. The counter is what separates "the app went away" from "a screen was replaced by
 * another" — during a push the incoming Activity starts before the outgoing one stops, so the count
 * never reaches zero.
 *
 * [Activity.isChangingConfigurations] is the other half: a rotation stops every Activity, and
 * without this check turning the phone sideways would report the user as having left.
 */
internal actual fun observeAppBackgrounded(onBackgrounded: () -> Unit) {
    val application = AndroidAppContext.requireOrNull() as? Application
    if (application == null) {
        Log.d(LogTag, "No app_close tracking: the shared module has no Application.")
        return
    }

    application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        private var started = 0

        override fun onActivityStarted(activity: Activity) {
            started++
        }

        override fun onActivityStopped(activity: Activity) {
            started--
            if (started <= 0 && !activity.isChangingConfigurations) {
                // Floored rather than left negative: an Activity stopped without this having seen
                // it start — which is what happens when the callbacks are registered while one is
                // already on screen — would otherwise leave the count below zero for good, and
                // every later backgrounding would go unreported.
                started = 0
                onBackgrounded()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    })
}
