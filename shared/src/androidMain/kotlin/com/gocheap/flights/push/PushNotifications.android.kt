package com.gocheap.flights.push

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.gocheap.flights.data.storage.AndroidAppContext
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** The tag the whole integration logs under: `adb logcat -s Push`. */
private const val LogTag = "Push"

/**
 * How the shared module reaches a permission dialog.
 *
 * `POST_NOTIFICATIONS` is a runtime permission from Android 13, and a runtime permission needs an
 * `Activity` to launch its contract from — which the shared module does not have and should not
 * hold. So the Activity registers itself here instead, exactly as it already does for
 * `AppLocale.recreateHost`.
 *
 * Null before `MainActivity.onCreate` and after the process is restored without one, which is a
 * real case: a message received in the background runs this app's code with no Activity alive.
 * Nothing asks for permission from there, but the null is handled rather than assumed away.
 */
object PushPermissionHost {

    /**
     * Set by `MainActivity`. Shows the system dialog and calls back with the answer, on the main
     * thread.
     *
     * A callback rather than a suspend function because what is on the other side is an
     * `ActivityResultLauncher`, which is callback-shaped and has to be registered before the
     * Activity is started.
     */
    var requester: ((onResult: (Boolean) -> Unit) -> Unit)? = null
}

/**
 * Asks Firebase for the current registration token.
 *
 * Nothing else to start: the messaging SDK's own startup provider has already run by the time this
 * is called, and `FlightMessagingService` is wired up in the manifest rather than here. The token
 * is fetched rather than waited for, because `onNewToken` only fires when a token *changes* — an
 * install that already has one would otherwise never report it.
 */
internal actual fun startPushMessaging() {
    try {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> PushNotifications.onTokenChanged(token) }
            .addOnFailureListener { error ->
                // The ordinary state of a build with no Firebase app, and of a device with no
                // working Play Services. Neither is worth more than a log line: everything else in
                // the app works without a token.
                Log.w(LogTag, "No registration token: ${error.message}")
            }
    } catch (error: Throwable) {
        Log.w(LogTag, "Messaging not started: ${error.message}", error)
    }
}

/**
 * Whether this app may show a notification, asking if it has not been decided.
 *
 * Three cases, in order. Below Android 13 there is no runtime permission at all and the only thing
 * that can be false is the user having turned notifications off in system settings. From 13 the
 * permission may already be held, in which case asking would show nothing and the answer is already
 * known. Otherwise it goes to the Activity, and if there is no Activity to go to — a background
 * process — the honest answer is "not granted" rather than a dialog that cannot be shown.
 */
internal actual suspend fun requestPushPermission(): Boolean {
    val context = AndroidAppContext.requireOrNull() ?: return false

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return notificationsEnabled(context)
    }

    val alreadyGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (alreadyGranted) return notificationsEnabled(context)

    val requester = PushPermissionHost.requester
    if (requester == null) {
        Log.d(LogTag, "Not asking for permission: no Activity registered.")
        return false
    }

    return suspendCancellableCoroutine { continuation ->
        // The launcher calls back exactly once in practice, but a resumed continuation that is
        // resumed again throws — and this one is resumed from a callback the framework owns.
        var answered = false
        requester { granted ->
            if (answered) return@requester
            answered = true
            continuation.resume(granted)
        }
    }
}

/**
 * Whether notifications are switched on for this app at all.
 *
 * Separate from the permission check and applied on top of it: from Android 13 holding
 * `POST_NOTIFICATIONS` and having notifications enabled are two different things — the permission
 * can be granted and then the whole app muted from system settings — and a caller asking "may I
 * show a notification" wants both.
 */
private fun notificationsEnabled(context: Context): Boolean = try {
    context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false
} catch (error: Throwable) {
    Log.w(LogTag, "Could not read notification settings: ${error.message}", error)
    false
}

internal actual fun subscribeToPushTopic(topic: String) {
    FirebaseMessaging.getInstance().subscribeToTopic(topic)
        .addOnFailureListener { error -> Log.w(LogTag, "Topic '$topic' failed: ${error.message}") }
}

internal actual fun unsubscribeFromPushTopic(topic: String) {
    FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
        .addOnFailureListener { error -> Log.w(LogTag, "Topic '$topic' failed: ${error.message}") }
}
