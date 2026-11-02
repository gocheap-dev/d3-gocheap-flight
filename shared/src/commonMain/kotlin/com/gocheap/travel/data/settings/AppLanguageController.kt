package com.gocheap.travel.data.settings

import com.gocheap.travel.utils.logDebug

/** The tag the language switch logs under: `adb logcat -s AppLanguage`. */
internal const val AppLanguageLogTag = "AppLanguage"

/**
 * Points the platform at the language the user chose, so the app's own words change with it.
 *
 * The setting used to travel no further than the supplier links — `language=fr` on a deeplink,
 * while every button in the app stayed English. This is the other half: the same code, applied to
 * the platform, so `Res.string.*` resolves against it.
 *
 * It has to go through the platform rather than through a value this app holds. Compose Resources
 * picks its locale from `Locale.current`, and the only supported way to move that is to change
 * what the system reports — the library's own override is `internal`, and `ResourceEnvironment`
 * cannot be constructed from outside it.
 *
 * Each platform persists the choice itself — Android in its per-app locale store, iOS in
 * `AppleLanguages` — so this is called again at launch only to keep that store in step with the
 * app's own setting, not to restore it.
 */
internal expect fun applyAppLanguage(code: String)

/**
 * Applies the stored language, if the platform is not already on it.
 *
 * Called at launch and whenever the setting changes. Guarded rather than applied blindly because
 * on Android applying it recreates the activity: doing that on every launch would cost a visible
 * restart of the first screen for no change at all.
 */
internal object AppLanguageController {

    fun sync() {
        val chosen = AppSettings.current.languageCode
        if (chosen.isBlank()) return
        logDebug(AppLanguageLogTag, "applying stored language: $chosen")
        applyAppLanguage(chosen)
    }
}
