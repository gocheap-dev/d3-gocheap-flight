package com.gocheap.flights.data.onboarding

import com.gocheap.flights.data.storage.platformKeyValueStore

/**
 * Whether the intro pages have been seen, so they are shown on a first launch and never again.
 *
 * One key rather than a [com.gocheap.flights.data.storage.PersistedList]: that type exists to put
 * JSON lists on disk, and a flag needs no serializer, no schema and no "unreadable" case — anything
 * other than the written marker means not yet.
 *
 * Reached straight off [platformKeyValueStore] because both platforms write without blocking —
 * `apply()` on Android, in-memory on iOS — so recording this from the tap that leaves the last page
 * costs the frame nothing.
 */
internal object OnboardingStore {

    private const val Key = "onboarding_completed"
    private const val Completed = "true"

    fun hasCompleted(): Boolean = platformKeyValueStore.read(Key) == Completed

    /** Called when the user leaves the pages, by finishing them or by skipping — either counts. */
    fun markCompleted() {
        platformKeyValueStore.write(Key, Completed)
    }
}
