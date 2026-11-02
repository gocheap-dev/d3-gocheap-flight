package com.gocheap.travel.data.settings

import android.content.Context
import android.telephony.TelephonyManager
import com.gocheap.travel.data.storage.AndroidAppContext
import java.util.Currency
import java.util.Locale

/**
 * Where the device thinks it is, asked the way the reference app asks.
 *
 * **The SIM first, the serving network second, the locale last.** That order is the point of using
 * telephony at all: the SIM says which country the account belongs to, the network says which
 * country the handset is standing in, and the locale only says which country the owner configured
 * the phone for. A British phone roaming in Spain should be priced in Spain, and only the middle
 * one of those knows that.
 *
 * Neither telephony read needs a permission.
 *
 * **CDMA handsets skip the network read**, as the reference app skips them: on CDMA that value is
 * not derived from a serving network the way GSM's is, and is unreliable. The SIM read still applies.
 *
 * The locale fallback is this app's own, where the reference app instead calls `api.ipify.org` and
 * then `ip-api.com` to geolocate the public IP. That is a round trip to two third parties — over
 * cleartext HTTP for the second — to answer a question the device can already answer offline. A
 * phone with no SIM and no registration still has a configured region.
 */
internal actual fun platformRegionHints(): RegionHints {
    val context = AndroidAppContext.requireOrNull()
    val locale = Locale.getDefault()

    return RegionHints(
        countryCode = context?.telephonyCountry() ?: locale.country.takeIf { it.length == 2 },
        languageCode = locale.language.takeIf(String::isNotBlank),
        currencyCode = locale.currencyCode(),
    )
}

/** The SIM's country, then the serving network's. Null when the device is telling us neither. */
private fun Context.telephonyCountry(): String? {
    val telephony = runCatching {
        getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }.getOrNull() ?: return null

    return runCatching {
        telephony.simCountryIso?.takeIf { it.length == 2 }
            ?: telephony.networkCountryIso
                ?.takeIf { it.length == 2 && telephony.phoneType != TelephonyManager.PHONE_TYPE_CDMA }
    }.getOrNull()
}

/**
 * The currency the configured locale implies.
 *
 * `Currency.getInstance` throws for a locale that names no country — "en" on its own does — so the
 * failure is ordinary rather than exceptional, and answers null for the caller to fall back from.
 */
private fun Locale.currencyCode(): String? =
    runCatching { Currency.getInstance(this)?.currencyCode }.getOrNull()
