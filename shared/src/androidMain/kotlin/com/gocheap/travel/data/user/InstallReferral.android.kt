package com.gocheap.travel.data.user

import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.gocheap.travel.data.storage.AndroidAppContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Asks the Play Store what brought this install, over the Install Referrer API.
 *
 * Every path answers with something: the API is unavailable on devices without a current Play
 * Store, the service can be down, and a sideloaded build has no referrer at all — all of which the
 * reference app treats as organic, because none of them are a reason to hold up a sign-up.
 *
 * The connection is closed as soon as an answer arrives. It is a bound service, and this is a
 * once-per-install read, so leaving it open would hold a binding for the life of the process.
 */
internal actual suspend fun resolveInstallReferral(): InstallReferral {
    val context = AndroidAppContext.requireOrNull() ?: return InstallReferral()

    return suspendCancellableCoroutine { continuation ->
        val client = runCatching { InstallReferrerClient.newBuilder(context).build() }
            .getOrElse {
                Log.d(AddUserLogTag, "Install Referrer unavailable: ${it.message}")
                continuation.resume(InstallReferral())
                return@suspendCancellableCoroutine
            }

        // The listener is documented to be able to fire more than once — a setup callback followed
        // by a disconnect — and resuming a continuation twice is a crash, so the first answer wins.
        val answered = AtomicBoolean(false)
        fun answer(referral: InstallReferral) {
            if (!answered.compareAndSet(false, true)) return
            runCatching { client.endConnection() }
            if (continuation.isActive) continuation.resume(referral)
        }

        continuation.invokeOnCancellation { runCatching { client.endConnection() } }

        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    if (responseCode != InstallReferrerClient.InstallReferrerResponse.OK) {
                        Log.d(AddUserLogTag, "Install Referrer declined with code $responseCode")
                        answer(InstallReferral())
                        return
                    }
                    val referrerUrl = runCatching { client.installReferrer.installReferrer }
                        .getOrNull()
                        .orEmpty()
                    Log.d(AddUserLogTag, "Install referrer: $referrerUrl")
                    answer(
                        if (referrerUrl.isBlank()) {
                            InstallReferral()
                        } else {
                            installReferralFrom(referrerUrl)
                        },
                    )
                }

                override fun onInstallReferrerServiceDisconnected() {
                    // Only meaningful before the setup callback; after it the answer is already in.
                    answer(InstallReferral())
                }
            })
        } catch (error: Throwable) {
            Log.d(AddUserLogTag, "Install Referrer connection failed: ${error.message}")
            answer(InstallReferral())
        }
    }
}
