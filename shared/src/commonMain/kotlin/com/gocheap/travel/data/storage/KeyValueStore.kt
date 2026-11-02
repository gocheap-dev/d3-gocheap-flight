package com.gocheap.travel.data.storage

/**
 * The smallest thing that can outlive the process: string in, string out, keyed by name.
 *
 * Backed by SharedPreferences on Android and NSUserDefaults on iOS. Both are the right size for
 * what this app persists — a handful of short JSON lists — and neither needs a dependency, a schema
 * or a migration story. Reach for DataStore if something here ever grows into real records.
 *
 * An interface rather than an `expect object` so a test can hand [PersistedList] somewhere to write
 * that is not the device.
 */
internal interface KeyValueStore {
    fun read(key: String): String?

    fun write(key: String, value: String)
}

/** Whichever one of the two this platform has. */
internal expect val platformKeyValueStore: KeyValueStore
