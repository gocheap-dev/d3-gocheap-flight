package com.gocheap.travel.data.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * One [T] that survives the process, stored as JSON under [key] — the single-record counterpart to
 * [PersistedList], and tolerant of the same schema drift for the same reason.
 */
internal class PersistedValue<T>(
    private val key: String,
    private val serializer: KSerializer<T>,
    private val storage: KeyValueStore = platformKeyValueStore,
) {
    /** [fallback] covers both "nothing saved yet" and "what was saved no longer parses". */
    fun load(fallback: T): T {
        val stored = storage.read(key) ?: return fallback
        return try {
            json.decodeFromString(serializer, stored)
        } catch (error: Exception) {
            fallback
        }
    }

    fun save(value: T) {
        try {
            storage.write(key, json.encodeToString(serializer, value))
        } catch (error: Exception) {
            // A setting that fails to persist must not take down the screen that changed it; it
            // simply reverts on the next launch.
        }
    }

    private companion object {
        val json = Json {
            // A record written by an older build should still load after the model gains a field.
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
