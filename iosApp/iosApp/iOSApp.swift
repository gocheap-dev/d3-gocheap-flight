import SwiftUI

@main
struct iOSApp: App {

    /// Only here for APNs, which hands its device token to an application delegate and to nothing
    /// else. See `FlightAppDelegate`.
    @UIApplicationDelegateAdaptor(FlightAppDelegate.self) private var appDelegate

    init() {
        // First of the three, and the one that has to be: it installs Kotlin's
        // unhandled-exception hook, so anything the two calls below throw is reported with its
        // Kotlin stack rather than as a bare abort inside the runtime. It also configures Firebase,
        // which the analytics bridge then finds already done.
        FlightCrashReportingBridge.start()

        // The shared module reports `app_launch` from the splash screen's ViewModel, which is
        // built as soon as the first frame is composed, and a sink registered after that would
        // miss it.
        FlightAnalyticsBridge.start()

        // Before the app finishes launching, which is the deadline that matters: a notification
        // tapped on a cold start is delivered to the system's delegate as the app comes up, and a
        // delegate registered after that misses the tap that opened the app.
        FlightPushBridge.start()

        // Before any screen is composed: the search card asks for a handler the moment its button
        // is pressed, and the SDK's own graph has to be standing by then.
        TravelSdkFlights.start()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
