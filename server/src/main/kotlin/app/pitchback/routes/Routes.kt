package app.pitchback.routes

import app.pitchback.AppContext
import app.pitchback.AppJson
import app.pitchback.api.AiStreamRequest
import app.pitchback.api.CreatePitchRequest
import app.pitchback.api.EndMeetRequest
import app.pitchback.api.NoShowRequest
import app.pitchback.api.ErrorBody
import app.pitchback.api.FeedResponse
import app.pitchback.api.InboxDto
import app.pitchback.api.LocationWriteRequest
import app.pitchback.api.LoginRequest
import app.pitchback.api.MeDto
import app.pitchback.api.OkDto
import app.pitchback.api.OpeningHours
import app.pitchback.api.PitchBackRequest
import app.pitchback.api.RecommendLocationRequest
import app.pitchback.api.RegisterRequest
import app.pitchback.api.UpdateProfileRequest
import app.pitchback.api.UpdateRemainingRequest
import app.pitchback.api.UploadResponse
import app.pitchback.api.VerificationRequestBody
import app.pitchback.api.WeeklyAvailability
import app.pitchback.auth.UserSession
import app.pitchback.currentUser
import app.pitchback.db.LocationRecRecord
import app.pitchback.db.LocationRecord
import app.pitchback.db.MeetRecord
import app.pitchback.db.NoShowRecord
import app.pitchback.db.PitchBackRecord
import app.pitchback.db.PitchRecord
import app.pitchback.db.UserRecord
import app.pitchback.db.VerificationRecord
import app.pitchback.db.patchOf
import app.pitchback.domain.STATUS_ACCEPTED
import app.pitchback.domain.STATUS_ACTIVE
import app.pitchback.domain.STATUS_APPROVED
import app.pitchback.domain.STATUS_CANCELLED
import app.pitchback.domain.STATUS_DISMISSED
import app.pitchback.domain.STATUS_ENDED
import app.pitchback.domain.STATUS_PASSED
import app.pitchback.domain.STATUS_PENDING
import app.pitchback.domain.STATUS_REVIEWED
import app.pitchback.domain.canAcceptPitchBack
import app.pitchback.domain.canDisableAccount
import app.pitchback.domain.canEndMeet
import app.pitchback.domain.canReportNoShow
import app.pitchback.domain.canPitchBackOnOwnPitch
import app.pitchback.domain.endMeetForUser
import app.pitchback.domain.meetActiveForUser
import app.pitchback.domain.meetEndedBy
import app.pitchback.domain.otherFinalMessage
import app.pitchback.domain.shouldShowInboxFinalMessage
import app.pitchback.domain.isAdminOnCreate
import app.pitchback.domain.normalizePhone
import app.pitchback.domain.pitchBackExpiresAt
import app.pitchback.domain.pitchIsPublic
import app.pitchback.domain.remainingAfterAccept
import app.pitchback.domain.validateHighlights
import app.pitchback.domain.validateName
import app.pitchback.domain.validatePassword
import app.pitchback.domain.nextOfferedSlots
import app.pitchback.domain.normalizeAvailability
import app.pitchback.domain.validateAvailability
import app.pitchback.domain.validateChosenSlot
import app.pitchback.domain.validatePhotos
import app.pitchback.domain.validatePitchBackText
import app.pitchback.domain.validatePitchText
import app.pitchback.domain.validateRemainingOnCreate
import app.pitchback.http.ApiException
import app.pitchback.i18n.ApiMessages
import app.pitchback.requireAdmin
import app.pitchback.requireUser
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.set
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.delay
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.mindrot.jbcrypt.BCrypt
import java.io.File
import java.util.UUID

fun Route.api(app: AppContext) {
    val db = app.db

    get("/api/health") {
        call.respond(OkDto())
    }

    sse("/api/events") {
        val user = call.currentUser(app) ?: return@sse
        app.hub.register(user.key, this)
        try {
            while (true) {
                send(ServerSentEvent(data = "{}", event = "ping"))
                delay(15_000)
            }
        } finally {
            app.hub.unregister(user.key, this)
        }
    }

    post("/api/auth/register") {
        val body = call.receive<RegisterRequest>()
        validateName(body.name)?.let { throw ApiException.bad(it, it) }
        validatePassword(body.password)?.let { throw ApiException.bad(it, it) }
        val phone = normalizePhone(body.phone) ?: throw ApiException.bad("PHONE", "PHONE_INVALID")
        if (db.userByPhone(phone) != null) throw ApiException.conflict("PHONE_TAKEN", "PHONE_TAKEN")
        val now = app.now()
        val user = db.insertUser(
            UserRecord(
                key = UUID.randomUUID().toString(),
                name = body.name.trim(),
                phone = phone,
                passwordHash = BCrypt.hashpw(body.password, BCrypt.gensalt()),
                isAdmin = isAdminOnCreate(db.userCount()),
                createdAt = now,
            ),
        )
        call.sessions.set(UserSession(user.key))
        call.respond(HttpStatusCode.Created, meDto(app, user))
    }

    post("/api/auth/login") {
        val body = call.receive<LoginRequest>()
        val phone = normalizePhone(body.phone) ?: throw ApiException.bad("PHONE", "PHONE_INVALID")
        val user = db.userByPhone(phone) ?: throw ApiException.unauthorized("AUTH_BAD")
        if (user.disabled) throw ApiException.forbidden("DISABLED", "DISABLED")
        if (!BCrypt.checkpw(body.password, user.passwordHash)) {
            throw ApiException.unauthorized("AUTH_BAD")
        }
        call.sessions.set(UserSession(user.key))
        call.respond(meDto(app, user))
    }

    post("/api/auth/logout") {
        call.sessions.clear<UserSession>()
        call.respond(OkDto())
    }

    get("/api/me") {
        val user = call.requireUser(app)
        call.respond(meDto(app, user))
    }

    patch("/api/me") {
        val user = call.requireUser(app)
        val body = call.receive<UpdateProfileRequest>()
        val nextPhone = body.phone?.let { raw ->
            val phone = normalizePhone(raw) ?: throw ApiException.bad("PHONE", "PHONE_INVALID")
            if (db.userByPhone(phone, exceptUserId = user.key) != null) {
                throw ApiException.conflict("PHONE_TAKEN", "PHONE_TAKEN")
            }
            phone
        }
        val patch = patchOf {
            body.name?.let {
                validateName(it)?.let { code -> throw ApiException.bad(code, code) }
                put("name", it.trim())
            }
            body.intro?.let { put("intro", it.trim()) }
            body.photoUrl?.let { put("photoUrl", it) }
            nextPhone?.let { put("phone", it) }
            if (body.language != null) {
                val lang = body.language
                if (lang != "en" && lang != "vi" && lang != "") {
                    throw ApiException.bad("LANGUAGE", "LANGUAGE")
                }
                put("language", lang.ifBlank { null } ?: "")
            }
        }
        val updated = db.updateUser(user.key, patch)
        call.respond(meDto(app, updated))
    }

    post("/api/me/verify") {
        val user = call.requireUser(app)
        val body = call.receive<VerificationRequestBody>()
        val phone = normalizePhone(body.zaloPhone) ?: throw ApiException.bad("PHONE", "ZALO_PHONE_INVALID")
        if (user.verified) throw ApiException.bad("ALREADY", "ALREADY_VERIFIED")
        if (db.pendingVerification(user.key) != null) {
            throw ApiException.conflict("PENDING", "VERIFY_PENDING")
        }
        db.insertVerification(
            VerificationRecord(
                key = UUID.randomUUID().toString(),
                userId = user.key,
                zaloPhone = phone,
                status = STATUS_PENDING,
                createdAt = app.now(),
            ),
        )
        call.respond(OkDto())
    }

    get("/api/me/pitches") {
        val user = call.requireUser(app)
        val items = db.pitchesByAuthor(user.key).mapNotNull { db.toCard(it, user.key, app.now()) }
        call.respond(FeedResponse(items))
    }

    post("/api/uploads") {
        call.requireUser(app)
        var url: String? = null
        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem) {
                val type = part.contentType?.toString() ?: ""
                val ext = when {
                    type.contains("png") -> "png"
                    type.contains("webp") -> "webp"
                    type.contains("gif") -> "gif"
                    type.contains("jpeg") || type.contains("jpg") -> "jpg"
                    else -> throw ApiException.bad("FILE_TYPE", "FILE_TYPE")
                }
                val bytes = part.provider().readRemaining().readByteArray()
                if (bytes.size > 8 * 1024 * 1024) {
                    throw ApiException.bad("FILE_SIZE", "FILE_SIZE")
                }
                val name = "${UUID.randomUUID()}.$ext"
                File(app.config.uploadDir, name).writeBytes(bytes)
                url = "/uploads/$name"
            }
            part.dispose()
        }
        val saved = url ?: throw ApiException.bad("FILE", "FILE_MISSING")
        call.respond(UploadResponse(saved))
    }

    get("/api/topics") {
        call.respond(db.topics())
    }

    get("/api/locations") {
        call.respond(db.locationsNewest().map { it.toDto() })
    }

    get("/api/locations/{id}") {
        val loc = db.locationById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound("LOCATION_NOT_FOUND")
        call.respond(loc.toDto())
    }

    post("/api/locations/recommend") {
        val user = call.requireUser(app)
        val body = call.receive<RecommendLocationRequest>()
        if (body.name.trim().length < 2) throw ApiException.bad("NAME", "PLACE_NAME")
        db.insertLocationRec(
            LocationRecRecord(
                key = UUID.randomUUID().toString(),
                userId = user.key,
                name = body.name.trim(),
                description = body.description.trim(),
                address = body.address.trim(),
                status = STATUS_PENDING,
                createdAt = app.now(),
            ),
        )
        call.respond(HttpStatusCode.Created, OkDto())
    }

    get("/api/pitches") {
        val viewer = call.currentUser(app)
        val before = call.request.queryParameters["before"]?.toLongOrNull() ?: Long.MAX_VALUE
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 12).coerceIn(1, 30)
        val now = app.now()
        val records = db.feed(before, limit, now)
        val items = records.mapNotNull { db.toCard(it, viewer?.key, now) }
        call.respond(FeedResponse(items, items.lastOrNull()?.createdAt))
    }

    get("/api/pitches/{id}") {
        val viewer = call.currentUser(app)
        val pitch = db.pitchById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound("PITCH_NOT_FOUND")
        val author = db.userById(pitch.authorId)
        val now = app.now()
        val isOwner = viewer?.key == pitch.authorId
        val isAdmin = viewer?.isAdmin == true
        if (!isOwner && !isAdmin) {
            if (author == null || !pitchIsPublic(
                    pitch.status,
                    pitch.remainingPitchBacks,
                    pitch.availableUntil,
                    author.disabled,
                    now,
                )
            ) {
                throw ApiException.notFound("PITCH_NOT_FOUND")
            }
        }
        val card = db.toCard(pitch, viewer?.key, now, forReview = isAdmin)
            ?: throw ApiException.notFound("PITCH_NOT_FOUND")
        call.respond(card)
    }

    post("/api/pitches") {
        val user = call.requireUser(app)
        val body = call.receive<CreatePitchRequest>()
        validatePitchText(body.text)?.let {
            throw ApiException.bad(it, it)
        }
        validatePhotos(body.photoUrls)?.let { throw ApiException.bad(it, it) }
        validateRemainingOnCreate(body.remainingPitchBacks)?.let {
            throw ApiException.bad(it, it)
        }
        validateAvailability(body.availability)?.let { throw ApiException.bad(it, it) }
        val availability = normalizeAvailability(body.availability)
        db.locationById(body.locationId) ?: throw ApiException.bad("LOCATION", "LOCATION_REQUIRED")
        val now = app.now()
        val pitch = db.insertPitch(
            PitchRecord(
                key = UUID.randomUUID().toString(),
                authorId = user.key,
                text = body.text.trim(),
                photoUrls = body.photoUrls,
                remainingPitchBacks = body.remainingPitchBacks,
                availableUntil = body.availableUntil,
                topic = body.topic.trim(),
                locationId = body.locationId,
                allowSocialPosting = body.allowSocialPosting,
                status = STATUS_PENDING,
                createdAt = now,
                availability = availability,
            ),
        )
        val recName = body.recommendLocationName?.trim().orEmpty()
        if (recName.length >= 2) {
            db.insertLocationRec(
                LocationRecRecord(
                    key = UUID.randomUUID().toString(),
                    userId = user.key,
                    name = recName,
                    description = body.recommendLocationNote?.trim().orEmpty(),
                    status = STATUS_PENDING,
                    createdAt = now,
                ),
            )
        }
        val card = db.toCard(pitch, user.key, now) ?: throw ApiException.notFound()
        call.respond(HttpStatusCode.Created, card)
    }

    patch("/api/pitches/{id}/remaining") {
        val user = call.requireUser(app)
        val pitch = db.pitchById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
        if (pitch.authorId != user.key) throw ApiException.forbidden()
        val remaining = call.receive<UpdateRemainingRequest>().remainingPitchBacks
        if (remaining < 0 || remaining > 99) throw ApiException.bad("REMAINING", "REMAINING_RANGE")
        val updated = db.updatePitch(pitch.key, patchOf { put("remainingPitchBacks", remaining) })
        app.hub.sendMany(listOf(user.key), "feed")
        call.respond(db.toCard(updated, user.key, app.now())!!)
    }

    patch("/api/pitches/{id}") {
        val user = call.requireUser(app)
        val pitch = db.pitchById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound("PITCH_NOT_FOUND")
        if (pitch.authorId != user.key) throw ApiException.forbidden()
        val body = call.receive<CreatePitchRequest>()
        validatePitchText(body.text)?.let { throw ApiException.bad(it, it) }
        validatePhotos(body.photoUrls)?.let { throw ApiException.bad(it, it) }
        if (body.remainingPitchBacks < 0 || body.remainingPitchBacks > 99) {
            throw ApiException.bad("REMAINING", "REMAINING_RANGE")
        }
        validateAvailability(body.availability)?.let { throw ApiException.bad(it, it) }
        val availability = normalizeAvailability(body.availability)
        db.locationById(body.locationId) ?: throw ApiException.bad("LOCATION", "LOCATION_REQUIRED")
        val updated = db.updatePitch(
            pitch.key,
            patchOf {
                put("text", body.text.trim())
                putJsonArray("photoUrls") { body.photoUrls.forEach { add(it) } }
                put("remainingPitchBacks", body.remainingPitchBacks)
                if (body.availableUntil == null) put("availableUntil", JsonNull)
                else put("availableUntil", body.availableUntil)
                put("topic", body.topic.trim())
                put("locationId", body.locationId)
                put("allowSocialPosting", body.allowSocialPosting)
                put("availability", AppJson.encodeToJsonElement(WeeklyAvailability.serializer(), availability))
            },
        )
        app.hub.send(user.key, "me")
        app.hub.send(user.key, "feed")
        call.respond(db.toCard(updated, user.key, app.now())!!)
    }

    delete("/api/pitches/{id}") {
        val user = call.requireUser(app)
        val pitch = db.pitchById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound("PITCH_NOT_FOUND")
        if (pitch.authorId != user.key) throw ApiException.forbidden()
        db.deletePitch(pitch.key)
        app.hub.send(user.key, "me")
        app.hub.send(user.key, "feed")
        call.respond(OkDto())
    }

    post("/api/pitches/{id}/pitch-back") {
        val user = call.requireUser(app)
        val pitch = db.pitchById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
        val author = db.userById(pitch.authorId) ?: throw ApiException.notFound()
        val now = app.now()
        if (!canPitchBackOnOwnPitch(pitch.authorId, user.key)) {
            throw ApiException.bad("OWN", "OWN_PITCH")
        }
        if (!pitchIsPublic(pitch.status, pitch.remainingPitchBacks, pitch.availableUntil, author.disabled, now)) {
            throw ApiException.bad("UNAVAILABLE", "PITCH_UNAVAILABLE")
        }
        val body = call.receive<PitchBackRequest>()
        validatePitchBackText(body.text)?.let {
            throw ApiException.bad(it, it)
        }
        val taken = db.takenSlotAtsForUser(pitch.authorId, now)
        val offered = nextOfferedSlots(
            pitch.availability,
            now,
            availableUntil = pitch.availableUntil,
            takenAts = taken,
        )
        validateChosenSlot(body.slotAt, offered)?.let { throw ApiException.bad(it, it) }
        if (db.existingPendingPitchBack(pitch.key, user.key) != null) {
            throw ApiException.conflict("EXISTS", "PITCH_BACK_EXISTS")
        }
        val created = now
        val pb = db.insertPitchBack(
            PitchBackRecord(
                key = UUID.randomUUID().toString(),
                pitchId = pitch.key,
                fromUserId = user.key,
                toUserId = pitch.authorId,
                text = body.text.trim(),
                status = STATUS_PENDING,
                createdAt = created,
                expiresAt = pitchBackExpiresAt(created),
                slotAt = body.slotAt,
            ),
        )
        app.hub.send(pitch.authorId, "inbox")
        call.respond(HttpStatusCode.Created, db.toPitchBackDto(pb, user.key)!!)
    }

    get("/api/inbox") {
        val user = call.requireUser(app)
        call.respond(inboxDto(app, user))
    }

    post("/api/pitch-backs/{id}/accept") {
        val user = call.requireUser(app)
        val pb = db.pitchBackById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
        if (pb.toUserId != user.key) throw ApiException.forbidden()
        val pitch = db.pitchById(pb.pitchId) ?: throw ApiException.notFound()
        val now = app.now()
        if (!canAcceptPitchBack(pb.status, pb.expiresAt, pitch.remainingPitchBacks, now)) {
            throw ApiException.bad("EXPIRED", "PITCH_BACK_EXPIRED")
        }
        db.updatePitchBack(pb.key, patchOf { put("status", STATUS_ACCEPTED) })
        val remaining = remainingAfterAccept(pitch.remainingPitchBacks)
        db.updatePitch(pitch.key, patchOf { put("remainingPitchBacks", remaining) })
        db.insertMeet(
            MeetRecord(
                key = UUID.randomUUID().toString(),
                pitchId = pitch.key,
                pitchBackId = pb.key,
                ownerId = pitch.authorId,
                guestId = pb.fromUserId,
                status = STATUS_ACTIVE,
                createdAt = now,
                slotAt = pb.slotAt,
            ),
        )
        app.hub.sendMany(listOf(pb.fromUserId, pb.toUserId), "inbox")
        app.hub.sendMany(listOf(pb.fromUserId, pb.toUserId), "feed")
        call.respond(OkDto())
    }

    post("/api/pitch-backs/{id}/pass") {
        val user = call.requireUser(app)
        val pb = db.pitchBackById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
        if (pb.toUserId != user.key) throw ApiException.forbidden()
        if (pb.status != STATUS_PENDING) throw ApiException.bad("STATUS", "ALREADY_HANDLED")
        db.updatePitchBack(pb.key, patchOf { put("status", STATUS_PASSED) })
        app.hub.sendMany(listOf(pb.fromUserId, pb.toUserId), "inbox")
        call.respond(OkDto())
    }

    post("/api/pitch-backs/{id}/cancel") {
        val user = call.requireUser(app)
        val pb = db.pitchBackById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
        if (pb.fromUserId != user.key) throw ApiException.forbidden()
        if (pb.status != STATUS_PENDING) throw ApiException.bad("STATUS", "ALREADY_HANDLED")
        db.updatePitchBack(pb.key, patchOf { put("status", STATUS_CANCELLED) })
        app.hub.sendMany(listOf(pb.fromUserId, pb.toUserId), "inbox")
        call.respond(OkDto())
    }

    post("/api/meets/{id}/cancel") {
        val user = call.requireUser(app)
        val meet = db.meetById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
        if (meet.ownerId != user.key && meet.guestId != user.key) throw ApiException.forbidden()
        if (meet.status != STATUS_ACTIVE) throw ApiException.bad("STATUS", "MEET_INACTIVE")
        db.updateMeet(
            meet.key,
            patchOf {
                put("status", STATUS_CANCELLED)
                put("cancelledBy", user.key)
            },
        )
        app.hub.sendMany(listOf(meet.ownerId, meet.guestId), "inbox")
        call.respond(OkDto())
    }

    post("/api/meets/{id}/end") {
        val user = call.requireUser(app)
        val meet = db.meetById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
        if (meet.ownerId != user.key && meet.guestId != user.key) throw ApiException.forbidden()
        val already = meetEndedBy(meet.endedUserIds, meet.endedBy)
        if (!canEndMeet(meet.status, already, user.key)) {
            throw ApiException.bad("STATUS", "MEET_INACTIVE")
        }
        val message = call.receive<EndMeetRequest>().message.trim()
        val (next, status) = endMeetForUser(already, user.key, meet.ownerId, meet.guestId)
        db.updateMeet(
            meet.key,
            patchOf {
                put("status", status)
                putJsonArray("endedUserIds") { next.forEach { add(it) } }
                if (meet.ownerId == user.key) put("ownerFinalMessage", message)
                else put("guestFinalMessage", message)
            },
        )
        app.hub.sendMany(listOf(meet.ownerId, meet.guestId), "inbox")
        val dto = db.toMeetDto(db.meetById(meet.key)!!, user.key)!!
        call.respond(dto)
    }

    post("/api/meets/{id}/dismiss") {
        val user = call.requireUser(app)
        val meet = db.meetById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
        if (meet.ownerId != user.key && meet.guestId != user.key) throw ApiException.forbidden()
        val dismissed = (meet.dismissedBy + user.key).distinct()
        db.updateMeet(
            meet.key,
            patchOf {
                put("dismissedBy", kotlinx.serialization.json.JsonArray(dismissed.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            },
        )
        call.respond(OkDto())
    }

    post("/api/meets/{id}/no-show") {
        val user = call.requireUser(app)
        val meet = db.meetById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
        if (meet.ownerId != user.key && meet.guestId != user.key) throw ApiException.forbidden()
        val already = db.noShowByMeetAndReporter(meet.key, user.key) != null
        if (!canReportNoShow(already, meet.status)) {
            throw ApiException.bad(
                if (already) "DUPLICATE" else "STATUS",
                if (already) "NOSHOW_DUPLICATE" else "MEET_INACTIVE",
            )
        }
        val message = runCatching { call.receive<NoShowRequest>().message }.getOrDefault("").trim()
        val accused = if (meet.ownerId == user.key) meet.guestId else meet.ownerId
        db.insertNoShow(
            NoShowRecord(
                key = UUID.randomUUID().toString(),
                meetId = meet.key,
                reporterId = user.key,
                accusedId = accused,
                status = STATUS_PENDING,
                createdAt = app.now(),
                message = message,
            ),
        )
        call.respond(OkDto())
    }

    post("/api/ai/stream") {
        call.requireUser(app)
        val req = call.receive<AiStreamRequest>()
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            try {
                app.qwen.stream(req) { chunk ->
                    val payload = AppJson.encodeToString(
                        kotlinx.serialization.json.JsonObject.serializer(),
                        patchOf { put("text", chunk) },
                    )
                    write("data: $payload\n\n")
                    flush()
                }
                write("data: {\"done\":true}\n\n")
                flush()
            } catch (e: ApiException) {
                val lang = ApiMessages.langFromAccept(call.request.headers[HttpHeaders.AcceptLanguage])
                val payload = AppJson.encodeToString(
                    ErrorBody.serializer(),
                    ErrorBody(ApiMessages.t(lang, e.message ?: e.code), e.code),
                )
                write("data: $payload\n\n")
                flush()
            }
        }
    }

    route("/api/admin") {
        get("/pending") {
            call.requireAdmin(app)
            call.respond(db.adminPending())
        }
        get("/pitches") {
            val admin = call.requireAdmin(app)
            val now = app.now()
            val items = db.pendingPitches().mapNotNull { db.toCard(it, admin.key, now, forReview = true) }
            call.respond(FeedResponse(items))
        }
        post("/pitches/{id}/approve") {
            call.requireAdmin(app)
            val pitch = db.pitchById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
            db.updatePitch(pitch.key, patchOf { put("status", STATUS_APPROVED) })
            app.hub.send(pitch.authorId, "me")
            app.hub.send(pitch.authorId, "feed")
            call.respond(OkDto())
        }
        post("/pitches/{id}/dismiss") {
            call.requireAdmin(app)
            val pitch = db.pitchById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
            db.updatePitch(pitch.key, patchOf { put("status", STATUS_DISMISSED) })
            app.hub.send(pitch.authorId, "me")
            call.respond(OkDto())
        }
        get("/verifications") {
            call.requireAdmin(app)
            call.respond(db.pendingVerifications().mapNotNull { db.toVerificationDto(it) })
        }
        post("/verifications/{id}/approve") {
            call.requireAdmin(app)
            val doc = db.verificationById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
            db.updateVerification(doc.key, patchOf { put("status", STATUS_APPROVED) })
            db.updateUser(doc.userId, patchOf { put("verified", true) })
            app.hub.send(doc.userId, "me")
            call.respond(OkDto())
        }
        post("/verifications/{id}/dismiss") {
            call.requireAdmin(app)
            val doc = db.verificationById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
            db.updateVerification(doc.key, patchOf { put("status", STATUS_DISMISSED) })
            app.hub.send(doc.userId, "me")
            call.respond(OkDto())
        }
        get("/users") {
            call.requireAdmin(app)
            call.respond(db.allUsers().map { db.toAdminUser(it) })
        }
        post("/users/{id}/promote") {
            call.requireAdmin(app)
            val target = db.userById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
            db.updateUser(target.key, patchOf { put("isAdmin", true) })
            app.hub.send(target.key, "me")
            call.respond(OkDto())
        }
        post("/users/{id}/disable") {
            val admin = call.requireAdmin(app)
            val target = db.userById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
            if (!canDisableAccount(admin.key, target.key)) throw ApiException.bad("SELF", "DISABLE_SELF")
            db.updateUser(target.key, patchOf { put("disabled", !target.disabled) })
            call.respond(OkDto())
        }
        get("/locations") {
            call.requireAdmin(app)
            call.respond(db.locationsNewest().map { it.toDto() })
        }
        post("/locations") {
            call.requireAdmin(app)
            val body = call.receive<LocationWriteRequest>()
            validateLocation(body)
            val loc = db.insertLocation(
                LocationRecord(
                    key = UUID.randomUUID().toString(),
                    name = body.name.trim(),
                    description = body.description.trim(),
                    photoUrls = body.photoUrls,
                    openingHours = body.openingHours,
                    address = body.address.trim(),
                    directionsUrl = body.directionsUrl.trim(),
                    highlights = body.highlights.map { it.trim() },
                    createdAt = app.now(),
                ),
            )
            call.respond(HttpStatusCode.Created, loc.toDto())
        }
        put("/locations/{id}") {
            call.requireAdmin(app)
            val existing = db.locationById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
            val body = call.receive<LocationWriteRequest>()
            validateLocation(body)
            val loc = db.updateLocation(
                existing.key,
                patchOf {
                    put("name", body.name.trim())
                    put("description", body.description.trim())
                    put("photoUrls", kotlinx.serialization.json.JsonArray(body.photoUrls.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    put("openingHours", AppJson.encodeToJsonElement(OpeningHours.serializer(), body.openingHours))
                    put("address", body.address.trim())
                    put("directionsUrl", body.directionsUrl.trim())
                    put("highlights", kotlinx.serialization.json.JsonArray(body.highlights.map { kotlinx.serialization.json.JsonPrimitive(it.trim()) }))
                },
            )
            call.respond(loc.toDto())
        }
        delete("/locations/{id}") {
            call.requireAdmin(app)
            db.deleteLocation(call.parameters["id"].orEmpty())
            call.respond(OkDto())
        }
        get("/location-recs") {
            call.requireAdmin(app)
            call.respond(db.pendingLocationRecs().mapNotNull { db.toLocationRecDto(it) })
        }
        post("/location-recs/{id}/dismiss") {
            call.requireAdmin(app)
            val rec = db.locationRecById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
            db.updateLocationRec(rec.key, patchOf { put("status", STATUS_DISMISSED) })
            call.respond(OkDto())
        }
        get("/no-shows") {
            call.requireAdmin(app)
            call.respond(db.pendingNoShows().mapNotNull { db.toNoShowDto(it) })
        }
        post("/no-shows/{id}/review") {
            call.requireAdmin(app)
            val doc = db.noShowById(call.parameters["id"].orEmpty()) ?: throw ApiException.notFound()
            db.updateNoShow(doc.key, patchOf { put("status", STATUS_REVIEWED) })
            call.respond(OkDto())
        }
    }
}

private fun validateLocation(body: LocationWriteRequest) {
    if (body.name.trim().length < 2) throw ApiException.bad("NAME", "NAME_REQUIRED")
    validatePhotos(body.photoUrls)?.let { throw ApiException.bad(it, it) }
    validateHighlights(body.highlights)?.let { throw ApiException.bad(it, it) }
}

private suspend fun meDto(app: AppContext, user: UserRecord): MeDto {
    val pending = if (user.isAdmin) app.db.adminPending() else null
    return MeDto(
        user = user.toPublic(self = true),
        remainingSlots = app.db.remainingSlots(user.key),
        adminPending = pending,
        verificationPending = !user.verified && app.db.pendingVerification(user.key) != null,
    )
}

private suspend fun inboxDto(app: AppContext, user: UserRecord): InboxDto {
    val incoming = app.db.incomingPitchBacks(user.key).mapNotNull { app.db.toPitchBackDto(it, user.key) }
    val outgoing = app.db.outgoingPitchBacks(user.key).mapNotNull { app.db.toPitchBackDto(it, user.key) }
    val meets = app.db.meetsForUser(user.key)
    val active = meets.filter {
        meetActiveForUser(it.status, meetEndedBy(it.endedUserIds, it.endedBy), user.key)
    }.mapNotNull { app.db.toMeetDto(it, user.key) }
    val cancelled = meets.filter {
        it.status == STATUS_CANCELLED && user.key !in it.dismissedBy
    }.mapNotNull { app.db.toMeetDto(it, user.key) }
    val finalMessages = meets.filter {
        shouldShowInboxFinalMessage(
            otherFinalMessage(user.key, it.ownerId, it.guestId, it.ownerFinalMessage, it.guestFinalMessage),
            user.key,
            it.dismissedBy,
        )
    }.mapNotNull { app.db.toMeetDto(it, user.key) }
    return InboxDto(
        finalMessages = finalMessages,
        meets = active,
        cancelledMeets = cancelled,
        incoming = incoming,
        outgoing = outgoing,
        badge = incoming.size + cancelled.size + finalMessages.size,
    )
}
