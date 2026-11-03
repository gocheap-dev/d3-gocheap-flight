package com.gocheap.flights.data.user

import com.gocheap.flights.data.model.user.AddUserRequest
import com.gocheap.flights.data.network.ApiService
import com.gocheap.flights.utils.logDebug
import com.gocheap.flights.utils.logWarning
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * The one tag the whole sign-up logs under, referrer read included, so that `adb logcat -s AddUser`
 * shows the sequence end to end rather than half of it.
 */
internal const val AddUserLogTag = "AddUser"

/**
 * Registers this install with the backend, once, and keeps what comes back.
 *
 * The Android behaviour is the reference app's: skip outright if an id is already stored, otherwise
 * try three times with a short pause between attempts, and treat anything other than a response
 * carrying a `user_id` as a failed attempt. iOS runs the identical code over the identical Ktor
 * client — only the identifiers underneath it differ, which is the whole reason this lives in
 * common code rather than being written twice.
 *
 * Every one of those outcomes is logged under [AddUserLogTag]. Nothing on screen changes whether
 * this call succeeds or fails — the app runs on [platformAnonymousUserId] until an id exists — so
 * the log is the only place the difference is visible.
 */
internal class UserRepository(
    private val apiService: ApiService = ApiService(),
) {

    /**
     * True if this install is registered by the time this returns — including when it already was.
     *
     * "Already registered" counts as success, exactly as in the reference app: the caller is a
     * launch gate, and an install that has an id needs nothing further from this call.
     */
    suspend fun addUserIfNeeded(): Boolean {
        val existing = UserSession.current
        if (existing.isRegistered) {
            logDebug(AddUserLogTag, "SKIPPED — already registered as user_id=${existing.userId}")
            return true
        }

        repeat(MaxAttempts) { attempt ->
            val attemptNumber = attempt + 1
            if (addUserOnce(attemptNumber)) return true
            // No pause after the last attempt: nothing would use it.
            if (attempt < MaxAttempts - 1) delay(RetryDelayMillis)
        }

        logWarning(
            AddUserLogTag,
            "GAVE UP after $MaxAttempts attempts — install stays unregistered until next launch",
        )
        return false
    }

    /** One attempt. False on any failure, so [addUserIfNeeded] decides whether to try again. */
    private suspend fun addUserOnce(attempt: Int): Boolean = try {
        val identity = currentDeviceIdentity()
        val referral = resolveInstallReferral()

        logDebug(
            AddUserLogTag,
            "REQUEST attempt $attempt/$MaxAttempts app=$platformAppLabel " +
                "device_id=${identity.deviceId} device_id_type=${identity.deviceIdType} " +
                "acquired_route=${referral.route} " +
                "referrer_url=${referral.referrerUrl.ifBlank { "(none)" }}",
        )

        val response = apiService.addUser(
            AddUserRequest(
                deviceId = identity.deviceId,
                deviceIdType = identity.deviceIdType,
                app = platformAppLabel,
                vendorId = identity.vendorId,
                pseudoId = identity.pseudoId,
                acquiredRoute = referral.route,
                referrerUrl = referral.referrerUrl.takeIf(String::isNotBlank),
                // The three below are the email-capture flow's, which this app does not have yet.
                // Sent absent rather than empty so a later `users/update-email/` is what fills them.
                email = null,
                name = null,
                emailConsent = null,
            ),
        )

        // A 2xx with no id is not a registration: the backend answers this way when it rejects the
        // app label, and storing the response would leave the install permanently unregistered
        // with nothing to retry from. Logged as its own case because it is the one failure that
        // looks like a success from the network's side — "Invalid app" arrives as HTTP 200.
        val userId = response.userId
        if (userId == null) {
            logWarning(
                AddUserLogTag,
                "FAILED attempt $attempt/$MaxAttempts — 2xx carried no user_id " +
                    "(msg=${response.msg ?: "(none)"})",
            )
            return false
        }

        UserSession.save(
            UserSessionState(
                userId = userId,
                deviceId = identity.deviceId,
                deviceIdType = identity.deviceIdType,
                attribution = response.attribution,
                acquiredRoute = referral.route,
                referrerUrl = referral.referrerUrl,
            ),
        )

        logDebug(
            AddUserLogTag,
            "SUCCESS on attempt $attempt/$MaxAttempts — user_id=$userId " +
                "acquired_route=${referral.route} " +
                "campaign_id=${response.attribution?.campaignId ?: "(none)"}",
        )
        true
    } catch (cancellation: CancellationException) {
        // The caller is a launch-scoped coroutine; a cancelled sign-up is the screen going away,
        // not a failure to retry.
        logDebug(AddUserLogTag, "CANCELLED on attempt $attempt/$MaxAttempts")
        throw cancellation
    } catch (error: Throwable) {
        logWarning(
            AddUserLogTag,
            "FAILED attempt $attempt/$MaxAttempts — ${describeFailure(error)}",
            error,
        )
        false
    }

    /**
     * What went wrong, in the terms the backend owner would ask about.
     *
     * The status alone does not separate a rejected app label from a malformed body, so the
     * response body is pulled in where there is one — truncated, because an error page can be a
     * whole HTML document and logcat drops the rest of the line rather than the rest of the page.
     */
    private suspend fun describeFailure(error: Throwable): String = when (error) {
        is ResponseException -> {
            val body = runCatching { error.response.bodyAsText() }.getOrDefault("")
            "HTTP ${error.response.status} body=${body.take(MaxLoggedBodyChars).ifBlank { "(empty)" }}"
        }
        // No status to report: a timeout, a dropped connection, or a body that would not parse.
        else -> "${error::class.simpleName}: ${error.message ?: "(no message)"}"
    }

    private companion object {
        /** Three attempts, 500ms apart — the reference app's retry budget. */
        const val MaxAttempts = 3
        const val RetryDelayMillis = 500L

        /** Enough of an error body to identify it; logcat truncates a long line regardless. */
        const val MaxLoggedBodyChars = 300
    }
}
