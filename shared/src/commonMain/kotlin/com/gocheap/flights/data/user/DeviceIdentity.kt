package com.gocheap.flights.data.user

import com.gocheap.flights.data.storage.platformKeyValueStore

/**
 * Who the backend is told this install is.
 *
 * [deviceId] and [deviceIdType] travel together and must agree: the backend dedupes installs on the
 * pair, so a `gps_adid` value labelled `uuid` registers a second user for a device that already has
 * one. Which of the two is produced is the platform's business — see the actuals.
 */
internal data class DeviceIdentity(
    val deviceId: String,
    val deviceIdType: String,
    /** Scoped to this publisher on both platforms: App Set ID on Android, IDFV on iOS. */
    val vendorId: String?,
    /** The analytics instance id, where the platform has one to give. */
    val pseudoId: String?,
)

/** The identifier types the backend recognises, kept here so the actuals cannot drift apart. */
internal object DeviceIdType {
    /** Android's advertising id, the only resettable cross-app identifier Play still offers. */
    const val GpsAdId = "gps_adid"

    /** Firebase Installations, which outlives a cleared advertising id but not an uninstall. */
    const val CustomId = "cst_id"

    /** iOS's advertising identifier — only ever non-zero once tracking has been authorised. */
    const val Idfa = "idfa"

    /**
     * Everything on iOS that is not an IDFA, and the last resort on Android.
     *
     * There is deliberately no `idfv`. The backend rejects that value, so an id resolved from the
     * vendor identifier is reported as a UUID instead — which costs nothing, because an IDFV and a
     * generated UUID are both plain UUIDs and the backend could not tell them apart regardless.
     */
    const val Uuid = "uuid"
}

/**
 * Resolves the identifiers for this install, in the platform's own order of preference.
 *
 * Suspending because both platforms answer asynchronously — a Play Services `Task` on Android, and
 * nothing on iOS that should be read on the main thread during launch.
 */
internal expect suspend fun currentDeviceIdentity(): DeviceIdentity

/**
 * The per-app, per-platform code `users/add` is keyed on. `Constants.App.Android.Label` on Android,
 * `APP_LABEL_IOS` on iOS — sending the wrong one is rejected outright with "Invalid app".
 */
internal expect val platformAppLabel: String

/**
 * The supplier-facing app code: `Constants.App.Android.Code` on Android, `Constants.App.Ios.Code` on iOS.
 *
 * Separate from [platformAppLabel] because the two name this app to two different backends — see
 * `Constants.App.Android.Code` for which is which, and why sending one in place of the other fails.
 */
internal expect val platformAppCode: String

/**
 * The user id the deeplinks carry until `users/add` has produced a real one — and the only one they
 * ever carry if it never does, which is why it is platform-specific rather than a shared sentinel:
 * it is [platformAppCode] in the form the supplier expects a user id in.
 */
internal expect val platformAnonymousUserId: String

/** `AppName/version (platform details)`, which the backend logs against the install. */
/**
 * What this build calls itself on the wire.
 *
 * **Blank means "do not set the header".** The backend validates this value and rejects one that
 * names only the app — it wants the HTTP stack's own tokens alongside it — so a platform that
 * cannot build a complete string hands back nothing, and the client leaves the header off for the
 * engine to fill in. See the iOS actual, where that is a real case.
 */
internal expect val platformUserAgent: String

/** `java.util.UUID` on Android, `NSUUID` on iOS — neither is reachable from common code. */
internal expect fun randomUuid(): String

/**
 * A UUID generated once on first launch and kept for the life of the install.
 *
 * The floor under [currentDeviceIdentity] on both platforms: an install with tracking refused, no
 * Play Services and no Firebase still has to register as *something*, and something stable — a
 * fresh UUID per launch would register a new user every time the app started.
 *
 * Read straight off [platformKeyValueStore] for the same reason as
 * [com.gocheap.flights.data.onboarding.OnboardingStore]: it is one short string with no schema and
 * no "unreadable" case, so a serializer would be ceremony around a `read`/`write` pair.
 */
internal object InstallUuid {

    private const val Key = "install_uuid"

    fun get(): String {
        platformKeyValueStore.read(Key)?.takeIf(String::isNotBlank)?.let { return it }
        val fresh = randomUuid()
        platformKeyValueStore.write(Key, fresh)
        return fresh
    }
}
