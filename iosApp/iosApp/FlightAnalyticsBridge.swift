import Foundation
import Shared

// Firebase is a CocoaPods dependency, so it is only on the compiler's search path once
// `pod install` has been run and the app is opened from `iosApp.xcworkspace`. Everything that
// touches it sits behind this check, which keeps the project building before that — and for anyone
// who checks the repository out and builds it without pods. The app then runs exactly as it did
// before this integration existed, reporting nothing: `AnalyticsHandoff.sink` stays nil and the
// shared module drops its events rather than queueing them.
//
// The same arrangement `TravelSdkFlights` is under, and for the same reason.
#if canImport(FirebaseCore)
import FirebaseAnalytics
#endif

/// Plugs Firebase Analytics into the shared module's GA4 reporting — the iOS counterpart of
/// `FirebaseAnalytics.getInstance(context)` in `FlightAnalytics.android.kt`, and the only file here
/// that knows Firebase Analytics exists.
///
/// Every event in the app is built in common Kotlin, by `FlightAnalytics`, so that the two
/// platforms cannot report the same user action under different names or with different
/// parameters. All that is left for this side is to hand what arrives to the platform SDK, which is
/// what the sink below does.
enum FlightAnalyticsBridge {

#if canImport(FirebaseCore)

    /// Call once, from the app's launch and before any screen is composed. The shared module
    /// reports `app_launch` as its first act, so a sink registered after the first Compose frame
    /// would miss it.
    static func start() {
        guard FirebaseBootstrap.configure() else {
            print("[Ga4] Not initialised: \(FirebaseBootstrap.notConfiguredMessage)")
            return
        }

        // Only now can anything be reported, so only now does the shared module get somewhere to
        // report to.
        AnalyticsHandoff.shared.sink = Sink()
        print("[Ga4] Initialised.")
    }

    /// What the shared module reports through.
    ///
    /// The parameters arrive as `[String: Any]` holding `String` and the `NSNumber` subclasses
    /// Kotlin/Native boxes its numbers into, all of which Firebase takes unchanged — the shared
    /// half has already dropped the nulls and clipped every name and value to what GA4 accepts, so
    /// there is nothing left to validate here.
    private final class Sink: AnalyticsSink {
        func log(name: String, parameters: [String: Any]) {
            Analytics.logEvent(name, parameters: parameters)
        }
    }

#else

    /// No pods, nothing to plug in. The app runs and reports nothing; see the comment at the top.
    static func start() {
        print("[Ga4] Not initialised: Firebase is not linked. Run `pod install` and open iosApp.xcworkspace.")
    }

#endif
}
