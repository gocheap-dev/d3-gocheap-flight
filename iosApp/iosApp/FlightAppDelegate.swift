import UIKit

/// The app delegate, which exists for exactly one reason: APNs hands a device token to an
/// application delegate and nowhere else.
///
/// SwiftUI has no equivalent hook — there is no scene callback or environment value for it — so a
/// push app needs one of these even when nothing else about it does. Deliberately thin: the rest
/// of the launch still happens in `iOSApp.init`, which runs earlier and already works.
///
/// Attached by `@UIApplicationDelegateAdaptor` in `iOSApp.swift`.
class FlightAppDelegate: NSObject, UIApplicationDelegate {

    /// APNs has issued a device token for this install.
    ///
    /// Firebase needs it to map its own registration token onto a real device. Without this the
    /// whole integration looks healthy from the inside — permission granted, a registration token
    /// in hand, sends accepted by the server — and not one notification is ever delivered.
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        FlightPushBridge.setApnsToken(deviceToken)
    }

    /// APNs refused.
    ///
    /// Ordinary on the Simulator before iOS 16 and on a build signed without the push entitlement,
    /// so it is a log line rather than anything louder. Everything else about the app is unaffected
    /// — it simply receives nothing.
    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[Push] APNs registration failed: \(error.localizedDescription)")
    }
}
