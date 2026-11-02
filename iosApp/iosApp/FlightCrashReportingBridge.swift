import Foundation
import Shared

// Firebase is a CocoaPods dependency, so it is only on the compiler's search path once
// `pod install` has been run and the app is opened from `iosApp.xcworkspace`. Everything that
// touches it sits behind this check, which keeps the project building before that — and for anyone
// who checks the repository out and builds it without pods. The app then runs exactly as it did
// before this integration existed, reporting nothing: `CrashHandoff.sink` stays nil and the shared
// module drops its breadcrumbs rather than queueing them.
//
// The same arrangement `FlightAnalyticsBridge` and `TravelSdkFlights` are under.
#if canImport(FirebaseCore)
import FirebaseCrashlytics
#endif

/// Plugs Crashlytics into the shared module's crash reporting — the iOS counterpart of
/// `FirebaseCrashlytics.getInstance()` in `CrashReporting.android.kt`, and the only file here that
/// knows Crashlytics exists.
///
/// The crashes themselves need none of this. Crashlytics installs its own signal and exception
/// handlers as it starts and files a report whether or not a line below ever runs. What the sink
/// adds is the context that makes a report worth reading — the screen, the region, the last few
/// things the user did — and a route for the shared code to report an error it *handled*, which by
/// definition never reaches a crash handler.
enum FlightCrashReportingBridge {

#if canImport(FirebaseCore)

    /// Call once, from the app's launch and as early as possible.
    ///
    /// Earlier matters here in a way it does not for analytics: `CrashReporting.start()` is also
    /// what installs Kotlin's unhandled-exception hook, and until it has, an uncaught Kotlin
    /// exception reaches Crashlytics as a bare `SIGABRT` inside the Kotlin runtime with none of the
    /// Kotlin stack that says which function threw.
    static func start() {
        guard FirebaseBootstrap.configure() else {
            print("[Crashlytics] Not initialised: \(FirebaseBootstrap.notConfiguredMessage)")
            return
        }

        // Before `CrashReporting.start()`, so that anything the shared module reports from inside
        // that call already has somewhere to go.
        CrashHandoff.shared.sink = Sink()
        CrashReporting.shared.start()
        print("[Crashlytics] Initialised.")
    }

    /// What the shared module reports through.
    ///
    /// Everything arriving here has already been trimmed and clipped to Crashlytics' own limits by
    /// the shared half, so there is nothing left to validate — this is a translation of four calls
    /// and nothing more.
    private final class Sink: CrashReportingSink {

        func log(message: String) {
            Crashlytics.crashlytics().log(message)
        }

        func setKey(name: String, value: String) {
            Crashlytics.crashlytics().setCustomValue(value, forKey: name)
        }

        func setUserId(id: String) {
            Crashlytics.crashlytics().setUserID(id)
        }

        /// One handled Kotlin error, as the three things Crashlytics models a non-fatal with.
        ///
        /// An `ExceptionModel` rather than an `NSError`: an error carries a domain and a code and
        /// is grouped on those, which would put every Kotlin exception in the app under one row.
        /// An exception model is grouped on its frames, which is what the shared side went to the
        /// trouble of flattening the Kotlin stack for.
        ///
        /// The frames arrive as the Kotlin runtime prints them — one string each, already carrying
        /// the symbol and offset — so the symbol is the only one of the three that is real.
        /// `StackFrame` has no symbol-only initialiser, so the empty file and line 0 below stand in
        /// for the two that are not known. Crashlytics shows a frame's symbol whether or not the
        /// other two are set, and inventing a file name for a Kotlin frame would only mislead
        /// whoever reads it.
        func record(name: String, reason: String, stackTrace: [String]) {
            let model = ExceptionModel(name: name, reason: reason)
            model.stackTrace = stackTrace.map { StackFrame(symbol: $0, file: "", line: 0) }
            Crashlytics.crashlytics().record(exceptionModel: model)
        }
    }

#else

    /// No pods, nothing to plug in. The app runs and reports nothing; see the comment at the top.
    static func start() {
        print("[Crashlytics] Not initialised: Firebase is not linked. Run `pod install` and open iosApp.xcworkspace.")
    }

#endif
}
