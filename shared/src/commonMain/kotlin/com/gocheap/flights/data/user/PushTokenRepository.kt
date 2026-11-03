package com.gocheap.flights.data.user

import com.gocheap.flights.analytics.crash.CrashReporting
import com.gocheap.flights.data.model.user.UpdatePushTokenRequest
import com.gocheap.flights.data.network.ApiService
import com.gocheap.flights.data.storage.platformKeyValueStore
import com.gocheap.flights.utils.logDebug
import com.gocheap.flights.utils.logWarning
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The tag the whole push-token upload logs under: `adb logcat -s PushToken`. */
internal const val PushTokenLogTag = "PushToken"

/**
 * Stores this install's FCM registration token against its `user_id`.
 *
 * Built the way [UserRepository] is — three attempts, a short pause between them, every outcome
 * logged — because it fails the same ways and matters the same amount: nothing on screen changes
 * whether this succeeds, and the only visible difference is that a push sent to this install
 * arrives or does not.
 *
 * What is *not* here is when to call it. That is [PushTokenRegistration]'s, because the answer is
 * "whenever any of six fields changes", which is a question about flows rather than about HTTP.
 */
internal class PushTokenRepository(
    private val apiService: ApiService = ApiService(),
) {

    /**
     * Sends [request] unless the identical body has already been stored.
     *
     * The skip is what stops a POST on every cold start of an install whose token, user, region and
     * zone are all unchanged — which is the ordinary case, and which this endpoint has no use for.
     * Delete the two [lastSent] lines to turn this into a per-launch heartbeat instead.
     *
     * Returns true if the backend holds this body by the time it returns, a skip included.
     */
    suspend fun send(request: UpdatePushTokenRequest): Boolean {
        val signature = request.signature()
        if (lastSent() == signature) {
            return finish(Skipped, request, "the backend already holds this exact body")
        }

        var lastFailure = "(no attempt was made)"
        repeat(MaxAttempts) { attempt ->
            val attemptNumber = attempt + 1
            val failure = sendOnce(request, attemptNumber)
            if (failure == null) {
                rememberSent(signature)
                return finish(Success, request, "stored on attempt $attemptNumber/$MaxAttempts")
            }
            lastFailure = failure
            // No pause after the last attempt: nothing would use it.
            if (attempt < MaxAttempts - 1) delay(RetryDelayMillis)
        }

        return finish(
            Failed,
            request,
            "gave up after $MaxAttempts attempts — last error: $lastFailure. This install is not " +
                "addressable until the token, the user or the region changes, or the app is " +
                "launched again",
        )
    }

    /**
     * The one line per call that says whether the backend has the token.
     *
     * Every path through [send] ends here, in the same shape, so the question "did this install's
     * token reach the database" is answered by one `grep` rather than by reading the attempts and
     * inferring it: `adb logcat -s PushToken | grep RESULT=` on Android, the same filter over the
     * Xcode console on iOS.
     *
     * Also recorded on the crash reporter, which is the only way to answer the same question about
     * an install that is not plugged into anything — a key for the standing state, a breadcrumb so
     * it is visible in the sequence of a report.
     */
    private fun finish(
        outcome: String,
        request: UpdatePushTokenRequest,
        detail: String,
    ): Boolean {
        val stored = outcome != Failed
        val line = "RESULT=$outcome user_id=${request.userId} token=${request.token} — $detail"
        if (stored) logDebug(PushTokenLogTag, line) else logWarning(PushTokenLogTag, line)

        CrashReporting.setKey("push_token_synced", outcome)
        CrashReporting.breadcrumb("push token $outcome for user ${request.userId}")
        return stored
    }

    /**
     * One attempt.
     *
     * Null when the backend accepted the body; the reason it did not, otherwise — returned rather
     * than only logged so that the terminal [finish] line can name the failure that actually ended
     * the call, instead of making the reader scroll back through three attempts to find it.
     */
    private suspend fun sendOnce(request: UpdatePushTokenRequest, attempt: Int): String? = try {
        logDebug(
            PushTokenLogTag,
            "REQUEST attempt $attempt/$MaxAttempts user_id=${request.userId} " +
                "device_id=${request.deviceId} country=${request.countryCode} " +
                "language=${request.language} timezone=${request.timezone} " +
                "token=${request.token}",
        )

        val response = apiService.updatePushToken(request)
        logDebug(
            PushTokenLogTag,
            "RESPONSE attempt $attempt/$MaxAttempts accepted — msg=${response.msg ?: "(none)"}",
        )
        null
    } catch (cancellation: CancellationException) {
        // A newer token or a changed region arrived and the collector cancelled this send in
        // favour of it. Not a failure, and not something to retry — the replacement is already
        // on its way, and it will log its own RESULT.
        logDebug(PushTokenLogTag, "SUPERSEDED on attempt $attempt/$MaxAttempts")
        throw cancellation
    } catch (error: Throwable) {
        val reason = describeFailure(error)
        logWarning(PushTokenLogTag, "FAILED attempt $attempt/$MaxAttempts — $reason", error)
        reason
    }

    /**
     * What went wrong, in the terms the backend owner would ask about — the same treatment
     * [UserRepository.describeFailure] gives it, and for the same reason.
     *
     * Worth the body here in particular: this endpoint reports a missing field and an unrecognised
     * `user_id` both as a 400, and only the body says which.
     */
    private suspend fun describeFailure(error: Throwable): String = when (error) {
        is ResponseException -> {
            val body = runCatching { error.response.bodyAsText() }.getOrDefault("")
            "HTTP ${error.response.status} body=${body.take(MaxLoggedBodyChars).ifBlank { "(empty)" }}"
        }
        else -> "${error::class.simpleName}: ${error.message ?: "(no message)"}"
    }

    /**
     * Everything the backend was last told, as one string.
     *
     * The whole body rather than the token alone: a user who moves country or changes language
     * needs the backend to know, and keying only on the token would leave it holding the region
     * this install had on the day it first registered.
     */
    private fun UpdatePushTokenRequest.signature(): String =
        "$userId|$deviceId|$token|$countryCode|$timezone|$language"

    private fun lastSent(): String? =
        runCatching { platformKeyValueStore.read(LastSentKey) }.getOrNull()

    private fun rememberSent(signature: String) {
        runCatching { platformKeyValueStore.write(LastSentKey, signature) }
    }

    private companion object {
        /**
         * The three verdicts, as they appear after `RESULT=`.
         *
         * Constants rather than literals at each site because they are what a reader greps for and
         * what the `push_token_synced` crash key is read as — a typo in one of them would be
         * invisible until someone went looking for a value that was never written.
         */
        const val Success = "SUCCESS"
        const val Failed = "FAILED"

        /** Nothing was sent because nothing had changed. The backend still holds the token. */
        const val Skipped = "SKIPPED"

        /** Three attempts, 500ms apart — [UserRepository]'s retry budget, for consistency. */
        const val MaxAttempts = 3
        const val RetryDelayMillis = 500L

        const val MaxLoggedBodyChars = 300

        /** Survives the process, which is the point: the skip has to work across launches. */
        const val LastSentKey = "push_token_last_sent"
    }
}
