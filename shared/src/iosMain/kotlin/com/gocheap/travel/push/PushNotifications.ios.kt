package com.gocheap.travel.push

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
// An Objective-C category on UIApplication, which Kotlin/Native maps to an extension function —
// so it has to be imported by name rather than arriving with the class.
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Where the iOS app plugs Firebase Messaging in — the same seam, and for the same reason, as
 * [com.gocheap.travel.analytics.crash.CrashHandoff].
 *
 * Only the Firebase half goes through here. The permission dialog and the APNs registration below
 * are `UserNotifications` and `UIKit`, which this module can call directly; it is the registration
 * token and the topic subscriptions that live inside a pod, and so inside Swift.
 */
object PushHandoff {

    /** Written once from Swift at launch, read on whatever thread asked. See `CrashHandoff.sink`. */
    var sink: PushMessagingSink? = null
}

/**
 * What the Swift side implements.
 *
 * [start] is separate from registering the sink because the two happen at different moments: Swift
 * registers as the app launches, and the shared module decides when messaging actually begins.
 */
interface PushMessagingSink {
    fun start()
    fun subscribe(topic: String)
    fun unsubscribe(topic: String)
}

internal actual fun startPushMessaging() {
    val sink = PushHandoff.sink
    if (sink == null) {
        println("[Push] Not started: Firebase Messaging is not linked.")
        return
    }
    try {
        sink.start()
    } catch (error: Throwable) {
        println("[Push] Not started: ${error.message}")
    }
}

/**
 * Shows the system permission dialog, and on a yes registers with APNs.
 *
 * The registration is the part that is easy to leave out and impossible to notice: authorisation
 * alone gets the app a dialog and nothing else. Until `registerForRemoteNotifications` has run and
 * APNs has answered with a device token, Firebase has no address to map its registration token
 * onto, and every send is accepted by the server and delivered nowhere.
 *
 * iOS answers a repeat call from its stored decision without showing anything, so this is safe to
 * call again — but see [PushNotifications.requestPermissionOnce] for why almost nothing should.
 */
internal actual suspend fun requestPushPermission(): Boolean = suspendCancellableCoroutine { continuation ->
    val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
    var answered = false
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
        options = options,
        completionHandler = { granted: Boolean, error: NSError? ->
            // The completion handler is the system's to call, so this guards against a second call
            // the way the Android half guards against its launcher.
            if (!answered) {
                answered = true
                if (error != null) println("[Push] Permission error: ${error.localizedDescription}")
                if (granted) registerForRemoteNotifications()
                continuation.resume(granted)
            }
        },
    )
}

/**
 * Asks APNs for a device token.
 *
 * On the main queue because `UIApplication` is main-thread-only and the completion handler above
 * arrives on an arbitrary one. `NSOperationQueue` rather than `dispatch_async` for the same reason
 * `FlightAnalytics.ios.kt` uses it — it needs no interop opt-in.
 */
private fun registerForRemoteNotifications() {
    NSOperationQueue.mainQueue.addOperationWithBlock {
        UIApplication.sharedApplication.registerForRemoteNotifications()
    }
}

internal actual fun subscribeToPushTopic(topic: String) {
    PushHandoff.sink?.subscribe(topic)
}

internal actual fun unsubscribeFromPushTopic(topic: String) {
    PushHandoff.sink?.unsubscribe(topic)
}
