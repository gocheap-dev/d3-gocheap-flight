package com.gocheap.travel.data.user

import com.gocheap.travel.data.model.user.UpdatePushTokenRequest
import com.gocheap.travel.data.settings.AppSettings
import com.gocheap.travel.push.PushNotifications
import com.gocheap.travel.utils.currentTimeZoneId
import com.gocheap.travel.utils.logDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Keeps the backend's copy of this install's push token current.
 *
 * `users/update-push-token/` needs six things at once, and on a first launch they arrive in no
 * fixed order and none of them at the moment the app starts: the `user_id` comes out of
 * `users/add/` behind the splash, the token comes out of Firebase, and the country and language
 * are whatever the region detection settles on a moment later. Calling the endpoint from any one
 * of those arrival points would mean calling it with the others missing.
 *
 * So this watches all three sources instead and sends whenever they together describe something
 * the backend has not been told. That covers every case without any of them being special: the
 * first launch, a rotated token, a user who changes country on the Profile tab, and a device
 * carried across a time zone.
 *
 * Deliberately not scoped to a ViewModel, for the reason [UserRegistration] is not either — this
 * outlives the splash that starts it, and a send that is still in flight when the first screen
 * appears has to finish.
 */
internal object PushTokenRegistration {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = PushTokenRepository()

    /** True once [start] has run, so a second splash in one process cannot double-subscribe. */
    private var started = false

    /**
     * The last reason reported for not sending, so the same one is not logged on every emission.
     *
     * The three flows below emit on every settings change and every stage of the sign-up, so an
     * unguarded "still waiting" line would repeat several times a second and bury the one line
     * that matters. Only a *change* of reason is worth a line.
     */
    private var lastHoldReason: String? = null

    /**
     * Begins watching. Returns immediately — nothing on screen depends on any of this.
     *
     * Never stops: the scope lives as long as the process, which is exactly as long as a token can
     * rotate for.
     */
    fun start() {
        if (started) return
        started = true

        scope.launch {
            combine(
                PushNotifications.token,
                UserSession.state,
                AppSettings.state,
            ) { token, session, settings ->
                // Null until there is something worth sending. Both halves are genuinely absent
                // for the first seconds of a first launch — the token is being fetched and the
                // sign-up is in flight — and a body missing either is rejected with a 400.
                //
                // Reported rather than passed over in silence, because "nothing was sent" and
                // "the send failed" look identical in a log that only speaks when it calls the
                // endpoint, and they have completely different causes. A HOLD line that never
                // turns into a RESULT one names the prerequisite that never arrived.
                // Non-null and trimmed up front: the hold check below does not narrow the
                // nullable `token` for the builder that follows it, and a local is clearer than
                // the second check it would otherwise take to.
                val registrationToken = token?.trim().orEmpty()
                val hold = when {
                    registrationToken.isEmpty() ->
                        "no registration token yet — Firebase has not issued one, or push is not configured"
                    !session.isRegistered ->
                        "no user_id yet — users/add has not succeeded"
                    else -> null
                }
                if (hold != null) {
                    if (hold != lastHoldReason) {
                        lastHoldReason = hold
                        logDebug(PushTokenLogTag, "HOLD — $hold")
                    }
                    return@combine null
                }
                lastHoldReason = null

                UpdatePushTokenRequest(
                    userId = session.userId,
                    // What this install registered under, read back rather than resolved again: a
                    // freshly resolved advertising id can differ from the one the backend deduped
                    // on, and this call has to name the row it means to update.
                    deviceId = session.deviceId,
                    token = registrationToken,
                    countryCode = settings.countryCode.uppercase(),
                    // Read at the moment the body is built rather than once at start: a device
                    // carried across a zone changes this without any of the flows emitting, and
                    // the next send for any other reason should carry the new one.
                    timezone = currentTimeZoneId(),
                    language = settings.languageCode,
                )
            }
                .filterNotNull()
                // Within the process. The repository has the across-launches half of this, which
                // needs storage and so cannot be done here.
                .distinctUntilChanged()
                // Latest, not every one: a send is three attempts and a round trip, and a token
                // that rotates mid-retry makes the one being retried worth abandoning rather than
                // finishing. The repository logs that as SUPERSEDED.
                .collectLatest { request ->
                    logDebug(
                        PushTokenLogTag,
                        "START users/update-push-token — user_id=${request.userId} " +
                            "country=${request.countryCode} language=${request.language}",
                    )
                    repository.send(request)
                }
        }
    }
}
