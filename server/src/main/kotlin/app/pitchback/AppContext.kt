package app.pitchback

import app.pitchback.ai.QwenClient
import app.pitchback.auth.UserSession
import app.pitchback.db.Database
import app.pitchback.db.UserRecord
import app.pitchback.http.ApiException
import app.pitchback.sse.EventHub
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

class AppContext(
    val config: AppConfig,
    val db: Database,
    val hub: EventHub,
    val qwen: QwenClient,
) {
    fun now(): Long = System.currentTimeMillis()
}

suspend fun ApplicationCall.currentUser(app: AppContext): UserRecord? {
    val id = sessions.get<UserSession>()?.userId ?: return null
    val user = app.db.userById(id) ?: return null
    if (user.disabled) return null
    return user
}

suspend fun ApplicationCall.requireUser(app: AppContext): UserRecord =
    currentUser(app) ?: throw ApiException.unauthorized()

suspend fun ApplicationCall.requireAdmin(app: AppContext): UserRecord {
    val user = requireUser(app)
    if (!user.isAdmin) throw ApiException.forbidden("ADMIN", "ADMIN_ONLY")
    return user
}
