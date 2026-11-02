package com.gocheap.travel.data.settings

import com.gocheap.travel.utils.logDebug
import platform.Foundation.NSUserDefaults

/**
 * iOS has no per-app language switch to call, so this writes the one key the system reads.
 *
 * `AppleLanguages` is what backs `NSLocale.preferredLanguages`, and that is precisely what Compose
 * Resources reads on this platform to pick a locale — see `getSystemEnvironment` in the library.
 * Putting the chosen code at the front of that list is therefore the whole mechanism.
 *
 * **It takes effect on the next launch, not immediately.** `NSLocale` resolves the preferred
 * languages once per process, so the screens already on display keep the language they were built
 * with. Android recreates its activity and changes over at once; iOS cannot be made to without
 * tearing down and rebuilding the root view controller, which would lose the back stack and every
 * search in it. The honest trade is to write the choice now and let it apply on the next start.
 */
internal actual fun applyAppLanguage(code: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    val current = (defaults.objectForKey(AppleLanguagesKey) as? List<*>)?.firstOrNull() as? String
    if (current == code) {
        logDebug(AppLanguageLogTag, "already on $code")
        return
    }
    logDebug(AppLanguageLogTag, "switching from '${current ?: "system"}' to '$code' (applies next launch)")
    defaults.setObject(listOf(code), forKey = AppleLanguagesKey)
}

/** The key iOS resolves `NSLocale.preferredLanguages` from. */
private const val AppleLanguagesKey = "AppleLanguages"
