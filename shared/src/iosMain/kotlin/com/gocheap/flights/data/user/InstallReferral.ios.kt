package com.gocheap.flights.data.user

import com.gocheap.flights.utils.logDebug

/**
 * Always unattributed on iOS.
 *
 * There is no counterpart to Android's Install Referrer API: the App Store reports campaign
 * attribution through AdServices / `AAAttribution`, which needs an attribution token exchanged with
 * Apple's own endpoint and a backend that understands the response. That is a separate integration
 * from this one, and the reference iOS app has not wired it up either — it sends the same
 * hardcoded "unknown" with no referrer.
 *
 * Deliberately not `Constants.Defaults.Route`: "organic" claims the install came from a store
 * listing, which is a thing this app does not know. See [UnattributedRoute].
 *
 * Logged despite being a constant, so the iOS console shows the same ordered sequence Android's
 * does — an `acquired_route=unknown` that was never looked up reads as a bug otherwise.
 */
internal actual suspend fun resolveInstallReferral(): InstallReferral {
    logDebug(
        AddUserLogTag,
        "Install referrer: not available on iOS — acquired_route=$UnattributedRoute",
    )
    return InstallReferral(route = UnattributedRoute, referrerUrl = "")
}
