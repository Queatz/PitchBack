package app.pitchback.db

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ArangoSessionStorageTest {
    @Test
    fun writeReadAndInvalidate() {
        runBlocking {
            val docs = MemorySessionDocuments()
            val storage = ArangoSessionStorage(docs, clock = { 1_700_000_000_000L })
            storage.write("abc", """{"userId":"u1"}""")
            assertEquals("""{"userId":"u1"}""", storage.read("abc"))
            assertEquals(1_700_000_000_000L / 1000 + SESSION_MAX_AGE_SECONDS, docs.expiresAt["abc"])
            storage.invalidate("abc")
            assertFailsWith<NoSuchElementException> { storage.read("abc") }
            assertNull(docs.values["abc"])
        }
    }

    @Test
    fun missingReadThrows() {
        runBlocking {
            val storage = ArangoSessionStorage(MemorySessionDocuments())
            assertFailsWith<NoSuchElementException> { storage.read("missing") }
        }
    }

    @Test
    fun writeOverwritesExisting() {
        runBlocking {
            val docs = MemorySessionDocuments()
            val storage = ArangoSessionStorage(docs, clock = { 2_000L }, ttlSeconds = 100)
            storage.write("s", "first")
            storage.write("s", "second")
            assertEquals("second", storage.read("s"))
            assertEquals(2L + 100, docs.expiresAt["s"])
        }
    }

    @Test
    fun emptyIdRejected() {
        runBlocking {
            val storage = ArangoSessionStorage(MemorySessionDocuments())
            assertFailsWith<IllegalArgumentException> { storage.write("", "v") }
            assertFailsWith<IllegalArgumentException> { storage.read("") }
            assertFailsWith<IllegalArgumentException> { storage.invalidate("") }
        }
    }
}

private class MemorySessionDocuments : SessionDocuments {
    val values = mutableMapOf<String, String>()
    val expiresAt = mutableMapOf<String, Long>()

    override suspend fun upsert(id: String, value: String, updatedAt: Long, expiresAt: Long) {
        values[id] = value
        this.expiresAt[id] = expiresAt
    }

    override suspend fun findValue(id: String): String? = values[id]

    override suspend fun delete(id: String) {
        values.remove(id)
        expiresAt.remove(id)
    }
}
