package com.gocheap.flights

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.gocheap.flights.data.settings.AppLocale
import com.gocheap.flights.data.settings.AppSettings
import com.gocheap.flights.data.storage.initFlightStorage
import com.gocheap.flights.push.PushIntents
import com.gocheap.flights.push.PushNotifications
import com.gocheap.flights.push.PushPermissionHost

class MainActivity : ComponentActivity() {

    /**
     * Where the answer from [notificationPermission] goes.
     *
     * Held rather than captured because the launcher has to be registered before the Activity
     * starts, while the thing waiting for the answer — a coroutine in the shared module — does not
     * exist until much later. Cleared as soon as it is called, so a later system-initiated result
     * cannot resume a coroutine that has already moved on.
     */
    private var notificationPermissionResult: ((Boolean) -> Unit)? = null

    /**
     * The `POST_NOTIFICATIONS` dialog.
     *
     * Registered unconditionally, including below Android 13 where the permission does not exist:
     * `registerForActivityResult` must be called before `onStart` regardless of whether it is ever
     * launched, and the shared module never launches it on those versions — see
     * `requestPushPermission`.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val callback = notificationPermissionResult
            notificationPermissionResult = null
            callback?.invoke(granted)
        }

    /**
     * Builds this activity's resources against the chosen language.
     *
     * The earliest hook there is, and it has to be: the configuration fixed here is what the
     * activity's resources — and so Compose's `Locale.current`, and so every `Res.string` —
     * resolve against for the whole life of the activity.
     *
     * The stored setting is read straight off storage rather than from [AppSettings], which is not
     * attached yet at this point in the launch.
     */
    override fun attachBaseContext(newBase: Context) {
        AppLocale.current = AppLocale.current ?: storedLanguage(newBase)
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app renders light whatever the system is set to (see FlightTheme), so the bars are
        // pinned to the light style rather than left on the default `auto`, which would follow the
        // system into dark and draw white status icons onto this app's white background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Before setContent: the recent stores read their saved lists the first time a screen
        // touches them, which happens during the very first composition below.
        initFlightStorage(this)

        // How a language change reaches the screen: the shared code sets the new code and calls
        // this, and the activity rebuilds against it. Without it the setting would save and
        // nothing on screen would move until the next cold start.
        AppLocale.recreateHost = { recreate() }

        // How the shared module reaches the dialog above. Registered here rather than in the
        // Application because a runtime permission needs an Activity to launch from.
        PushPermissionHost.requester = { onResult ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionResult = onResult
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Nothing to ask for; the shared half does not call this below 13, and answering
                // rather than hanging keeps that a detail of that file rather than a contract.
                onResult(true)
            }
        }

        // The intent that started this launch, which on a cold start from a tapped notification is
        // the only place the payload exists.
        handlePushIntent(intent)

        setContent {
            App()
        }
    }

    /**
     * A tap arriving while this Activity is already alive.
     *
     * The notification's `PendingIntent` is `SINGLE_TOP`, so a tap with the app already open is
     * delivered here instead of through `onCreate`. Without this, the second push of a session
     * would open the app and then do nothing.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // So that `getIntent()` and what is handled below agree — anything reading the Activity's
        // intent later would otherwise still see the one that cold-started it.
        setIntent(intent)
        handlePushIntent(intent)
    }

    /**
     * Hands a tapped notification to the shared module, if that is what this intent is.
     *
     * Only the handing over: what to *do* with a tap is a shared decision, and lives wherever
     * `PushNotifications.consumeTap()` is called.
     */
    private fun handlePushIntent(intent: Intent?) {
        if (!PushIntents.isFromPush(intent)) return
        val data = PushIntents.payload(intent)
        PushNotifications.onTapped(
            title = data["title"],
            body = data["body"],
            data = data,
        )
    }
}

/**
 * The stored language, read before anything else is initialised.
 *
 * The same preferences file [com.gocheap.flights.data.storage.initFlightStorage] hands to the
 * shared code, opened directly because `attachBaseContext` runs before that call.
 */
private fun storedLanguage(context: Context): String? =
    runCatching {
        context.getSharedPreferences("flighttp_prefs", Context.MODE_PRIVATE)
            .getString("app_settings", null)
            ?.let { Regex("\"languageCode\"\\s*:\\s*\"([a-zA-Z-]+)\"").find(it)?.groupValues?.get(1) }
    }.getOrNull()

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}