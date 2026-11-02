package com.gocheap.travel.data.user

import com.gocheap.travel.utils.Constants
import kotlin.random.Random
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.uname
import platform.posix.utsname
import platform.AdSupport.ASIdentifierManager
import platform.AppTrackingTransparency.ATTrackingManager
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusAuthorized
import platform.Foundation.NSBundle
import platform.Foundation.NSUUID
import platform.UIKit.UIDevice

/**
 * The advertising identifier iOS hands back whenever tracking has not been authorised. As on
 * Android it is a well-formed UUID rather than an absent value, so it has to be recognised by hand.
 */
private const val ZeroedAdvertisingId = "00000000-0000-0000-0000-000000000000"

/**
 * Where the resolved device id is cached. The name matches the reference iOS app's
 * `flight70_device_id` in shape but not in value — these are per-app, and sharing one across two
 * apps from the same publisher would have them register as the same device.
 */
private const val DeviceIdKeychainAccount = "flighttp_device_id"

/** `pseudo_id` is 16 alphanumerics in the reference app; it is a nonce, not an identifier. */
private const val PseudoIdLength = 16
private const val PseudoIdAlphabet =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

internal actual val platformAppLabel: String get() = Constants.App.Ios.Label

internal actual val platformAppCode: String get() = Constants.App.Ios.Code

internal actual val platformAnonymousUserId: String get() = Constants.App.Ios.AnonymousUserId

/**
 * Keychain, then IDFA, then IDFV, then a fresh UUID — the reference iOS app's four-step chain,
 * first match wins, and the result is written back to the keychain so later launches short-circuit
 * at step one.
 *
 * The keychain comes first for a reason worth stating: it survives app deletion, so a reinstall
 * re-registers under the id the device already had rather than creating a second user. That is also
 * why it is the keychain and not `NSUserDefaults`, which is deleted with the app — see [IosKeychain].
 *
 * **It never prompts for tracking.** Reading the IDFA requires App Tracking Transparency
 * authorisation; *requesting* it means an `NSUserTrackingUsageDescription` in `Info.plist`, a system
 * alert on first launch and a matching App Store privacy declaration, none of which this app has.
 * So the status is only read: if this app later asks for tracking and is granted it, step two starts
 * resolving on its own — though by then the keychain already holds an id, so in practice the IDFA
 * only ever wins on a first launch that already had permission.
 */
internal actual suspend fun currentDeviceIdentity(): DeviceIdentity {
    // Sent on every request regardless of what the device id resolved to. It is what lets the
    // backend re-link an install whose device id is an IDFA back to the device behind it.
    val vendorId = UIDevice.currentDevice.identifierForVendor?.UUIDString.orEmpty()
    val pseudoId = randomPseudoId()

    IosKeychain.read(DeviceIdKeychainAccount)?.takeIf(String::isNotBlank)?.let { cached ->
        // The type is not cached alongside it: a cached id is reported as `uuid` whatever it was
        // first resolved from, which is what the reference app does and what the backend expects —
        // see [DeviceIdType] for why `idfa` is the only other value it accepts.
        return DeviceIdentity(cached, DeviceIdType.Uuid, vendorId, pseudoId)
    }

    val advertisingId = authorisedAdvertisingId()
    if (advertisingId != null) {
        return remember(advertisingId, DeviceIdType.Idfa, vendorId, pseudoId)
    }

    if (vendorId.isNotBlank()) {
        // Reported as `uuid`, not `idfv`: the backend rejects `idfv` outright. Both are UUID-shaped,
        // so nothing is lost by it — [DeviceIdType] has the detail.
        return remember(vendorId, DeviceIdType.Uuid, vendorId, pseudoId)
    }

    // IDFV is null for a short window before the device's first unlock, which is the only way to
    // reach here. A generated UUID keeps that launch from going unregistered.
    return remember(NSUUID().UUIDString, DeviceIdType.Uuid, vendorId, pseudoId)
}

/**
 * `FlightTp/1.0 CFNetwork/1498.700.2 Darwin/23.6.0` — the app, then the HTTP stack carrying it.
 *
 * **The system tokens are load-bearing, not decoration.** The flight host validates this header
 * and rejects a product token on its own: `FlightTp-iOS/1.0`, `FlightTp/1.0`, `FlightTp/1.0
 * Darwin/23.6.0` and `FlightTp/1.0 (iPhone; iOS 17.5)` are all answered with
 * `400 {"error":"Invalid 'User-Agent' header"}` before the request is looked at. Adding
 * `CFNetwork/<version>` is what makes it pass — verified against the backend, one token at a time.
 *
 * Which is really a lesson about *overriding* the header at all. `NSURLSession` appends exactly
 * these tokens by itself; setting a User-Agent replaced the whole string and threw them away, so
 * every iOS flight call failed the check for want of a part the platform would have supplied.
 *
 * Both versions are read rather than hardcoded — the CFNetwork bundle trick is the one Alamofire
 * uses, and the Darwin release comes from `uname`. If the CFNetwork bundle cannot be read this
 * returns blank, and the clients then leave the header alone so `NSURLSession` can supply its own,
 * which is valid by construction. A made-up version number would be worse than no header at all.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual val platformUserAgent: String
    get() {
        val appVersion =
            NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") ?: "1.0"

        val cfNetworkVersion = NSBundle.bundleWithIdentifier("com.apple.CFNetwork")
            ?.objectForInfoDictionaryKey("CFBundleShortVersionString")
            ?.toString()
            ?.takeIf(String::isNotBlank)
            // Without this token the flight host rejects the request outright, and inventing a
            // version would be a lie the backend might later match on. Hand back nothing instead.
            ?: return ""

        val darwinRelease = memScoped {
            val info = alloc<utsname>()
            if (uname(info.ptr) != 0) return@memScoped null
            info.release.toKString().takeIf(String::isNotBlank)
        }

        return buildString {
            append(Constants.App.ApplicationName).append('/').append(appVersion)
            append(" CFNetwork/").append(cfNetworkVersion)
            darwinRelease?.let { append(" Darwin/").append(it) }
        }
    }

internal actual fun randomUuid(): String = NSUUID().UUIDString

/** Caches the resolved id before handing it back, so the chain only ever runs once per install. */
private fun remember(
    deviceId: String,
    deviceIdType: String,
    vendorId: String,
    pseudoId: String,
): DeviceIdentity {
    IosKeychain.write(DeviceIdKeychainAccount, deviceId)
    return DeviceIdentity(deviceId, deviceIdType, vendorId, pseudoId)
}

/** Null unless tracking is already authorised and the identifier is a real one. */
private fun authorisedAdvertisingId(): String? {
    if (ATTrackingManager.trackingAuthorizationStatus !=
        ATTrackingManagerAuthorizationStatusAuthorized
    ) {
        return null
    }
    val identifier = ASIdentifierManager.sharedManager().advertisingIdentifier.UUIDString
    return identifier.takeIf { it.isNotBlank() && it != ZeroedAdvertisingId }
}

/**
 * A fresh 16-character nonce per call, never persisted — the reference app regenerates it the same
 * way. It is not an identifier despite the name, so there is nothing here to keep.
 */
private fun randomPseudoId(): String = buildString(PseudoIdLength) {
    repeat(PseudoIdLength) {
        append(PseudoIdAlphabet[Random.nextInt(PseudoIdAlphabet.length)])
    }
}
