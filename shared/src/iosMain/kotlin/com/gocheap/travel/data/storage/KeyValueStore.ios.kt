package com.gocheap.travel.data.storage

import platform.Foundation.NSUserDefaults

internal actual val platformKeyValueStore: KeyValueStore get() = IosKeyValueStore

internal object IosKeyValueStore : KeyValueStore {
    private val defaults: NSUserDefaults get() = NSUserDefaults.standardUserDefaults

    override fun read(key: String): String? = defaults.stringForKey(key)

    override fun write(key: String, value: String) {
        // NSUserDefaults writes to memory and flushes on its own schedule, so there is no
        // synchronize() here — calling it is discouraged and would block the caller for nothing.
        defaults.setObject(value, forKey = key)
    }
}
