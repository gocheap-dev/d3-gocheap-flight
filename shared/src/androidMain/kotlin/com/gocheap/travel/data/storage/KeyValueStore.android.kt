package com.gocheap.travel.data.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Hands the shared module an Android Context to persist against. Call it once, from the Application
 * or the launcher Activity, before any screen is composed.
 *
 * iOS needs no equivalent — NSUserDefaults is reachable without one — so this is the one place the
 * two platforms are wired differently.
 */
fun initFlightStorage(context: Context) {
    val application = context.applicationContext
    AndroidKeyValueStore.attach(application)
    // Same call site, same lifetime: the identifiers `users/add` is built from are read off Play
    // Services and Firebase, all of which want a Context, and none of which the shared module can
    // be handed one of any other way.
    AndroidAppContext.attach(application)
}

/**
 * The application Context, for the parts of the shared module that need one and are not storage.
 *
 * Separate from [AndroidKeyValueStore] because they fail differently: a missing store is a
 * programming error worth a crash, whereas a missing Context only means this launch registers the
 * install under a generated UUID instead of its advertising id — degraded, not broken.
 */
internal object AndroidAppContext {

    private var application: Context? = null

    fun attach(context: Context) {
        if (application != null) return
        application = context
    }

    fun requireOrNull(): Context? = application
}

// Resolved on each access rather than eagerly, so merely loading this file cannot outrun
// initFlightStorage.
internal actual val platformKeyValueStore: KeyValueStore get() = AndroidKeyValueStore

internal object AndroidKeyValueStore : KeyValueStore {
    private const val FileName = "flighttp_prefs"

    private var preferences: SharedPreferences? = null

    fun attach(context: Context) {
        if (preferences != null) return
        preferences = context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
    }

    override fun read(key: String): String? = require().getString(key, null)

    override fun write(key: String, value: String) {
        // apply(), not commit(): the caller is on whatever thread recorded the search, and nothing
        // reads this back until the next launch.
        require().edit().putString(key, value).apply()
    }

    /**
     * Loud rather than silent: storage is touched lazily, so a missing [initFlightStorage] would
     * otherwise surface as recents that never save, far from the line that caused it.
     */
    private fun require(): SharedPreferences = checkNotNull(preferences) {
        "Call initFlightStorage(context) from your Application or Activity before using the app."
    }
}
