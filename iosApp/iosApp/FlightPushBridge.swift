import Foundation
import UserNotifications
import Shared

// Firebase is a CocoaPods dependency, so it is only on the compiler's search path once
// `pod install` has been run and the app is opened from `iosApp.xcworkspace`. Everything that
// touches it sits behind this check, which keeps the project building before that — and for anyone
// who checks the repository out and builds it without pods. The app then runs with no push at all:
// `PushHandoff.sink` stays nil, no token is ever fetched, and nothing else about the app changes.
//
// The same arrangement `FlightCrashReportingBridge` and `TravelSdkFlights` are under.
#if canImport(FirebaseCore)
import FirebaseMessaging
#endif

/// Plugs Firebase Cloud Messaging into the shared module's push handling — the iOS counterpart of
/// `FlightMessagingService` and `PushNotifications.android.kt`, and the only file here that knows
/// Firebase Messaging exists.
///
/// Note how little of push this file actually owns. The permission dialog and the APNs
/// registration are `UserNotifications` and `UIKit`, which the shared module calls for itself —
/// see `PushNotifications.ios.kt`. What is left, and the only part that needs a pod, is the
/// registration token, topic subscriptions, and being the delegate the system hands a notification
/// to.
enum FlightPushBridge {

#if canImport(FirebaseCore)

    /// The object the system and Firebase hold as their delegate.
    ///
    /// Stored rather than created at the point of assignment because both delegate properties are
    /// `weak`: a freshly made instance assigned inline would be released before the first
    /// notification arrived, and the app would simply stop reacting to taps with nothing in the
    /// log to say why.
    private static let delegate = Delegate()

    /// Call once, from the app's launch, after Firebase has been configured.
    ///
    /// Only sets up delivery. It deliberately does not ask for permission — see
    /// `PushNotifications.requestPermissionOnce` on the Kotlin side for when that happens and why
    /// it is not here.
    static func start() {
        guard FirebaseBootstrap.configure() else {
            print("[Push] Not initialised: \(FirebaseBootstrap.notConfiguredMessage)")
            return
        }

        // Before `PushNotifications.start()`, so the token fetch it triggers has somewhere to go.
        UNUserNotificationCenter.current().delegate = delegate
        Messaging.messaging().delegate = delegate
        PushHandoff.shared.sink = Sink()
        PushNotifications.shared.start()
        print("[Push] Initialised.")
    }

    /// The APNs device token, handed over by the app delegate.
    ///
    /// Firebase's method swizzling normally does this on its own, but it is set explicitly so that
    /// turning swizzling off — which an app adding another notification SDK eventually does — does
    /// not silently break every send. Until Firebase has this, its registration token maps to no
    /// device and messages are accepted by the server and delivered nowhere.
    static func setApnsToken(_ token: Data) {
        Messaging.messaging().apnsToken = token
    }

    /// What the shared module drives messaging through.
    private final class Sink: PushMessagingSink {

        /// Asks for the current registration token.
        ///
        /// Fetched rather than waited for, for the same reason the Android half fetches it:
        /// `didReceiveRegistrationToken` fires on a *change*, so an install that already has a
        /// token would otherwise never report one.
        func start() {
            Messaging.messaging().token { token, error in
                if let token {
                    PushNotifications.shared.onTokenChanged(token: token)
                } else {
                    print("[Push] No registration token: \(error?.localizedDescription ?? "unknown")")
                }
            }
        }

        func subscribe(topic: String) {
            Messaging.messaging().subscribe(toTopic: topic) { error in
                if let error { print("[Push] Topic '\(topic)' failed: \(error.localizedDescription)") }
            }
        }

        func unsubscribe(topic: String) {
            Messaging.messaging().unsubscribe(fromTopic: topic) { error in
                if let error { print("[Push] Topic '\(topic)' failed: \(error.localizedDescription)") }
            }
        }
    }

    /// Everything the system and Firebase call back into.
    ///
    /// One object for both protocols because they are two halves of the same conversation — the
    /// token comes from Firebase, the notifications come from the system, and both end up at the
    /// same shared object.
    private final class Delegate: NSObject, UNUserNotificationCenterDelegate, MessagingDelegate {

        // MARK: - MessagingDelegate

        func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
            guard let fcmToken else { return }
            PushNotifications.shared.onTokenChanged(token: fcmToken)
        }

        // MARK: - UNUserNotificationCenterDelegate

        /// A push arriving while the app is open.
        ///
        /// iOS shows nothing in this case unless it is told to, which is the opposite of Android,
        /// where the system draws nothing in the foreground and `FlightMessagingService` posts the
        /// notification by hand. Both ends up the same: a push is visible whether or not the user
        /// happens to be looking at the app.
        func userNotificationCenter(
            _ center: UNUserNotificationCenter,
            willPresent notification: UNNotification,
            withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
        ) {
            let content = notification.request.content
            PushNotifications.shared.onReceived(
                title: content.title,
                body: content.body,
                data: Self.payload(from: content.userInfo)
            )
            completionHandler([.banner, .sound, .badge])
        }

        /// A notification the user tapped.
        ///
        /// Handed straight over and nothing more: what a tap should *do* is a shared decision, and
        /// lives wherever `PushNotifications.consumeTap()` is called.
        func userNotificationCenter(
            _ center: UNUserNotificationCenter,
            didReceive response: UNNotificationResponse,
            withCompletionHandler completionHandler: @escaping () -> Void
        ) {
            let content = response.notification.request.content
            PushNotifications.shared.onTapped(
                title: content.title,
                body: content.body,
                data: Self.payload(from: content.userInfo)
            )
            completionHandler()
        }

        /// The sender's own key-values, out of everything APNs delivered.
        ///
        /// Strings only, which is the filter rather than a convenience: `aps` is a dictionary and
        /// `google.c.*` carries numbers, none of it put there by whoever sent the push. What
        /// survives is what the sender wrote, in the same shape the Android half reads off an
        /// `Intent` — so that one `data` payload means the same thing on both platforms.
        static func payload(from userInfo: [AnyHashable: Any]) -> [String: String] {
            var result: [String: String] = [:]
            for (key, value) in userInfo {
                guard let key = key as? String, let value = value as? String else { continue }
                result[key] = value
            }
            return result
        }
    }

#else

    /// No pods, nothing to plug in. The app runs and receives nothing; see the comment at the top.
    static func start() {
        print("[Push] Not initialised: Firebase is not linked. Run `pod install` and open iosApp.xcworkspace.")
    }

    static func setApnsToken(_ token: Data) {}

#endif
}
