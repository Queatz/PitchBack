package app.pitchback.db

import io.ktor.server.sessions.SessionStorage

internal const val SESSION_MAX_AGE_SECONDS = 60L * 60 * 24 * 365

internal interface SessionDocuments {
    suspend fun upsert(id: String, value: String, updatedAt: Long, expiresAt: Long)
    suspend fun findValue(id: String): String?
    suspend fun delete(id: String)
}

class ArangoSessionStorage internal constructor(
    private val documents: SessionDocuments,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlSeconds: Long = SESSION_MAX_AGE_SECONDS,
) : SessionStorage {
    constructor(arango: Arango) : this(ArangoSessionDocuments(arango))

    override suspend fun write(id: String, value: String) {
        require(id.isNotEmpty()) { "Session id is empty" }
        val now = clock()
        documents.upsert(id, value, now, now / 1000 + ttlSeconds)
    }

    override suspend fun read(id: String): String {
        require(id.isNotEmpty()) { "Session id is empty" }
        return documents.findValue(id) ?: throw NoSuchElementException("Session $id not found")
    }

    override suspend fun invalidate(id: String) {
        require(id.isNotEmpty()) { "Session id is empty" }
        documents.delete(id)
    }
}

private class ArangoSessionDocuments(private val arango: Arango) : SessionDocuments {
    override suspend fun upsert(id: String, value: String, updatedAt: Long, expiresAt: Long) {
        arango.query(
            """
            UPSERT { _key: @key }
            INSERT { _key: @key, value: @value, updatedAt: @updatedAt, expiresAt: @expiresAt }
            UPDATE { value: @value, updatedAt: @updatedAt, expiresAt: @expiresAt }
            IN sessions
            """.trimIndent(),
            mapOf(
                "key" to v(id),
                "value" to v(value),
                "updatedAt" to v(updatedAt),
                "expiresAt" to v(expiresAt),
            ),
        )
    }

    override suspend fun findValue(id: String): String? =
        arango.queryOne(
            "FOR s IN sessions FILTER s._key == @key RETURN s.value",
            mapOf("key" to v(id)),
        )

    override suspend fun delete(id: String) {
        arango.remove("sessions", id)
    }
}
