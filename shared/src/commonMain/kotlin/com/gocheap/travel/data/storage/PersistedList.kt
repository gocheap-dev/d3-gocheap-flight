package com.gocheap.travel.data.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A list of [T] that survives the process, stored as one JSON string under [key].
 *
 * Deliberately not a Flow: the recent stores already hold the live list in a StateFlow, and this is
 * only the layer underneath them — read once at startup, overwritten whenever that list changes.
 */
internal class PersistedList<T>(
    private val key: String,
    itemSerializer: KSerializer<T>,
    private val storage: KeyValueStore = platformKeyValueStore,
) {
    private val serializer = ListSerializer(itemSerializer)

    /**
     * Anything unreadable is treated as "nothing saved yet".
     *
     * Recents are a convenience, and the shapes stored here change whenever a model gains a field.
     * Losing a history list on upgrade is a shrug; crashing on launch because last version's JSON
     * no longer parses is not.
     */
    fun load(): List<T> {
        val stored = storage.read(key) ?: return emptyList()
        return try {
            json.decodeFromString(serializer, stored)
        } catch (error: Exception) {
            emptyList()
        }
    }

    fun save(value: List<T>) {
        try {
            storage.write(key, json.encodeToString(serializer, value))
        } catch (error: Exception) {
            // Failing to record a recent must never take down the search that triggered it.
        }
    }

    private companion object {
        val json = Json {
            // A list written by an older build should still load after a model gains a field.
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
