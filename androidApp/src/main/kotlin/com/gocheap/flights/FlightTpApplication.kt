package com.gocheap.flights

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.gocheap.flights.analytics.crash.CrashReporting
import com.gocheap.flights.data.storage.initFlightStorage
import com.gocheap.flights.push.PushNotifier
import com.gocheap.flights.push.PushNotifications
import com.gocheap.flights.sdk.TravelSdkBridge
import com.gocheap.flights.sdk.TravelSdkRegionBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.travelapp.sdk.config.EnabledInfoItems
import com.travelapp.sdk.config.SdkConfig
import com.travelapp.sdk.TravelSdkActivity
import com.travelapp.sdk.config.TravelSdk

/**
 * Initialises the Travelpayouts flight SDK, which its documentation requires be done once, from
 * `Application.onCreate`, before any of its screens are opened.
 *
 * Android only by nature: the SDK is an .aar, so none of this can live in the shared module. The
 * screens it opens are its own Activities — this app's Compose UI hands over a search and gets
 * control back when the user comes out, so nothing here touches how any screen looks.
 */
class FlightTpApplication : Application() {

    /**
     * Lives as long as the process, which is what the region mirror needs: a setting changed on the
     * Profile tab has to reach the SDK whether or not its results screen is open.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        disableDarkTheme()
        // Before anything reads a stored setting — which the mirror does as soon as it collects.
        // Also what hands the shared module the Context that Firebase Analytics is reached
        // through, so it has to come before the line below.
        initFlightStorage(this)
        // Hoisted out of initTravelSdk(), which used to be the only caller and which returns early
        // on a build without Travelpayouts credentials. GA4 has nothing to do with that SDK, and
        // leaving the call where it was meant a checkout without a marker reported no analytics at
        // all. Idempotent — initTravelSdk() still calls it, and finds the app already there.
        ensureFirebase()
        // After it, because Crashlytics has nowhere to file a report until a Firebase app exists.
        // This does not install the crash handler — Crashlytics' own startup provider did that
        // before onCreate ran, so a crash in any of the lines above is already reported — it only
        // confirms the SDK is reachable and gives the shared module somewhere to attach its
        // breadcrumbs and keys.
        CrashReporting.start()
        // Both before the first push can arrive, which on a device that already has a token can be
        // moments from now. The channel first: the system draws a background push itself, against
        // the channel named in the manifest, and a notification posted to a channel that does not
        // exist is dropped without a word.
        PushNotifier.ensureChannel(this)
        PushNotifications.start()
        initTravelSdk()
        slideTheSdkResultsInAndOut()
        TravelSdkRegionBridge.mirrorInto(this, scope)
    }

    /**
     * Keeps every screen on its light colours regardless of the device's dark mode setting.
     *
     * This app's own Compose UI is light by construction — [com.gocheap.flights.ui.theme.FlightTheme]
     * only ever builds a light scheme — but the Travelpayouts SDK's screens are not: its `TaTheme`
     * descends from `Theme.Material3.DayNight` and the .aar ships `values-night` colours, so on a
     * device in dark mode the results list came back dark in the middle of an otherwise light app.
     * Those screens are AppCompat Activities, which is what makes this the lever that reaches them.
     */
    private fun disableDarkTheme() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }


    /**
     * Repeats the results Activity's window animations through the API that predictive back reads.
     *
     * `windowAnimationStyle` on the theme already covers the back *button*. The back *gesture* is
     * a different path: from Android 14 the system drives it, and it reads these overrides rather
     * than the theme — which is why the gesture kept playing the platform's cross-Activity scale
     * and fade while the button slid. The app targets well past that, so the gesture is what most
     * people will actually use.
     *
     * Set from here because the Activity belongs to the SDK: there is no `onCreate` of ours to put
     * it in, and this is the hook that runs before the window's transition is resolved.
     */
    private fun slideTheSdkResultsInAndOut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is TravelSdkActivity) return
                activity.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.nav_slide_in_from_end,
                    R.anim.nav_slide_out_to_start,
                )
                activity.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.nav_slide_in_from_start,
                    R.anim.nav_slide_out_to_end,
                )
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun initTravelSdk() {
        // The marker is an Int in the SDK's config despite being documented as a string, and an
        // absent or malformed one is the ordinary state of a checkout without credentials — so it
        // disables the integration rather than taking the app down on launch.
        val marker = BuildConfig.TRAVELPAYOUTS_MARKER.toIntOrNull()
        val apiKey = BuildConfig.TRAVELPAYOUTS_API_KEY
        if (marker == null || apiKey.isBlank()) {
            Log.w(
                LogTag,
                "Travelpayouts SDK not initialised: set TRAVELPAYOUTS_MARKER and " +
                    "TRAVELPAYOUTS_API_KEY in local.properties.",
            )
            return
        }

        if (!ensureFirebase()) {
            Log.w(
                LogTag,
                "Travelpayouts SDK not initialised: it builds a Firebase Analytics client as it " +
                    "starts, so this module needs a google-services.json (or the FIREBASE_* " +
                    "values in local.properties).",
            )
            return
        }

        try {
            val config = SdkConfig(
                marker = marker,
                apiKey = apiKey,
                appId = BuildConfig.APPLICATION_ID,
                appVersion = BuildConfig.VERSION_NAME,
                // The SDK's own info screen; this app reaches the SDK only for flight results, so
                // just the items that make sense without its tab bar around them.
                enabledInfoItems = setOf(
                    EnabledInfoItems.ABOUT_APP,
                    EnabledInfoItems.FAVORITES,
                ),
                flightsFavoritesEnabled = true,
            )
            TravelSdk.init(this, config)
            // Only now is the SDK able to answer, so only now does the flight search go to it.
            TravelSdkBridge.register()
            Log.i(LogTag, "Travelpayouts SDK initialised for marker $marker.")
        } catch (error: Throwable) {
            // A failed init must not cost the user the rest of the app: the stay and rental tabs
            // have nothing to do with this SDK, and flights fall back to the in-app results page.
            Log.e(LogTag, "Travelpayouts SDK failed to initialise.", error)
        }
    }

    /**
     * Makes sure a default Firebase app exists.
     *
     * Two things need one: this app's own GA4 reporting — see
     * `com.gocheap.flights.analytics.ga4.FlightAnalytics` — and the Travelpayouts SDK, which
     * builds a Firebase Analytics client as it initialises.
     *
     * Normally nothing to do: a `google-services.json` in this module has Firebase's own startup
     * provider create one before this runs, and that is the configuration to ship. This fills in
     * for a build without that file, from values kept beside the Travelpayouts credentials.
     * `FlightAnalyticsBridge.swift` is the iOS counterpart and takes the same two routes.
     */
    private fun ensureFirebase(): Boolean {
        if (FirebaseApp.getApps(this).isNotEmpty()) return true

        val appId = BuildConfig.FIREBASE_APP_ID
        val apiKey = BuildConfig.FIREBASE_API_KEY
        val projectId = BuildConfig.FIREBASE_PROJECT_ID
        if (appId.isBlank() || apiKey.isBlank() || projectId.isBlank()) return false

        return try {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApplicationId(appId)
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .build(),
            )
            true
        } catch (error: Throwable) {
            Log.e(LogTag, "Could not initialise Firebase for the Travelpayouts SDK.", error)
            false
        }
    }

    private companion object {
        const val LogTag = "TravelSdk"
    }
}
