package com.gocheap.travel.data.settings

import com.gocheap.travel.utils.Constants
import com.gocheap.travel.utils.logDebug

/** The tag region detection logs under: `adb logcat -s InitRegion`, as the reference app's does. */
internal const val InitRegionLogTag = "InitRegion"

/**
 * What the device says about where it is and what it reads in.
 *
 * Every field is optional because every one of them genuinely can be absent: a tablet with no SIM
 * and no network registration knows no country, and a locale can name a country this app has no
 * currency for.
 */
internal data class RegionHints(
    val countryCode: String? = null,
    val languageCode: String? = null,
    val currencyCode: String? = null,
)

/** Asked once at launch — see [RegionDefaults]. The two platforms know different things. */
internal expect fun platformRegionHints(): RegionHints

/**
 * Picks the region a first launch opens on, and keeps the language following the device after that.
 *
 * The reference app's `setCountryAndLanguage`, which splits into two cases, and so does this:
 *
 * **Nothing chosen yet.** Take the device's country, find it in the bundled catalogue, and adopt
 * that row's currency. The language is the device's where the row's supplier localises into it,
 * and English where it does not — a Greek phone pointed at Japan gets Japanese prices in English,
 * because `el` is not a language the Japanese site is served in.
 *
 * **Something chosen already.** Only the language is revisited, and only to follow the device:
 * someone who switches their phone to French should get French results without going looking for
 * the setting. Their country and currency are left exactly as they set them.
 *
 * **None of this counts as choosing.** [AppSettingsState.isRegionChosen] stays false, which is
 * what keeps a guess out of the Travelpayouts SDK's own preferences — it geolocates for itself,
 * and overwriting that with an inference would be replacing one guess with a worse one. The flag
 * flips when a human taps a row on the Profile tab.
 */
/**
 * What detection concluded, with each field set only where it has something to say.
 *
 * Null throughout means "change nothing" — a device that named no country, or a chosen region
 * whose language already matches. The caller writes only the fields that are present, so the three
 * settings move independently.
 */
internal data class DetectedRegion(
    val countryCode: String? = null,
    val currencyCode: String? = null,
    val languageCode: String? = null,
)

internal object RegionDefaults {

    /**
     * Call after [Regions.load] — the catalogue is what a country code is resolved against, and
     * without it this would adopt a country with no currency behind it.
     */
    fun apply() {
        val hints = platformRegionHints()
        val settings = AppSettings.current
        logDebug(
            InitRegionLogTag,
            "device country=${hints.countryCode ?: "-"} language=${hints.languageCode ?: "-"} " +
                "currency=${hints.currencyCode ?: "-"} | stored country=${settings.countryCode} " +
                "chosen=${settings.isRegionChosen}",
        )

        val resolved = resolve(hints, settings)
        if (resolved == null) {
            logDebug(InitRegionLogTag, "nothing to apply — keeping the stored settings")
            return
        }
        AppSettings.applyDetectedRegion(
            countryCode = resolved.countryCode,
            currencyCode = resolved.currencyCode,
            languageCode = resolved.languageCode,
        )
        logDebug(InitRegionLogTag, "applied $resolved")
    }

    /**
     * The decision itself, apart from where the hints come from and where the result goes.
     *
     * Pure so it can be tested against the cases that actually occur — a tablet with no SIM, a
     * country the catalogue does not list, a language its supplier does not serve — none of which
     * are reachable through a device in a test.
     *
     * Returns null when nothing should change.
     */
    internal fun resolve(hints: RegionHints, settings: AppSettingsState): DetectedRegion? =
        if (settings.isRegionChosen) followDeviceLanguage(hints, settings) else adoptDeviceRegion(hints)

    /**
     * The first-launch case: a country, its currency, and a language the pair is actually served in.
     */
    private fun adoptDeviceRegion(hints: RegionHints): DetectedRegion? {
        val detected = hints.countryCode?.takeIf { it.length == 2 }?.uppercase() ?: return null
        val row = Regions.country(detected)

        if (row == null) {
            // A country the catalogue has no row for. Its code is still the right thing to send —
            // the supplier knows more countries than this file lists — but there is no currency to
            // adopt with it, so the device's own stands in where this app can offer it at all.
            return DetectedRegion(
                countryCode = detected,
                currencyCode = hints.currencyCode?.takeIf { Regions.currency(it) != null },
                languageCode = supportedLanguage(hints.languageCode, supportedByRow = emptyList()),
            )
        }

        return DetectedRegion(
            countryCode = row.code,
            currencyCode = row.currencyCode.takeIf { it.isNotBlank() },
            languageCode = supportedLanguage(hints.languageCode, row.supportedLanguages),
        )
    }

    /**
     * The later-launch case: the device's language wins if this app can offer it at all.
     *
     * Deliberately not conditioned on the chosen *country* supporting it, unlike the first-launch
     * case. Someone who set their region by hand and then switched their phone's language is
     * asking for that language, not to be told their country does not have it.
     */
    private fun followDeviceLanguage(hints: RegionHints, settings: AppSettingsState): DetectedRegion? {
        // A language the user chose outranks the device's. Following the phone here would undo
        // that pick on the first launch after they changed their system language — the setting
        // would appear to forget itself.
        if (settings.isLanguageChosen) return null
        val device = hints.languageCode?.let(Regions::language) ?: return null
        if (device.code == settings.languageCode) return null
        return DetectedRegion(languageCode = device.code)
    }

    /**
     * The device's language where the supplier serves this country in it, and English otherwise.
     *
     * [supportedByRow] empty means the catalogue said nothing — most rows do — and then any
     * language this app knows is allowed rather than none, since there is nothing to contradict it.
     */
    private fun supportedLanguage(deviceLanguage: String?, supportedByRow: List<String>): String {
        val known = deviceLanguage?.let(Regions::language) ?: return Constants.Defaults.Language
        if (supportedByRow.isEmpty()) return known.code
        // The catalogue speaks Java's legacy codes — "in", "iw" — where this app stores the modern
        // ones, so a row is matched on either spelling rather than on the stored code alone.
        val accepted = supportedByRow.any { it.equals(known.code, true) || it.equals(known.legacyCode, true) }
        return if (accepted) known.code else Constants.Defaults.Language
    }
}

/**
 * The code the bundled catalogue would spell this language with.
 *
 * Java's legacy forms for Indonesian and Hebrew, which the file still uses — see [Regions.languages].
 */
private val AppLanguage.legacyCode: String
    get() = when (code) {
        "id" -> "in"
        "he" -> "iw"
        else -> code
    }
