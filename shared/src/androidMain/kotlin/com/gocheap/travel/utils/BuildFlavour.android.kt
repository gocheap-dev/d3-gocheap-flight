package com.gocheap.travel.utils

import android.content.pm.ApplicationInfo
import com.gocheap.travel.data.storage.AndroidAppContext

/**
 * Read off the installed package's own `debuggable` flag rather than off a `BuildConfig`.
 *
 * This is a library module: its `BuildConfig.DEBUG` tracks how the *library* was compiled, which
 * is not the same question as whether the app shipping it is debuggable. The manifest flag is.
 *
 * False when storage has not been attached yet — the quiet answer is the safe one, and the only
 * requests made before `attach` are made before anything is listening anyway.
 */
internal actual val isDebugBuild: Boolean
    get() {
        val context = AndroidAppContext.requireOrNull() ?: return false
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
