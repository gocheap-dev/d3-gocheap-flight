package com.gocheap.flights.data.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.gocheap.flights.utils.logDebug
import java.util.Locale

/**
 * Android's app language, applied without AppCompat.
 *
 * `AppCompatDelegate.setApplicationLocales` is the usual answer and is not available here: it only
 * does anything once an `AppCompatDelegate` exists, this app's activity is a plain
 * `ComponentActivity`, and its theme descends from `Theme.Material` rather than `Theme.AppCompat` —
 * so adopting AppCompat to get a locale switch would mean restyling the app and the Travelpayouts
 * SDK's screens with it. Calling it as-is fails silently, which is exactly what it did: the setting
 * saved, the system recorded no locale, and nothing changed on screen.
 *
 * What replaces it is the mechanism AppCompat wraps anyway — the activity's own configuration, set
 * in `attachBaseContext` and reapplied by recreating the activity. One path for every API level,
 * and nothing to keep in step with a backport.
 */
internal actual fun applyAppLanguage(code: String) {
    val previous = AppLocale.current
    AppLocale.current = code
    if (previous == code) {
        logDebug(AppLanguageLogTag, "already on $code — not recreating")
        return
    }
    logDebug(AppLanguageLogTag, "switching from '${previous ?: "system"}' to '$code'")
    // The activity rebuilds against the new configuration; Compose reads the locale from there.
    AppLocale.recreateHost?.invoke()
}

/**
 * The language the activity is built against, and the hook that rebuilds it.
 *
 * Held here rather than read back out of [AppSettings] because `attachBaseContext` runs before the
 * activity exists, which is earlier than anything that could collect a flow.
 *
 * Public, unlike the rest of this file: the host activity in the Android module is what calls
 * [wrap] and supplies [recreateHost], and that is a different module from this one.
 */
object AppLocale {

    var current: String? = null

    /** Set by the host activity; calling it recreates that activity. */
    var recreateHost: (() -> Unit)? = null

    /**
     * The base context with the chosen language applied.
     *
     * Called from `Activity.attachBaseContext`, which is the only point early enough that the
     * activity's resources — and so everything Compose reads a locale from — are built against it.
     */
    fun wrap(base: Context): Context {
        val code = current ?: return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLayoutDirection(locale)
            }
        }
        return base.createConfigurationContext(configuration)
    }
}
