package com.gocheap.flights.data.user

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.gocheap.flights.data.storage.AndroidAppContext
import com.gocheap.flights.utils.Constants
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.tasks.Task
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

/**
 * The advertising id Play hands back once the user has opted out of ads personalisation. It is a
 * real, well-formed UUID rather than an absent value, so it has to be checked for by hand — sending
 * it would register every opted-out device on this app as the same user.
 */
private const val ZeroedAdvertisingId = "00000000-0000-0000-0000-000000000000"

internal actual val platformAppLabel: String get() = Constants.App.Android.Label

internal actual val platformAppCode: String get() = Constants.App.Android.Code

internal actual val platformAnonymousUserId: String get() = Constants.App.Android.AnonymousUserId

/**
 * Advertising id, then the Firebase installation, then a UUID of this app's own — the same order,
 * and the same three `device_id_type` values, as the reference app.
 *
 * Each step is guarded on its own: Play Services can be missing from the device, Firebase can be
 * unconfigured in this build, and neither is a reason to fail a sign-up that the next step down can
 * still complete. The vendor and pseudo ids are best-effort for the same reason and go out absent
 * rather than empty when they cannot be read.
 *
 * Unlike the reference app this does not *wait* for the App Set and analytics ids to arrive: it
 * collects them beside the device id and sends what it has. Waiting there means a build without
 * Firebase never reaches `users/add` at all, because the value it is waiting on is never written.
 */
internal actual suspend fun currentDeviceIdentity(): DeviceIdentity {
    val context = AndroidAppContext.requireOrNull()
    val vendorId = context?.let { appSetId(it) }
    val pseudoId = context?.let { analyticsInstanceId(it) }

    val advertisingId = context?.let { advertisingId(it) }
    if (advertisingId != null) {
        return DeviceIdentity(advertisingId, DeviceIdType.GpsAdId, vendorId, pseudoId)
    }

    val installationId = firebaseInstallationId()
    if (installationId != null) {
        return DeviceIdentity(installationId, DeviceIdType.CustomId, vendorId, pseudoId)
    }

    return DeviceIdentity(InstallUuid.get(), DeviceIdType.Uuid, vendorId, pseudoId)
}

/**
 * `FlightTp/1.0 Dalvik/2.1.0 (Linux; U; Android 14; Pixel 7 Build/...)`.
 *
 * Non-ASCII is replaced rather than dropped: a device whose model name carries an accent would
 * otherwise produce a header OkHttp rejects outright, taking down a call that has nothing to do
 * with what the device is called.
 */
internal actual val platformUserAgent: String
    get() {
        val version = AndroidAppContext.requireOrNull()?.let(::versionName).orEmpty()
        val system = runCatching { System.getProperty("http.agent") }.getOrNull().orEmpty()
        return "${Constants.App.ApplicationName}/$version $system"
            .map { character -> if (character.code in 0x20..0x7E) character else '?' }
            .joinToString(separator = "")
    }

internal actual fun randomUuid(): String = UUID.randomUUID().toString()

/** Null when Play Services is absent, the lookup fails, or the user has opted out. */
private suspend fun advertisingId(context: Context): String? = try {
    // Documented as blocking and as throwing if called from the main thread.
    val info = withContext(Dispatchers.IO) { AdvertisingIdClient.getAdvertisingIdInfo(context) }
    info.id?.takeIf { it.isNotBlank() && it != ZeroedAdvertisingId }
} catch (error: Throwable) {
    Log.d(AddUserLogTag, "No advertising id on this device: ${error.message}")
    null
}

private suspend fun appSetId(context: Context): String? = try {
    AppSet.getClient(context).appSetIdInfo.awaitOrNull()?.id?.takeIf(String::isNotBlank)
} catch (error: Throwable) {
    Log.d(AddUserLogTag, "No App Set id on this device: ${error.message}")
    null
}

/** Null on a build with no Firebase configured, which `FlightTpApplication` tolerates already. */
private suspend fun firebaseInstallationId(): String? = try {
    FirebaseInstallations.getInstance().id.awaitOrNull()?.takeIf(String::isNotBlank)
} catch (error: Throwable) {
    Log.d(AddUserLogTag, "No Firebase installation id: ${error.message}")
    null
}

private suspend fun analyticsInstanceId(context: Context): String? = try {
    FirebaseAnalytics.getInstance(context).appInstanceId.awaitOrNull()?.takeIf(String::isNotBlank)
} catch (error: Throwable) {
    Log.d(AddUserLogTag, "No analytics instance id: ${error.message}")
    null
}

private fun versionName(context: Context): String = try {
    val packages = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packages.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packages.getPackageInfo(context.packageName, 0)
    }
    info.versionName.orEmpty()
} catch (error: Throwable) {
    ""
}

/**
 * Bridges a Play Services [Task] to a coroutine, answering null rather than throwing on failure.
 *
 * Hand-rolled instead of `kotlinx-coroutines-play-services`'s `await()`: every caller here wants
 * "null if it did not work", so that dependency would buy a throwing API this file then has to
 * catch at all four call sites.
 */
private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        continuation.resume(if (task.isSuccessful) task.result else null)
    }
}
