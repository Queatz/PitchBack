package app.pitchback.db

import app.pitchback.AppConfig
import app.pitchback.AppJson
import app.pitchback.http.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.Base64

class Arango(private val config: AppConfig) {
    private val log = LoggerFactory.getLogger(Arango::class.java)
    val auth = "Basic " + Base64.getEncoder()
        .encodeToString("${config.arangoUser}:${config.arangoPassword}".toByteArray())

    val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(AppJson)
        }
        expectSuccess = false
    }

    fun dbPath(path: String): String =
        "${config.arangoUrl}/_db/${config.arangoDatabase}$path"

    suspend fun setup() {
        val createdb = http.request("${config.arangoUrl}/_api/database") {
            method = HttpMethod.Post
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("name", config.arangoDatabase) })
        }
        if (createdb.status.value >= 400 && createdb.status != HttpStatusCode.Conflict) {
            val body = createdb.bodyAsText()
            if (!body.contains("duplicate") && createdb.status != HttpStatusCode.Conflict) {
                log.warn("Create database: {} {}", createdb.status, body)
            }
        }
        val collections = listOf(
            "users", "locations", "pitches", "pitchBacks", "meets",
            "verifications", "locationRecs", "noShows", "sessions", "activityEvents", "visits",
        )
        for (name in collections) {
            val res = http.request(dbPath("/_api/collection")) {
                method = HttpMethod.Post
                header(HttpHeaders.Authorization, auth)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("name", name) })
            }
            if (res.status.value >= 400) {
                val body = res.bodyAsText()
                if (!body.contains("duplicate") && res.status != HttpStatusCode.Conflict) {
                    log.warn("Create collection {}: {} {}", name, res.status, body)
                }
            }
        }
        ensureIndex("users", listOf("phone"), unique = true)
        ensureIndex("users", listOf("lastActiveAt"), unique = false)
        ensureIndex("pitches", listOf("createdAt"), unique = false)
        ensureIndex("activityEvents", listOf("createdAt"), unique = false)
        ensureIndex("visits", listOf("dayStart"), unique = false)
        ensureIndex("pitches", listOf("status"), unique = false)
        ensureIndex("pitchBacks", listOf("toUserId"), unique = false)
        ensureIndex("pitchBacks", listOf("fromUserId"), unique = false)
        ensureIndex("meets", listOf("ownerId"), unique = false)
        ensureIndex("meets", listOf("guestId"), unique = false)
        ensureTtlIndex("sessions", "expiresAt", expireAfter = 0)
        log.info("ArangoDB ready at {} / {}", config.arangoUrl, config.arangoDatabase)
    }

    private suspend fun ensureIndex(collection: String, fields: List<String>, unique: Boolean) {
        http.request(dbPath("/_api/index?collection=$collection")) {
            method = HttpMethod.Post
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("type", "persistent")
                    put("unique", unique)
                    put("fields", JsonArray(fields.map { JsonPrimitive(it) }))
                },
            )
        }
    }

    private suspend fun ensureTtlIndex(collection: String, field: String, expireAfter: Long) {
        http.request(dbPath("/_api/index?collection=$collection")) {
            method = HttpMethod.Post
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("type", "ttl")
                    put("expireAfter", expireAfter)
                    put("fields", JsonArray(listOf(JsonPrimitive(field))))
                },
            )
        }
    }

    suspend fun query(aql: String, bind: Map<String, JsonElement> = emptyMap()): JsonArray {
        val out = mutableListOf<JsonElement>()
        var payload = http.request(dbPath("/_api/cursor")) {
            method = HttpMethod.Post
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("query", aql)
                    put("bindVars", JsonObject(bind))
                    put("batchSize", 500)
                },
            )
        }
        while (true) {
            val json = parseObject(payload.bodyAsText(), payload.status)
            val result = json["result"]?.jsonArray ?: JsonArray(emptyList())
            out.addAll(result)
            val hasMore = json["hasMore"]?.toString()?.contains("true") == true
            if (hasMore) {
                val id = json["id"]?.jsonPrimitive?.contentOrNull ?: break
                payload = http.request(dbPath("/_api/cursor/$id")) {
                    method = HttpMethod.Put
                    header(HttpHeaders.Authorization, auth)
                }
            } else {
                break
            }
        }
        return JsonArray(out)
    }

    suspend inline fun <reified T> queryList(aql: String, bind: Map<String, JsonElement> = emptyMap()): List<T> =
        query(aql, bind).map { AppJson.decodeFromJsonElement(it) }

    suspend inline fun <reified T> queryOne(aql: String, bind: Map<String, JsonElement> = emptyMap()): T? =
        queryList<T>(aql, bind).firstOrNull()

    suspend inline fun <reified T> insert(collection: String, doc: T): T {
        val element = AppJson.encodeToJsonElement(doc)
        val res = http.request(dbPath("/_api/document/$collection?returnNew=true")) {
            method = HttpMethod.Post
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(element)
        }
        val json = parseObject(res.bodyAsText(), res.status)
        val newDoc = json["new"] ?: json
        return AppJson.decodeFromJsonElement(newDoc)
    }

    suspend inline fun <reified T> update(collection: String, key: String, patch: JsonObject): T {
        val res = http.request(dbPath("/_api/document/$collection/$key?returnNew=true")) {
            method = HttpMethod.Patch
            header(HttpHeaders.Authorization, auth)
            contentType(ContentType.Application.Json)
            setBody(patch)
        }
        val json = parseObject(res.bodyAsText(), res.status)
        val newDoc = json["new"] ?: json
        return AppJson.decodeFromJsonElement(newDoc)
    }

    suspend fun remove(collection: String, key: String) {
        val res = http.request(dbPath("/_api/document/$collection/$key")) {
            method = HttpMethod.Delete
            header(HttpHeaders.Authorization, auth)
        }
        if (res.status.value >= 400 && res.status != HttpStatusCode.NotFound) {
            parseObject(res.bodyAsText(), res.status)
        }
    }

    suspend fun close() {
        http.close()
    }

    fun parseObject(text: String, status: HttpStatusCode): JsonObject {
        val json = runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw ApiException(
                HttpStatusCode.BadGateway,
                "DB",
                "DB_ERROR",
            )
        val err = json["error"]
        val isError = err != null && err !is JsonNull && err.toString() != "false" &&
            (err.jsonPrimitive.contentOrNull == "true" || err.toString() == "true")
        if (status.value >= 400 || isError) {
            val num = json["errorNum"]?.jsonPrimitive?.intOrNull
            if (num == 1210) {
                throw ApiException.conflict("DUPLICATE", "DUPLICATE")
            }
            throw ApiException(
                if (status.value >= 400) status else HttpStatusCode.BadGateway,
                "DB",
                "DB_ERROR",
            )
        }
        return json
    }
}

fun v(value: String): JsonElement = JsonPrimitive(value)
fun v(value: Int): JsonElement = JsonPrimitive(value)
fun v(value: Long): JsonElement = JsonPrimitive(value)
fun v(value: Boolean): JsonElement = JsonPrimitive(value)
fun vNull(): JsonElement = JsonNull
