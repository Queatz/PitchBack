package app.pitchback.sse

import io.ktor.server.sse.ServerSSESession
import io.ktor.sse.ServerSentEvent
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class EventHub {
    private val log = LoggerFactory.getLogger(EventHub::class.java)
    private val sessions = ConcurrentHashMap<String, CopyOnWriteArrayList<ServerSSESession>>()

    fun register(userId: String, session: ServerSSESession) {
        sessions.getOrPut(userId) { CopyOnWriteArrayList() }.add(session)
    }

    fun unregister(userId: String, session: ServerSSESession) {
        sessions[userId]?.remove(session)
    }

    suspend fun send(userId: String, event: String, data: String = "{}") {
        val list = sessions[userId] ?: return
        list.toList().forEach { session ->
            try {
                session.send(ServerSentEvent(data = data, event = event))
            } catch (e: Exception) {
                log.debug("SSE send failed for {}", userId, e)
                list.remove(session)
            }
        }
    }

    suspend fun sendMany(userIds: Collection<String>, event: String, data: String = "{}") {
        userIds.distinct().forEach { send(it, event, data) }
    }
}
