package com.gocheap.travel.utils

import android.util.Log

internal actual fun logDebug(tag: String, message: String) {
    Log.d(tag, message)
}

internal actual fun logWarning(tag: String, message: String, error: Throwable?) {
    if (error == null) Log.w(tag, message) else Log.w(tag, message, error)
}
