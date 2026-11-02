package com.gocheap.travel.data.settings

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.currentLocale

/**
 * Where the device thinks it is, from the device itself.
 *
 * iOS has no equivalent of Android's SIM and serving-network country: `CTCarrier.isoCountryCode`
 * was deprecated in iOS 16 and has returned a fixed placeholder since, so there is nothing to read
 * even for an app that linked CoreTelephony. What is left — and what Apple intends to be read — is
 * the region the owner has set, which is also what the App Store and the system price in.
 *
 * All three values come off the one locale, so they agree with each other by construction: a phone
 * set to Japan reports JP, ja and JPY together, rather than a country from one source and a
 * currency from another.
 */
internal actual fun platformRegionHints(): RegionHints {
    val locale = NSLocale.currentLocale

    return RegionHints(
        countryCode = locale.valueFor(NSLocaleCountryCode)?.takeIf { it.length == 2 },
        languageCode = locale.valueFor(NSLocaleLanguageCode),
        currencyCode = locale.valueFor(NSLocaleCurrencyCode),
    )
}

/**
 * One locale component as a non-blank string.
 *
 * `objectForKey` is typed `Any?` through the Objective-C bridge and is genuinely absent for some
 * components — a locale with no region has no country code — so each read is narrowed here rather
 * than at three call sites.
 */
private fun NSLocale.valueFor(key: String?): String? {
    // The component keys themselves come through the bridge as nullable, so the absent-key case
    // has to be handled before the absent-value one.
    val componentKey = key ?: return null
    return (objectForKey(componentKey) as? String)?.takeIf(String::isNotBlank)
}
