package app.pitchback.ai

import app.pitchback.AppConfig
import app.pitchback.AppJson
import app.pitchback.api.AiStreamRequest
import app.pitchback.http.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class QwenClient(private val config: AppConfig) {
    private val http = HttpClient(CIO) {
        expectSuccess = false
    }

    suspend fun stream(req: AiStreamRequest, onChunk: suspend (String) -> Unit) {
        if (config.dashscopeKey.isBlank()) {
            throw ApiException(
                HttpStatusCode.ServiceUnavailable,
                "AI_UNAVAILABLE",
                "AI_UNAVAILABLE",
            )
        }
        val kindLabel = when (req.kind) {
            "pitch" -> "a meetup pitch (80+ characters, first-person, specific about Saigon)"
            "pitchBack" -> "a pitch-back reply (40+ characters, warm, specific)"
            "intro" -> "a short personal intro for a social meetup profile"
            "final" -> "a short final message after meeting in person"
            "location" -> "a location description for a cafe or meetup spot in Saigon"
            else -> "short social copy for a meetup app"
        }
        val languageName = if (req.language.startsWith("vi")) "Vietnamese" else "English"
        val user = buildString {
            append("Write $kindLabel. Language: $languageName. ")
            append("No markdown, no surrounding quotes, no preamble. ")
            if (req.starter.isNotBlank()) append("Starter idea: ${req.starter.trim()}. ")
            if (req.existing.isNotBlank()) append("Current draft to improve: ${req.existing.trim()}. ")
            if (req.extra.isNotBlank()) append("Extra instructions: ${req.extra.trim()}. ")
        }
        val body = buildJsonObject {
            put("model", config.qwenModel)
            put("stream", true)
            put("enable_thinking", false)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put(
                                "content",
                                "You write natural copy for Saigon.Social, a social talk-and-meet app in Ho Chi Minh City. Keep it human, specific, and concise.",
                            )
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", user)
                        },
                    )
                },
            )
        }
        http.preparePost("${config.dashscopeBase}/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer ${config.dashscopeKey}")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.execute { response ->
            if (response.status.value >= 400) {
                val ch = response.bodyAsChannel()
                while (!ch.isClosedForRead) {
                    ch.readUTF8Line() ?: break
                }
                throw ApiException(
                    HttpStatusCode.BadGateway,
                    "AI",
                    "AI_FAILED",
                )
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                val chunk = runCatching {
                    val json = AppJson.parseToJsonElement(data).jsonObject
                    json["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("delta")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                if (!chunk.isNullOrEmpty()) onChunk(chunk)
            }
        }
    }

    fun close() = http.close()
}
