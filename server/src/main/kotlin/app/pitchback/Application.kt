package app.pitchback

import app.pitchback.ai.QwenClient
import app.pitchback.api.ErrorBody
import app.pitchback.auth.UserSession
import app.pitchback.db.Arango
import app.pitchback.db.Database
import app.pitchback.http.ApiException
import app.pitchback.i18n.ApiMessages
import app.pitchback.routes.api
import app.pitchback.sse.EventHub
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionSerializer
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sse.SSE
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File

fun main() {
    val config = AppConfig.load()
    embeddedServer(Netty, port = config.port, host = config.host, module = { module(config) })
        .start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.load()) {
    val log = LoggerFactory.getLogger("SaigonSocial")
    val arango = Arango(config)
    runBlocking { arango.setup() }
    val db = Database(arango)
    val hub = EventHub()
    val qwen = QwenClient(config)
    val app = AppContext(config, db, hub, qwen)

    install(DefaultHeaders)
    install(Compression)
    install(CallLogging) {
        level = Level.INFO
    }
    install(ContentNegotiation) {
        json(AppJson)
    }
    install(SSE)
    install(Sessions) {
        cookie<UserSession>("saigon_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 30
            cookie.secure = config.isProd
            cookie.extensions["SameSite"] = "Lax"
            serializer = kotlinxSessionSerializer()
            transform(SessionTransportTransformerMessageAuthentication(config.sessionSecret))
        }
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.AcceptLanguage)
        allowCredentials = true
        allowNonSimpleContentTypes = true
        config.corsOrigins.forEach { origin ->
            allowHost(origin.host, schemes = origin.schemes, subDomains = origin.subDomains)
        }
    }
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            val lang = ApiMessages.langFromAccept(call.request.headers[HttpHeaders.AcceptLanguage])
            val key = cause.message ?: cause.code
            call.respond(cause.status, ErrorBody(ApiMessages.t(lang, key), cause.code))
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled error", cause)
            val lang = ApiMessages.langFromAccept(call.request.headers[HttpHeaders.AcceptLanguage])
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorBody(ApiMessages.t(lang, "SERVER"), "SERVER"),
            )
        }
    }

    routing {
        api(app)
        staticFiles("/uploads", config.uploadDir)
        if (config.isProd) {
            val dist = File("app/webApp/build/dist/js/productionExecutable")
            if (dist.isDirectory) {
                staticFiles("/", dist)
                val index = File(dist, "index.html")
                get("{path...}") {
                    val path = call.request.path()
                    if (path.startsWith("/api") || path.startsWith("/uploads")) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respondFile(index)
                    }
                }
            }
        }
    }

    launch {
        while (isActive) {
            delay(30_000)
            try {
                db.expirePitchBacks(System.currentTimeMillis()).forEach { pb ->
                    hub.send(pb.fromUserId, "inbox")
                    hub.send(pb.toUserId, "inbox")
                }
            } catch (e: Exception) {
                log.warn("Expiry job failed: {}", e.message)
            }
        }
    }
}

private fun kotlinxSessionSerializer(): SessionSerializer<UserSession> =
    object : SessionSerializer<UserSession> {
        private val ser = serializer<UserSession>()
        override fun serialize(session: UserSession): String = AppJson.encodeToString(ser, session)
        override fun deserialize(text: String): UserSession = AppJson.decodeFromString(ser, text)
    }
