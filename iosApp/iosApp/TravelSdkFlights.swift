import Foundation
import UIKit
import Shared

// The SDK is a CocoaPods dependency, so it is only on the compiler's search path once
// `pod install` has been run and the app is opened from `iosApp.xcworkspace`. Everything that
// touches it sits behind this check, which keeps the project building before that — and for anyone
// who checks the repository out and builds it without pods. The flight search then falls back the
// way it did before this integration existed.
//
// Keyed on WLFlightsUnlimited rather than WLFlights because that is the framework the results
// screen comes from, and it is the newer of the two: a Pods project generated before it was
// vendored links WLFlights alone, and this build would then fail on an import rather than on a
// missing symbol at launch.
#if canImport(WLFlightsUnlimited)
import WLConfig
import WLFlights
import WLFlightsUnlimited
import WLShared
import WLUserInterface
#endif

/// Translates this app's search card into the Travelpayouts SDK's own search and opens its
/// results — the iOS counterpart of `TravelSdkBridge` on Android, and the only file here that
/// knows the SDK exists.
///
/// The SDK also ships screens for picking airports, dates and passengers, and a home screen that
/// gathers all three. This app keeps its own for all of it and borrows only the results, so the
/// search card's answer is the SDK's results list and nothing else: no SDK home screen is ever
/// presented, and there is none underneath the results to go back to.
///
/// That is what `WLFlightsUnlimited` is for. `WLFlights.ScreenProvider.flightsViewController(deeplink:)`
/// — what this used to call — returns the SDK's *flights home* controller and merely asks it to
/// respond to the deeplink, which leaves its own search form in the stack.
/// `WLFlightsUnlimited.ScreenProvider.flightsResultsViewController(parameters:ticketSignature:)`
/// is the results screen on its own. Its direct parameters API is used here because the deeplink
/// parser resolves existing shared tickets and cannot turn an unsigned search into a search model.
enum TravelSdkFlights {

#if canImport(WLFlightsUnlimited)

    /// Call once, from the app's launch. Nothing is registered when credentials are missing, so
    /// the search card behaves exactly as it did before the SDK existed.
    static func start() {
        guard let credentials = Credentials.fromBundle() else {
            print("[TravelSdk] Not initialised: set TRAVELPAYOUTS_MARKER and TRAVELPAYOUTS_API_KEY in Config.xcconfig.")
            return
        }

        // Needle's generated factories back the SDK's dependency graph; its screens cannot be
        // built before both modules have registered theirs. Qualified because the two modules
        // export the same function name. WLFlightsUnlimited adds no factories of its own — it
        // builds its screens out of the graph WLFlights registers here.
        WLShared.registerProviderFactories()
        WLFlights.registerProviderFactories()

        Configuration.setup(
            with: Configuration(
                appearance: Appearance(),
                constants: Configuration.Constants(
                    marker: credentials.marker,
                    clientDeviceHost: credentials.clientDeviceHost,
                    apiKey: credentials.apiKey,
                    appStoreId: credentials.appStoreId
                )
            )
        )
        // Still WLFlights': this switches the flights feature on as a whole, which is what both
        // the parse service and the results screen are built out of. WLFlightsUnlimited's own
        // provider has no such call.
        WLFlights.ScreenProvider.shared.enable()

        // Only now can the SDK answer, so only now does the flight search go to it.
        FlightResultsHandoff.shared.handler = Handler()
        print("[TravelSdk] Initialised for marker \(credentials.marker).")
    }

    /// What the shared module calls when the search card's button is pressed.
    ///
    /// The seam it implements is synchronous — "did something take this search" — so parameter
    /// validation happens before accepting it. Once accepted, this presents the SDK results screen;
    /// the Kotlin caller's fallback is only for a search this integration cannot start.
    private final class Handler: FlightResultsHandler {

        /// One search at a time. The overlay already swallows taps on the card underneath, but
        /// the search can also be started from somewhere the overlay does not cover, and two
        /// results screens stacked on each other is a trap to back out of.
        private var isOpening = false

        func show(search: SdkFlightSearch) -> Bool {
            guard let parameters = search.resultsParameters() else {
                // Which part gave way is printed by `resultsParameters` itself; this only says
                // the search was not started. It used to blame the date for every failure, which
                // is why a rejected cabin code read as an unparseable departure date.
                print("[TravelSdk] Refused: could not build a search for \(search.originIata)->\(search.destinationIata) on \(search.departureDate).")
                return false
            }
            // Already running: taken, but there is nothing new to do.
            guard !isOpening else {
                print("[TravelSdk] Ignored: a search is already being prepared.")
                return true
            }
            guard let window = UIApplication.shared.activeWindow,
                  let presenter = window.topViewController() else {
                print("[TravelSdk] Refused: no active window to present from.")
                return false
            }

            isOpening = true
            let overlay = SearchOverlay.show(over: window)
            print("[TravelSdk] Opening results for \(search.originIata)->\(search.destinationIata) on \(search.departureDate)...")

            DispatchQueue.main.async { [weak self, weak presenter] in
                self?.isOpening = false
                overlay.dismiss()
                guard let presenter, self != nil else {
                    print("[TravelSdk] Nothing left to present from; the search was dropped.")
                    return
                }
                print("[TravelSdk] Opening the results screen.")
                presenter.presentFlightResults(for: parameters)
            }

            return true
        }
    }

    /// The SDK derives its whole palette from one base colour, so handing it this app's blue is
    /// all it takes for the results to match the card they came from — the iOS counterpart of the
    /// `ta_primary_light` resource override on Android.
    private struct Appearance: BaseVersionAppearanceConfiguration {
        /// `FlightColors.Primary`.
        var baseColorVariant: ColorVariant? { .hex("#4A8DF6") }

        /// 16pt corners and outline icons, which is what this app's own components use.
        var cornerStyle: CornerStyle { .round }
        var iconStyle: IconStyle { .line }
        var palette: ColorPalette { .hsv }
    }

#else

    /// Without the pods there is nothing to hand a search to, so none is registered and the search
    /// card keeps whatever behaviour it has without an SDK.
    static func start() {
        print("[TravelSdk] Flights SDK not linked: run `pod install` in iosApp/ and open iosApp.xcworkspace.")
    }

#endif

    /// Read from the bundle rather than compiled in, so the keys live in a file that is not
    /// committed — the iOS counterpart of the Android module's `local.properties`.
    private struct Credentials {
        let marker: String
        let apiKey: String
        let clientDeviceHost: String
        let appStoreId: String?

        static func fromBundle() -> Credentials? {
            let info = Bundle.main.infoDictionary
            func value(_ key: String) -> String {
                (info?[key] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
            }

            let marker = value("TravelpayoutsMarker")
            let apiKey = value("TravelpayoutsApiKey")
            guard !marker.isEmpty, !apiKey.isEmpty else { return nil }

            let appStoreId = value("TravelpayoutsAppStoreId")
            return Credentials(
                marker: marker,
                apiKey: apiKey,
                clientDeviceHost: value("TravelpayoutsClientDeviceHost"),
                appStoreId: appStoreId.isEmpty ? nil : appStoreId
            )
        }
    }
}

#if canImport(WLFlightsUnlimited)

private extension SdkFlightSearch {
    /// The SDK exposes `FlightsStartSearchParameters` as the input to its results screen. The
    /// deeplink parser is for resolving a real shared ticket and rejects synthetic searches.
    /// These selection wrappers are Codable, but their backing values are intentionally hidden
    /// behind `spiModel`. Decode that same backing shape rather than depending on internal SDK APIs.
    func resultsParameters() -> FlightsStartSearchParameters? {
        guard let forthDate = Self.isoFormatter.date(from: departureDate) else { return nil }
        let backDate = returnDate.flatMap { Self.isoFormatter.date(from: $0) }
        guard let origin = Self.locationSelection(code: originIata),
              let destination = Self.locationSelection(code: destinationIata) else { return nil }
        let calendarModel: [String: Any]
        if let backDate {
            // `ClosedRange`'s synthesised Codable writes an unkeyed pair — [lowerBound, upperBound]
            // — not an object with those two names. The object form decoded as a type mismatch,
            // which took out every round trip; one-way searches went down the `single` branch
            // below and were unaffected.
            calendarModel = ["range": ["_0": [
                forthDate.timeIntervalSinceReferenceDate,
                backDate.timeIntervalSinceReferenceDate
            ]]]
        } else {
            calendarModel = ["single": ["_0": forthDate.timeIntervalSinceReferenceDate]]
        }
        let calendar = Self.decode(FlightsCalendarSelection.self, from: ["spiModel": calendarModel])
        // The cabin goes over the wire as the IATA letter, which is what `tripClass` already
        // carries from Kotlin. Not the public `FlightsPassengersSelection.TripClass` raw value:
        // that enum reads "economy"/"business", but it is only a façade over the `spiModel`
        // backing type being decoded here, whose own TripClass is "Y"/"C". Encoding the SDK's
        // own `.default` shows it plainly — `{"spiModel":{...,"tripClass":"Y"}}` — and passing
        // the façade's spelling instead is what made this decode fail, and with it every flight
        // search on iOS.
        //
        // The SDK knows two cabins where the card offers four, so the premium cabins fold into
        // the one they are sold beside: first searches business, premium economy searches
        // economy. That is the same fold the Android bridge makes.
        let cabin = (tripClass == "C" || tripClass == "F") ? "C" : "Y"
        let passengers = Self.decode(FlightsPassengersSelection.self, from: [
            "spiModel": [
                "adultsCount": Int(adults),
                "childrenCount": Int(children),
                "infantsCount": Int(infants),
                "tripClass": cabin
            ]
        ])
        guard let calendar, let passengers else { return nil }
        return FlightsStartSearchParameters(
            originSelection: origin,
            destinationSelection: destination,
            calendarSelection: calendar,
            passengersSelection: passengers
        )
    }

    private static func locationSelection(code: String) -> FlightsLocationsSelection? {
        decode(FlightsLocationsSelection.self, from: [
            "spiModel": [
                "id": code,
                "type": FlightsLocationsSelection.ObjectType.city.rawValue,
                "code": code,
                "city_code": code,
                "city_name": code,
                "name": code,
                "country_name": "",
                "main_airport_name": NSNull(),
                "cases": NSNull(),
                "city_cases": NSNull(),
                "country_cases": NSNull()
            ]
        ])
    }

    private static func decode<T: Decodable>(_ type: T.Type, from object: [String: Any]) -> T? {
        guard JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object) else { return nil }
        do {
            return try JSONDecoder().decode(type, from: data)
        } catch {
            print("[TravelSdk] Could not build search selection \(type): \(error)")
            return nil
        }
    }

    /// Fixed locale and UTC: these are calendar days off the search card, not moments in time, and
    /// a device calendar that is not Gregorian would otherwise read them as different days.
    static let isoFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

private extension UIViewController {

    /// The results list, presented on its own. Full screen rather than a sheet: the SDK draws its
    /// own navigation bar and back button, and a card that can be swiped away mid-search reads as
    /// a dropped search.
    ///
    /// Presented as a push rather than with UIKit's default modal, which rises from the bottom.
    /// The stay and car searches answer with an in-app screen that the navigation graph slides in
    /// from the trailing edge, and flights is the same step in the same flow — it only looks
    /// different because its results come from the SDK as a view controller rather than as a
    /// composable. `PushTransition` makes the seam invisible by giving the presentation the push
    /// UIKit would have used had this been on a navigation stack.
    func presentFlightResults(for parameters: FlightsStartSearchParameters) {
        let results = WLFlightsUnlimited.ScreenProvider.shared.flightsResultsViewController(
            parameters: parameters,
            ticketSignature: nil
        )
        PushTransition.install(on: results)
        present(results, animated: true) {
            // The SDK doesn't expose a font option on its appearance configuration. Apply the
            // app's bundled Plus Jakarta Sans faces to the UIKit text it has created, keeping its
            // sizing and emphasis intact. Run after presentation so the SDK has loaded its initial content.
            results.view.applyPlusJakartaSansRecursively()
        }
    }

}

private extension UIView {

    func applyPlusJakartaSansRecursively() {
        if let label = self as? UILabel {
            label.font = UIFont.plusJakartaSans(replacing: label.font)
        } else if let button = self as? UIButton, let font = button.titleLabel?.font {
            button.titleLabel?.font = UIFont.plusJakartaSans(replacing: font)
        } else if let field = self as? UITextField {
            field.font = UIFont.plusJakartaSans(replacing: field.font ?? .systemFont(ofSize: 17))
        } else if let textView = self as? UITextView {
            textView.font = UIFont.plusJakartaSans(replacing: textView.font ?? .systemFont(ofSize: 17))
        }

        subviews.forEach { $0.applyPlusJakartaSansRecursively() }
    }
}

private extension UIFont {

    static func plusJakartaSans(replacing font: UIFont) -> UIFont {
        let fontTraits = font.fontDescriptor.object(forKey: .traits) as? [UIFontDescriptor.TraitKey: Any]
        let weight = fontTraits?[.weight] as? CGFloat ?? 0
        let face: String
        switch weight {
        case ..<0.15: face = "PlusJakartaSans-Regular"
        case ..<0.27: face = "PlusJakartaSans-Medium"
        case ..<0.35: face = "PlusJakartaSans-SemiBold"
        case ..<0.48: face = "PlusJakartaSans-Bold"
        default: face = "PlusJakartaSans-ExtraBold"
        }
        return UIFont(name: face, size: font.pointSize) ?? font
    }
}

/// A brief spinner over this app's own search card while the results controller is prepared.
///
/// A view over what is already on screen rather than a presented placeholder controller: the
/// SDK's results then remain the only thing ever presented, with no empty screen to dismiss first.
///
/// Added to the window rather than to the view of the controller on screen. Compose Multiplatform
/// draws through a `UIHostingController`, which owns its view's subviews outright and warns that
/// adding one is unsupported and may break the view hierarchy. The window is the common superview
/// that warning points at: a subview added there sits above the hosting controller's view because
/// it is added after it, without reaching inside it.
private final class SearchOverlay: UIView {

    private let spinner = UIActivityIndicatorView(style: .large)

    static func show(over window: UIWindow) -> SearchOverlay {
        let overlay = SearchOverlay(frame: window.bounds)
        overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        // Dimmed rather than opaque so the card stays readable underneath, and interactive by
        // default, which is what stops a second tap on Search reaching the button behind it.
        overlay.backgroundColor = UIColor.black.withAlphaComponent(0.25)

        overlay.spinner.color = .white
        overlay.spinner.translatesAutoresizingMaskIntoConstraints = false
        overlay.addSubview(overlay.spinner)
        NSLayoutConstraint.activate([
            overlay.spinner.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            overlay.spinner.centerYAnchor.constraint(equalTo: overlay.centerYAnchor),
        ])
        overlay.spinner.startAnimating()

        overlay.alpha = 0
        window.addSubview(overlay)
        // A short fade keeps the overlay from appearing as a sudden flash.
        UIView.animate(withDuration: 0.15) { overlay.alpha = 1 }
        return overlay
    }

    func dismiss() {
        spinner.stopAnimating()
        removeFromSuperview()
    }
}

private extension UIApplication {
    /// The window the app is on screen in — what the spinner is hung on, and where the controller
    /// to present from is found. Both come from the same window so they cannot disagree about
    /// which one that is.
    var activeWindow: UIWindow? {
        let scene = connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        return scene?.windows.first { $0.isKeyWindow } ?? scene?.windows.first
    }
}

private extension UIWindow {
    /// What the SDK's results are presented from: Compose Multiplatform runs inside one hosting
    /// controller, so there is nothing else in scope to present from.
    func topViewController() -> UIViewController? {
        var top = rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

#endif


/// Presents and dismisses a controller the way `UINavigationController` pushes and pops one:
/// the arriving screen travels the full width from the trailing edge while the one it covers
/// slides a quarter of that and dims beneath it.
///
/// UIKit has no modal presentation style for this — `.coverVertical`, `.crossDissolve`,
/// `.flipHorizontal` and `.partialCurl` are the whole set — so the push has to be written out as
/// a transition. Everything here is UIKit only; it knows nothing about the SDK and is kept
/// outside the `canImport` above so the file still builds without the pods.
final class PushTransition: NSObject, UIViewControllerTransitioningDelegate {

    /// How far the covered screen travels, against the full width of the one covering it.
    private static let parallax: CGFloat = 0.25

    /// How dark the covered screen goes once it is all the way under.
    private static let veilAlpha: CGFloat = 0.12

    /// The navigation library's own iOS spec, which is what the stay and car results animate on.
    /// Matching it here is the point of the exercise: a flight push a third quicker than the other
    /// two would still read as a different screen arriving a different way.
    private static let duration: TimeInterval = 0.5
    private static let curveFrom = CGPoint(x: 0.2833, y: 0.99)
    private static let curveTo = CGPoint(x: 0.31833, y: 0.99)

    private static var delegateKey: UInt8 = 0

    /// `transitioningDelegate` is a weak reference, so the delegate is tied to the lifetime of the
    /// controller it drives. Without that it is released before the transition starts and UIKit
    /// quietly falls back to the default modal — the bottom-up slide this exists to replace.
    static func install(on presented: UIViewController) {
        let delegate = PushTransition()
        presented.modalPresentationStyle = .custom
        // `.custom` hands the status bar to the presenter unless asked otherwise, and the results
        // screen is full screen, so it should be the one deciding.
        presented.modalPresentationCapturesStatusBarAppearance = true
        presented.transitioningDelegate = delegate
        objc_setAssociatedObject(
            presented, &delegateKey, delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC
        )
    }

    func presentationController(
        forPresented presented: UIViewController,
        presenting: UIViewController?,
        source: UIViewController
    ) -> UIPresentationController? {
        KeepPresenterPresentationController(presentedViewController: presented, presenting: presenting)
    }

    func animationController(
        forPresented presented: UIViewController,
        presenting: UIViewController,
        source: UIViewController
    ) -> UIViewControllerAnimatedTransitioning? {
        PushAnimator(isPresenting: true, parallax: Self.parallax, veilAlpha: Self.veilAlpha,
                     duration: Self.duration, curveFrom: Self.curveFrom, curveTo: Self.curveTo)
    }

    func animationController(
        forDismissed dismissed: UIViewController
    ) -> UIViewControllerAnimatedTransitioning? {
        PushAnimator(isPresenting: false, parallax: Self.parallax, veilAlpha: Self.veilAlpha,
                     duration: Self.duration, curveFrom: Self.curveFrom, curveTo: Self.curveTo)
    }
}

/// A full-screen presentation that leaves the presenting view in the container.
///
/// The default for a full-screen modal is to take it out once the transition ends, which is fine
/// when nothing is animating it — but the parallax needs that view on screen and moving for the
/// whole of the push, and a view UIKit has removed cannot provide it.
private final class KeepPresenterPresentationController: UIPresentationController {
    override var shouldRemovePresentersView: Bool { false }
    override var frameOfPresentedViewInContainerView: CGRect { containerView?.bounds ?? .zero }
}

private final class PushAnimator: NSObject, UIViewControllerAnimatedTransitioning {

    private let isPresenting: Bool
    private let parallax: CGFloat
    private let veilAlpha: CGFloat
    private let duration: TimeInterval
    private let curveFrom: CGPoint
    private let curveTo: CGPoint

    init(
        isPresenting: Bool,
        parallax: CGFloat,
        veilAlpha: CGFloat,
        duration: TimeInterval,
        curveFrom: CGPoint,
        curveTo: CGPoint
    ) {
        self.isPresenting = isPresenting
        self.parallax = parallax
        self.veilAlpha = veilAlpha
        self.duration = duration
        self.curveFrom = curveFrom
        self.curveTo = curveTo
    }

    func transitionDuration(using context: UIViewControllerContextTransitioning?) -> TimeInterval {
        duration
    }

    func animateTransition(using context: UIViewControllerContextTransitioning) {
        let container = context.containerView
        let width = container.bounds.width
        let offset = width * parallax

        // `view(forKey:)` is only populated for views UIKit is itself adding or removing. Because
        // the presenter's view stays put, one side of each transition comes back nil and has to be
        // reached through its controller instead.
        let incoming = context.view(forKey: .to) ?? context.viewController(forKey: .to)?.view
        let outgoing = context.view(forKey: .from) ?? context.viewController(forKey: .from)?.view

        guard let incoming, let outgoing else {
            context.completeTransition(false)
            return
        }

        let covered = isPresenting ? outgoing : incoming
        let covering = isPresenting ? incoming : outgoing

        if covering.superview == nil {
            container.addSubview(covering)
        }
        if covered.superview == nil {
            container.insertSubview(covered, belowSubview: covering)
        }
        covering.frame = container.bounds
        covered.frame = container.bounds

        let veil = UIView(frame: container.bounds)
        veil.backgroundColor = .black
        veil.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        veil.isUserInteractionEnabled = false
        container.insertSubview(veil, aboveSubview: covered)

        covering.transform = CGAffineTransform(translationX: isPresenting ? width : 0, y: 0)
        covered.transform = CGAffineTransform(translationX: isPresenting ? 0 : -offset, y: 0)
        veil.alpha = isPresenting ? 0 : veilAlpha

        // A property animator rather than `UIView.animate`, which only takes the four named
        // curves — the library's spec is a bezier, and this is the API that accepts one.
        let animator = UIViewPropertyAnimator(
            duration: duration,
            controlPoint1: curveFrom,
            controlPoint2: curveTo
        )
        animator.addAnimations {
            covering.transform = CGAffineTransform(translationX: self.isPresenting ? 0 : width, y: 0)
            covered.transform = CGAffineTransform(translationX: self.isPresenting ? -offset : 0, y: 0)
            veil.alpha = self.isPresenting ? self.veilAlpha : 0
        }
        animator.addCompletion { _ in
            // Transforms are per-transition staging, not state: left behind, the shifted view
            // stays a quarter off screen for as long as it lives.
            covering.transform = .identity
            covered.transform = .identity
            veil.removeFromSuperview()
            context.completeTransition(!context.transitionWasCancelled)
        }
        animator.startAnimation()
    }
}
