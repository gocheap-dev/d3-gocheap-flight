import Foundation

// Firebase is a CocoaPods dependency, so it is only on the compiler's search path once
// `pod install` has been run and the app is opened from `iosApp.xcworkspace`. Everything that
// touches it sits behind this check, which keeps the project building before that — and for anyone
// who checks the repository out and builds it without pods.
//
// The same arrangement `TravelSdkFlights` is under, and for the same reason.
#if canImport(FirebaseCore)
import FirebaseCore
#endif

/// Makes sure a Firebase app exists, once, for everything that reports through one.
///
/// Two things need it — Analytics and Crashlytics — and each has its own bridge. This used to live
/// inside `FlightAnalyticsBridge`, which made the crash reporter's correctness depend on the two
/// `start()` calls staying in the right order in `iOSApp.init`. It does not any more: whichever
/// bridge runs first configures Firebase, the second finds it already configured, and reordering
/// them in future breaks nothing.
///
/// `FlightTpApplication.ensureFirebase` is the Android counterpart and takes the same two routes,
/// deliberately: the two platforms are configured the same way.
enum FirebaseBootstrap {

#if canImport(FirebaseCore)

    /// True once there is a Firebase app to report through.
    ///
    /// `GoogleService-Info.plist` in the app target is the configuration to ship, and the plain
    /// `FirebaseApp.configure()` finds it on its own. The fallback below is for a checkout without
    /// that file, from the same values the Android module reads out of `local.properties`.
    ///
    /// The plist is looked for by hand rather than letting `configure()` fail: without one it logs
    /// a fatal error and takes the launch down, which is not an acceptable way for a build with no
    /// reporting configured to behave.
    @discardableResult
    static func configure() -> Bool {
        if FirebaseApp.app() != nil { return true }

        if let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let options = FirebaseOptions(contentsOfFile: path) {
            FirebaseApp.configure(options: options)
            return true
        }

        guard let options = Credentials.fromBundle() else { return false }
        FirebaseApp.configure(options: options)
        return true
    }

    /// What to print when `configure()` says no, so that every caller says the same thing.
    static let notConfiguredMessage =
        "add a GoogleService-Info.plist to the iosApp target, or set the FIREBASE_* values in Config.xcconfig."

    /// Firebase configuration reaching Swift through Info.plist, the way the Travelpayouts
    /// credentials already do. See `Config.xcconfig`.
    private enum Credentials {
        static func fromBundle() -> FirebaseOptions? {
            let info = Bundle.main.infoDictionary
            func value(_ key: String) -> String {
                (info?[key] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
            }

            let appId = value("FirebaseAppId")
            let senderId = value("FirebaseGcmSenderId")
            let apiKey = value("FirebaseApiKey")
            let projectId = value("FirebaseProjectId")
            // All four are required: `FirebaseOptions` takes the first two in its initialiser, and
            // nothing will upload without the other two.
            guard !appId.isEmpty, !senderId.isEmpty, !apiKey.isEmpty, !projectId.isEmpty else {
                return nil
            }

            let options = FirebaseOptions(googleAppID: appId, gcmSenderID: senderId)
            options.apiKey = apiKey
            options.projectID = projectId
            return options
        }
    }

#endif
}
