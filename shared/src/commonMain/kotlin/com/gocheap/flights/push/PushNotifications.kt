package com.gocheap.flights.push

import com.gocheap.flights.analytics.crash.CrashReporting
import com.gocheap.flights.data.storage.platformKeyValueStore
import com.gocheap.flights.utils.logDebug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One push, in the only shape both platforms can agree on.
 *
 * [data] is the half that matters to this app. A `notification` payload is drawn by the operating
 * system and this app never sees its text unless it was tapped; a `data` payload is this app's own
 * key-values and survives both routes. Anything that has to *do* something — a route to open, a
 * search to re-run, a campaign to report against — belongs there, which is why it is the only
 * non-null field.
 */
data class PushMessage(
    val title: String?,
    val body: String?,
    val data: Map<String, String>,
)

/**
 * Everything this app does with Firebase Cloud Messaging, as one object both platforms share.
 *
 * Built the way [com.gocheap.flights.analytics.ga4.FlightAnalytics] and
 * [com.gocheap.flights.analytics.crash.CrashReporting] are: the common half owns the state and the
 * vocabulary, and the platform halves own only what cannot be written once — the registration
 * token, a permission dialog, and on Android a `Service` the shared module cannot declare.
 *
 * What is deliberately *not* here: posting a notification. Both platforms draw a `notification`
 * payload themselves when the app is in the background, and the one case that needs drawing by
 * hand — a data-only message, on Android, in the foreground — needs a notification channel and a
 * `PendingIntent`, neither of which exists in common code. See `FlightMessagingService`.
 *
 * Public rather than internal because the native side registers into it.
 */
object PushNotifications {

    private val _token = MutableStateFlow<String?>(null)

    /**
     * The registration token a server addresses this install by, or null until one arrives.
     *
     * A flow rather than a suspend read because it genuinely changes: Firebase rotates a token on
     * restore-to-new-device, on a cleared app, and at its own discretion, and something that read
     * it once at launch would go on sending to a dead address for the life of the install.
     */
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _pendingTap = MutableStateFlow<PushMessage?>(null)

    /**
     * A notification the user tapped that has not been acted on yet.
     *
     * Held rather than delivered because a tap can arrive before there is anything to deliver it
     * to: on a cold start the operating system hands over the payload while the first screen is
     * still being composed. Whoever acts on it calls [consumeTap], which is what clears it — a
     * collector alone must not, or two collectors would each act on the same tap.
     */
    val pendingTap: StateFlow<PushMessage?> = _pendingTap.asStateFlow()

    /** True once [start] has run, so a second call cannot ask for a second token. */
    private var started = false

    /**
     * Call once per launch, after the platform's Firebase app exists.
     *
     * Only fetches the token. Permission is deliberately separate — see [requestPermissionOnce] —
     * because a token is issued whether or not the user has allowed anything to be shown with it,
     * and asking at launch is the worst moment to ask.
     */
    fun start() {
        if (started) return
        started = true
        startPushMessaging()
    }

    /* ------------------------------------ Permission ----------------------------------- */

    /**
     * Asks for notification permission, but only the first time this install ever asks.
     *
     * The flag is this app's own rather than the platform's, because the two platforms disagree
     * about what a second call does. iOS answers a repeat `requestAuthorization` from its stored
     * decision and shows nothing; Android shows the dialog again after a single denial, and only
     * stops after two. Without this, a user who said no once would be asked again on the next
     * launch, and on the one after that — which is how an app gets its notifications turned off in
     * system settings rather than merely declined.
     *
     * Returns whether notifications are allowed *now*, which is not the same as whether this call
     * asked: a second call returns the standing answer without a dialog.
     */
    suspend fun requestPermissionOnce(): Boolean {
        val alreadyAsked = runCatching { platformKeyValueStore.read(AskedKey) == Asked }
            .getOrDefault(false)
        if (alreadyAsked) return false

        val granted = requestPermission()
        // Written after the call rather than before it, so that the one chance this install gets
        // is spent on a dialog that was actually reached. A launch killed while the dialog is on
        // screen writes nothing and asks again — which is the right way round, because the only
        // thing worse than asking twice is never asking at all.
        runCatching { platformKeyValueStore.write(AskedKey, Asked) }
        return granted
    }

    /**
     * Asks outright, ignoring whether this install has asked before.
     *
     * For a deliberate opt-in — a switch on the Profile tab, say — where the user has just said
     * they want this and a dialog is the expected answer. Everything automatic should call
     * [requestPermissionOnce] instead.
     */
    suspend fun requestPermission(): Boolean {
        val granted = try {
            requestPushPermission()
        } catch (error: Throwable) {
            CrashReporting.record(error, origin = "push.requestPermission")
            false
        }
        CrashReporting.breadcrumb("push permission: ${if (granted) "granted" else "denied"}")
        return granted
    }

    /* -------------------------------------- Topics ------------------------------------- */

    /**
     * Starts delivering anything sent to [topic] to this install.
     *
     * Topics are how a campaign addresses "everyone searching from Brazil" without a server
     * keeping a list of tokens. Fire-and-forget on both platforms: the SDK retries a failed
     * subscription itself, and nothing on screen depends on the outcome.
     */
    fun subscribe(topic: String) {
        val clean = topic.trim()
        if (clean.isEmpty()) return
        try {
            subscribeToPushTopic(clean)
        } catch (error: Throwable) {
            CrashReporting.record(error, origin = "push.subscribe")
        }
    }

    /** The reverse of [subscribe]; the same fire-and-forget terms. */
    fun unsubscribe(topic: String) {
        val clean = topic.trim()
        if (clean.isEmpty()) return
        try {
            unsubscribeFromPushTopic(clean)
        } catch (error: Throwable) {
            CrashReporting.record(error, origin = "push.unsubscribe")
        }
    }

    /* ------------------------- What the platform halves call in ------------------------ */

    /**
     * A new registration token.
     *
     * Called from both platforms' token callbacks and from [start]'s initial fetch, so it can
     * arrive with the value it already holds; the flow drops that on its own.
     */
    fun onTokenChanged(token: String) {
        val clean = token.trim()
        if (clean.isEmpty()) return
        _token.value = clean
        // In full, and from here rather than from either platform half, so that one line covers
        // the initial fetch, a rotation, and both platforms. This is the value to paste into the
        // Firebase console to send a test push — `adb logcat -s Push` on Android, the Xcode
        // console on iOS.
        //
        // It is a device address rather than a credential: holding it lets a sender who also holds
        // the project's server key address this install, and nothing else. Logged unconditionally
        // for that reason — a token that only appears in debug builds is of no use for diagnosing
        // a release install that is not receiving anything.
        logDebug(LogTag, "Registration token: $clean")
        // The token is what a report has to be matched to a failed send with, and it is otherwise
        // only visible in a log line nobody reads until something is wrong.
        CrashReporting.setKey("push_token", clean)
    }

    /**
     * A notification the user tapped.
     *
     * Replaces whatever was pending rather than queueing behind it: two unacted taps mean the user
     * opened the app twice without the first having been handled, and the second one is the
     * intention that still stands.
     */
    fun onTapped(title: String?, body: String?, data: Map<String, String>) {
        val message = PushMessage(title = title, body = body, data = data)
        CrashReporting.breadcrumb("push tapped: ${data[RouteKey] ?: title ?: "(no route)"}")
        _pendingTap.value = message
    }

    /** A message that arrived while the app was open. Reported, not held — nothing tapped it. */
    fun onReceived(title: String?, body: String?, data: Map<String, String>) {
        CrashReporting.breadcrumb("push received: ${data[RouteKey] ?: title ?: "(no route)"}")
    }

    /**
     * Takes the pending tap, if there is one, and clears it.
     *
     * Returns null on every call after the first, which is the point: acting on a tap twice would
     * push the same screen twice onto the back stack.
     */
    fun consumeTap(): PushMessage? {
        var taken: PushMessage? = null
        _pendingTap.update { current ->
            taken = current
            null
        }
        return taken
    }

    /**
     * The `data` key a push names a destination with, so that the sender and whatever acts on
     * [pendingTap] cannot disagree about its spelling.
     *
     * The value is an `AppRoute` route string — `"home"`, `"hotel_results"` — which is what the
     * navigation host already keys on.
     */
    const val RouteKey = "route"

    /** The tag both platform halves already log under, so everything push lands together. */
    private const val LogTag = "Push"

    private const val AskedKey = "push_permission_asked"
    private const val Asked = "true"
}

/**
 * Readies the platform's messaging SDK and reports the current token through
 * [PushNotifications.onTokenChanged].
 *
 * **Must never throw.** Called from `Application.onCreate` and from the iOS app's launch, where
 * anything thrown takes the launch down.
 */
internal expect fun startPushMessaging()

/**
 * Shows the platform's notification permission dialog and waits for the answer.
 *
 * Returns the standing answer without a dialog where the platform already has one — which both do,
 * though they differ on what counts; see [PushNotifications.requestPermissionOnce].
 */
internal expect suspend fun requestPushPermission(): Boolean

/** Subscribes this install to a topic. Already trimmed and known non-empty. */
internal expect fun subscribeToPushTopic(topic: String)

/** The reverse. Already trimmed and known non-empty. */
internal expect fun unsubscribeFromPushTopic(topic: String)
