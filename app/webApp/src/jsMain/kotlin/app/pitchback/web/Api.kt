package app.pitchback.web

import app.pitchback.AppJson
import app.pitchback.api.AdminEventsResponse
import app.pitchback.api.AdminPendingDto
import app.pitchback.api.AdminStatsDto
import app.pitchback.api.AdminVisitsResponse
import app.pitchback.api.AiStreamRequest
import app.pitchback.api.CreatePitchRequest
import app.pitchback.api.EndMeetRequest
import app.pitchback.api.NoShowRequest
import app.pitchback.api.ErrorBody
import app.pitchback.api.FeedResponse
import app.pitchback.api.InboxDto
import app.pitchback.api.LocationDto
import app.pitchback.api.LocationRecDto
import app.pitchback.api.LocationWriteRequest
import app.pitchback.api.LoginRequest
import app.pitchback.api.MeDto
import app.pitchback.api.MeetDto
import app.pitchback.api.NoShowDto
import app.pitchback.api.PitchBackRequest
import app.pitchback.api.PitchCardDto
import app.pitchback.api.RecommendLocationRequest
import app.pitchback.api.RegisterRequest
import app.pitchback.api.TopicDto
import app.pitchback.api.UpdateProfileRequest
import app.pitchback.api.UpdateRemainingRequest
import app.pitchback.api.UploadResponse
import app.pitchback.api.UserAdminDto
import app.pitchback.api.VerificationDto
import app.pitchback.api.VerificationRequestBody
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.fetch.RequestCredentials
import org.w3c.fetch.RequestInit
import org.w3c.files.File
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.json

class ApiError(message: String, val code: String) : RuntimeException(message)

var requestLang: String = "en"

private fun langHeader(): String = if (requestLang.lowercase().startsWith("vi")) "vi" else "en"

fun apiBase(): String {
    val loc = window.location
    if (loc.port == "8081") return "${loc.protocol}//${loc.hostname}:8080"
    val injected = js(
        "((typeof window !== 'undefined' && typeof window.__API_BASE__ === 'string') ? window.__API_BASE__ : '')",
    ) as String
    return injected.trim().trimEnd('/')
}

fun assetUrl(path: String): String {
    if (path.startsWith("http")) return path
    return apiBase() + path
}

object Api {
    private suspend fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        form: FormData? = null,
    ): String {
        val init = js("{}")
        init.method = method
        init.credentials = "include"
        val headers = js("{}")
        headers["Accept-Language"] = langHeader()
        if (form == null && (body != null || method != "GET")) {
            headers["Content-Type"] = "application/json"
        }
        init.headers = headers
        if (form != null) init.body = form else if (body != null) init.body = body
        val res = window.fetch(apiBase() + path, init).await()
        val text = res.text().await()
        if (!res.ok) {
            val err = runCatching { AppJson.decodeFromString(ErrorBody.serializer(), text) }.getOrNull()
            throw ApiError(err?.error ?: text.ifBlank { "Request failed" }, err?.code ?: "ERROR")
        }
        return text
    }

    private suspend inline fun <reified T> get(path: String): T =
        AppJson.decodeFromString(request(path))

    suspend fun health() = request("/api/health")

    suspend fun recordVisit() {
        request("/api/visits", "POST", "{}")
    }

    suspend fun register(body: RegisterRequest): MeDto =
        AppJson.decodeFromString(request("/api/auth/register", "POST", AppJson.encodeToString(body)))

    suspend fun login(body: LoginRequest): MeDto =
        AppJson.decodeFromString(request("/api/auth/login", "POST", AppJson.encodeToString(body)))

    suspend fun logout() {
        request("/api/auth/logout", "POST", "{}")
    }

    suspend fun me(): MeDto = get("/api/me")

    suspend fun updateMe(body: UpdateProfileRequest): MeDto =
        AppJson.decodeFromString(request("/api/me", "PATCH", AppJson.encodeToString(body)))

    suspend fun verify(zalo: String) {
        request("/api/me/verify", "POST", AppJson.encodeToString(VerificationRequestBody(zalo)))
    }

    suspend fun myPitches(): FeedResponse = get("/api/me/pitches")

    suspend fun feed(before: Long? = null): FeedResponse {
        val q = if (before != null) "?before=$before" else ""
        return get("/api/pitches$q")
    }

    suspend fun pitch(id: String): PitchCardDto = get("/api/pitches/$id")

    suspend fun createPitch(body: CreatePitchRequest): PitchCardDto =
        AppJson.decodeFromString(request("/api/pitches", "POST", AppJson.encodeToString(body)))

    suspend fun updatePitch(id: String, body: CreatePitchRequest): PitchCardDto =
        AppJson.decodeFromString(request("/api/pitches/$id", "PATCH", AppJson.encodeToString(body)))

    suspend fun updateRemaining(id: String, remaining: Int): PitchCardDto =
        AppJson.decodeFromString(
            request("/api/pitches/$id/remaining", "PATCH", AppJson.encodeToString(UpdateRemainingRequest(remaining))),
        )

    suspend fun deletePitch(id: String) {
        request("/api/pitches/$id", "DELETE")
    }

    suspend fun pitchBack(id: String, text: String, slotAt: Long) {
        request("/api/pitches/$id/pitch-back", "POST", AppJson.encodeToString(PitchBackRequest(text, slotAt)))
    }

    suspend fun topics(): List<TopicDto> = get("/api/topics")

    suspend fun locations(): List<LocationDto> = get("/api/locations")

    suspend fun recommendLocation(body: RecommendLocationRequest) {
        request("/api/locations/recommend", "POST", AppJson.encodeToString(body))
    }

    suspend fun inbox(): InboxDto = get("/api/inbox")

    suspend fun accept(id: String) { request("/api/pitch-backs/$id/accept", "POST", "{}") }
    suspend fun pass(id: String) { request("/api/pitch-backs/$id/pass", "POST", "{}") }
    suspend fun cancelPitchBack(id: String) { request("/api/pitch-backs/$id/cancel", "POST", "{}") }
    suspend fun cancelMeet(id: String) { request("/api/meets/$id/cancel", "POST", "{}") }
    suspend fun endMeet(id: String, message: String): MeetDto =
        AppJson.decodeFromString(request("/api/meets/$id/end", "POST", AppJson.encodeToString(EndMeetRequest(message))))
    suspend fun dismissMeet(id: String) { request("/api/meets/$id/dismiss", "POST", "{}") }
    suspend fun noShow(id: String, message: String = "") {
        request("/api/meets/$id/no-show", "POST", AppJson.encodeToString(NoShowRequest(message)))
    }

    suspend fun upload(file: File, onProgress: ((Double) -> Unit)? = null): String {
        val form = FormData()
        form.append("file", file)
        val text = uploadRequest(form, onProgress)
        return AppJson.decodeFromString(UploadResponse.serializer(), text).url
    }

    private suspend fun uploadRequest(form: FormData, onProgress: ((Double) -> Unit)?): String =
        suspendCancellableCoroutine { cont ->
            val xhr = XMLHttpRequest()
            xhr.open("POST", apiBase() + "/api/uploads")
            xhr.withCredentials = true
            xhr.setRequestHeader("Accept-Language", langHeader())
            if (onProgress != null) {
                xhr.upload.addEventListener("progress", { event ->
                    val e = event.asDynamic()
                    val total = (e.total as? Number)?.toDouble() ?: 0.0
                    if (total > 0.0) {
                        val loaded = (e.loaded as? Number)?.toDouble() ?: 0.0
                        onProgress((loaded / total).coerceIn(0.0, 1.0))
                    }
                })
            }
            xhr.addEventListener("load", {
                if (!cont.isActive) return@addEventListener
                val text = xhr.responseText
                if (xhr.status in 200..299) {
                    cont.resume(text)
                } else {
                    val err = runCatching { AppJson.decodeFromString(ErrorBody.serializer(), text) }.getOrNull()
                    cont.resumeWithException(
                        ApiError(err?.error ?: text.ifBlank { "Request failed" }, err?.code ?: "ERROR"),
                    )
                }
            })
            xhr.addEventListener("error", {
                if (cont.isActive) cont.resumeWithException(ApiError("Request failed", "ERROR"))
            })
            xhr.addEventListener("abort", {
                if (cont.isActive) cont.resumeWithException(ApiError("Request failed", "ERROR"))
            })
            cont.invokeOnCancellation { xhr.abort() }
            xhr.send(form)
        }

    suspend fun adminPending(): AdminPendingDto = get("/api/admin/pending")
    suspend fun adminStats(): AdminStatsDto = get("/api/admin/stats")
    suspend fun adminVisits(): AdminVisitsResponse = get("/api/admin/visits")
    suspend fun adminActivity(before: Long? = null): AdminEventsResponse {
        val q = if (before != null) "?before=$before" else ""
        return get("/api/admin/activity$q")
    }
    suspend fun adminPitches(): FeedResponse = get("/api/admin/pitches")
    suspend fun approvePitch(id: String) { request("/api/admin/pitches/$id/approve", "POST", "{}") }
    suspend fun dismissPitch(id: String) { request("/api/admin/pitches/$id/dismiss", "POST", "{}") }
    suspend fun adminVerifications(): List<VerificationDto> = get("/api/admin/verifications")
    suspend fun approveVerification(id: String) { request("/api/admin/verifications/$id/approve", "POST", "{}") }
    suspend fun dismissVerification(id: String) { request("/api/admin/verifications/$id/dismiss", "POST", "{}") }
    suspend fun adminUsers(): List<UserAdminDto> = get("/api/admin/users")
    suspend fun promote(id: String) { request("/api/admin/users/$id/promote", "POST", "{}") }
    suspend fun toggleDisable(id: String) { request("/api/admin/users/$id/disable", "POST", "{}") }
    suspend fun adminLocations(): List<LocationDto> = get("/api/admin/locations")
    suspend fun createLocation(body: LocationWriteRequest): LocationDto =
        AppJson.decodeFromString(request("/api/admin/locations", "POST", AppJson.encodeToString(body)))
    suspend fun updateLocation(id: String, body: LocationWriteRequest): LocationDto =
        AppJson.decodeFromString(request("/api/admin/locations/$id", "PUT", AppJson.encodeToString(body)))
    suspend fun deleteLocation(id: String) { request("/api/admin/locations/$id", "DELETE") }
    suspend fun locationRecs(): List<LocationRecDto> = get("/api/admin/location-recs")
    suspend fun dismissRec(id: String) { request("/api/admin/location-recs/$id/dismiss", "POST", "{}") }
    suspend fun noShows(): List<NoShowDto> = get("/api/admin/no-shows")
    suspend fun reviewNoShow(id: String) { request("/api/admin/no-shows/$id/review", "POST", "{}") }

    suspend fun streamAi(req: AiStreamRequest, onChunk: (String) -> Unit, onError: (String) -> Unit) {
        val init = js("{}")
        init.method = "POST"
        init.credentials = "include"
        init.headers = json("Content-Type" to "application/json", "Accept-Language" to langHeader())
        init.body = AppJson.encodeToString(req)
        val res = window.fetch(apiBase() + "/api/ai/stream", init).await()
        val body = res.asDynamic().body ?: run {
            onError("No stream")
            return
        }
        val reader = body.getReader()
        val decoder = js("new TextDecoder()")
        var buffer = ""
        while (true) {
            val result = reader.read().unsafeCast<kotlin.js.Promise<dynamic>>().await()
            if (result.done == true) break
            buffer += decoder.decode(result.value) as String
            val parts = buffer.split("\n\n")
            buffer = parts.last()
            for (part in parts.dropLast(1)) {
                val line = part.trim().removePrefix("data:").trim()
                if (line.isEmpty()) continue
                val obj = runCatching { AppJson.parseToJsonElement(line) }.getOrNull() as? JsonObject
                val err = obj?.get("error")?.jsonPrimitive?.content
                val text = obj?.get("text")?.jsonPrimitive?.content
                if (!err.isNullOrBlank() && !obj.containsKey("text") && !obj.containsKey("done")) {
                    onError(err)
                } else if (!text.isNullOrEmpty()) {
                    onChunk(text)
                }
            }
        }
    }
}
