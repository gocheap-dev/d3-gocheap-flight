package com.gocheap.flights.data.storage

import com.gocheap.flights.data.model.hotel.HotelDestination
import com.gocheap.flights.data.model.hotel.HotelDestinationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistedListTest {

    /** Stands in for SharedPreferences / NSUserDefaults, and can be primed with junk. */
    private class FakeStore(private val values: MutableMap<String, String> = mutableMapOf()) :
        KeyValueStore {
        var writes = 0
            private set

        override fun read(key: String): String? = values[key]

        override fun write(key: String, value: String) {
            values[key] = value
            writes++
        }
    }

    private fun destination(id: String, name: String) = HotelDestination(
        id = id,
        name = name,
        location = "California, United States",
        type = HotelDestinationType.City,
        parentId = null,
        supplierId = id,
    )

    private fun listFor(store: KeyValueStore) =
        PersistedList("recents", HotelDestination.serializer(), store)

    @Test
    fun a_saved_list_comes_back_whole() {
        val store = FakeStore()
        val saved = listOf(destination("1", "San Francisco"), destination("2", "San Diego"))

        listFor(store).save(saved)

        // A separate instance, the way the next launch would read it.
        assertEquals(saved, listFor(store).load())
    }

    @Test
    fun nothing_saved_yet_reads_as_an_empty_list() {
        assertEquals(emptyList(), listFor(FakeStore()).load())
    }

    @Test
    fun junk_left_by_an_older_build_does_not_throw() {
        // Each of these is something a previous version could plausibly have written: a different
        // shape, a truncated write, a bare value where a list belongs.
        listOf(
            """[{"id":"1"}]""",
            """[{"id":"1","name":"San Francisco",""",
            """{"id":"1","name":"San Francisco"}""",
            "",
            "null",
        ).forEach { junk ->
            val store = FakeStore(mutableMapOf("recents" to junk))
            assertEquals(emptyList(), listFor(store).load(), "expected empty for: $junk")
        }
    }

    @Test
    fun clearing_is_written_through_rather_than_only_dropped_in_memory() {
        val store = FakeStore()
        val list = listFor(store)
        list.save(listOf(destination("1", "San Francisco")))

        list.save(emptyList())

        assertEquals(2, store.writes)
        assertTrue(listFor(store).load().isEmpty())
    }
}
