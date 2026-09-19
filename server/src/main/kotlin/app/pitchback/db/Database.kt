package app.pitchback.db

import app.pitchback.api.AdminPendingDto
import app.pitchback.api.LocationDto
import app.pitchback.api.LocationRecDto
import app.pitchback.api.MeetDto
import app.pitchback.api.NoShowDto
import app.pitchback.api.OfferedSlotDto
import app.pitchback.api.PitchBackDto
import app.pitchback.api.PitchCardDto
import app.pitchback.api.TopicDto
import app.pitchback.api.UserAdminDto
import app.pitchback.api.VerificationDto
import app.pitchback.domain.meetEndedBy
import app.pitchback.domain.nextOfferedSlots
import app.pitchback.domain.normalizeAvailability
import app.pitchback.domain.otherFinalMessage
import app.pitchback.domain.STATUS_APPROVED
import app.pitchback.domain.STATUS_PENDING
import app.pitchback.domain.phonesMatch
import app.pitchback.domain.pitchIsPublic
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class Database(val arango: Arango) {

    suspend fun userCount(): Int =
        arango.queryOne<Int>("RETURN LENGTH(users)") ?: 0

    suspend fun userById(id: String): UserRecord? =
        arango.queryOne("FOR u IN users FILTER u._key == @id RETURN u", mapOf("id" to v(id)))

    suspend fun userByPhone(phone: String, exceptUserId: String? = null): UserRecord? {
        val users: List<UserRecord> = arango.queryList("FOR u IN users RETURN u")
        return users.firstOrNull { u ->
            (exceptUserId == null || u.key != exceptUserId) && phonesMatch(u.phone, phone)
        }
    }

    suspend fun insertUser(user: UserRecord): UserRecord = arango.insert("users", user)

    suspend fun updateUser(key: String, patch: JsonObject): UserRecord = arango.update("users", key, patch)

    suspend fun allUsers(): List<UserRecord> =
        arango.queryList("FOR u IN users SORT u.createdAt DESC RETURN u")

    suspend fun locationById(id: String): LocationRecord? =
        arango.queryOne("FOR l IN locations FILTER l._key == @id RETURN l", mapOf("id" to v(id)))

    suspend fun locationsNewest(): List<LocationRecord> =
        arango.queryList("FOR l IN locations SORT l.createdAt DESC RETURN l")

    suspend fun insertLocation(doc: LocationRecord): LocationRecord = arango.insert("locations", doc)

    suspend fun updateLocation(key: String, patch: JsonObject): LocationRecord =
        arango.update("locations", key, patch)

    suspend fun deleteLocation(key: String) = arango.remove("locations", key)

    suspend fun pitchById(id: String): PitchRecord? =
        arango.queryOne("FOR p IN pitches FILTER p._key == @id RETURN p", mapOf("id" to v(id)))

    suspend fun insertPitch(doc: PitchRecord): PitchRecord = arango.insert("pitches", doc)

    suspend fun updatePitch(key: String, patch: JsonObject): PitchRecord = arango.update("pitches", key, patch)

    suspend fun deletePitch(key: String) {
        arango.query(
            "FOR pb IN pitchBacks FILTER pb.pitchId == @id REMOVE pb IN pitchBacks",
            mapOf("id" to v(key)),
        )
        arango.remove("pitches", key)
    }

    suspend fun pitchesByAuthor(authorId: String): List<PitchRecord> =
        arango.queryList(
            "FOR p IN pitches FILTER p.authorId == @id SORT p.createdAt DESC RETURN p",
            mapOf("id" to v(authorId)),
        )

    suspend fun remainingSlots(authorId: String): Int =
        arango.queryOne(
            """
            FOR p IN pitches
              FILTER p.authorId == @id AND p.status == @status
              COLLECT AGGREGATE s = SUM(p.remainingPitchBacks)
              RETURN s
            """.trimIndent(),
            mapOf("id" to v(authorId), "status" to v(STATUS_APPROVED)),
        ) ?: 0

    suspend fun pendingPitches(): List<PitchRecord> =
        arango.queryList(
            "FOR p IN pitches FILTER p.status == @s SORT p.createdAt DESC RETURN p",
            mapOf("s" to v(STATUS_PENDING)),
        )

    suspend fun feed(before: Long, limit: Int, now: Long): List<PitchRecord> =
        arango.queryList(
            """
            FOR p IN pitches
              FILTER p.status == @status
              FILTER p.remainingPitchBacks > 0
              FILTER p.availableUntil == null OR p.availableUntil > @now
              FILTER p.createdAt < @before
              SORT p.createdAt DESC
              LIMIT @limit
              RETURN p
            """.trimIndent(),
            mapOf(
                "status" to v(STATUS_APPROVED),
                "now" to v(now),
                "before" to v(before),
                "limit" to v(limit),
            ),
        )

    suspend fun topics(): List<TopicDto> =
        arango.queryList(
            """
            FOR p IN pitches
              FILTER p.status == @status AND p.topic != ""
              COLLECT topic = p.topic WITH COUNT INTO n
              SORT n DESC
              LIMIT 30
              RETURN { topic, count: n }
            """.trimIndent(),
            mapOf("status" to v(STATUS_APPROVED)),
        )

    suspend fun insertPitchBack(doc: PitchBackRecord): PitchBackRecord = arango.insert("pitchBacks", doc)

    suspend fun pitchBackById(id: String): PitchBackRecord? =
        arango.queryOne("FOR p IN pitchBacks FILTER p._key == @id RETURN p", mapOf("id" to v(id)))

    suspend fun updatePitchBack(key: String, patch: JsonObject): PitchBackRecord =
        arango.update("pitchBacks", key, patch)

    suspend fun existingPendingPitchBack(pitchId: String, fromUserId: String): PitchBackRecord? =
        arango.queryOne(
            """
            FOR p IN pitchBacks
              FILTER p.pitchId == @pitchId AND p.fromUserId == @from AND p.status == @status
              RETURN p
            """.trimIndent(),
            mapOf("pitchId" to v(pitchId), "from" to v(fromUserId), "status" to v(STATUS_PENDING)),
        )

    suspend fun incomingPitchBacks(userId: String): List<PitchBackRecord> =
        arango.queryList(
            """
            FOR p IN pitchBacks
              FILTER p.toUserId == @id AND p.status == @status
              SORT p.createdAt DESC
              RETURN p
            """.trimIndent(),
            mapOf("id" to v(userId), "status" to v(STATUS_PENDING)),
        )

    suspend fun outgoingPitchBacks(userId: String): List<PitchBackRecord> =
        arango.queryList(
            """
            FOR p IN pitchBacks
              FILTER p.fromUserId == @id AND p.status == @status
              SORT p.createdAt DESC
              RETURN p
            """.trimIndent(),
            mapOf("id" to v(userId), "status" to v(STATUS_PENDING)),
        )

    suspend fun expirePitchBacks(now: Long): List<PitchBackRecord> =
        arango.queryList(
            """
            FOR p IN pitchBacks
              FILTER p.status == @status AND p.expiresAt <= @now
              UPDATE p WITH { status: "expired" } IN pitchBacks
              RETURN NEW
            """.trimIndent(),
            mapOf("status" to v(STATUS_PENDING), "now" to v(now)),
        )

    suspend fun insertMeet(doc: MeetRecord): MeetRecord = arango.insert("meets", doc)

    suspend fun meetById(id: String): MeetRecord? =
        arango.queryOne("FOR m IN meets FILTER m._key == @id RETURN m", mapOf("id" to v(id)))

    suspend fun updateMeet(key: String, patch: JsonObject): MeetRecord = arango.update("meets", key, patch)

    suspend fun meetsForUser(userId: String): List<MeetRecord> =
        arango.queryList(
            """
            FOR m IN meets
              FILTER m.ownerId == @id OR m.guestId == @id
              SORT m.createdAt DESC
              RETURN m
            """.trimIndent(),
            mapOf("id" to v(userId)),
        )

    suspend fun insertVerification(doc: VerificationRecord): VerificationRecord =
        arango.insert("verifications", doc)

    suspend fun pendingVerification(userId: String): VerificationRecord? =
        arango.queryOne(
            """
            FOR v IN verifications
              FILTER v.userId == @id AND v.status == @status
              SORT v.createdAt DESC
              LIMIT 1
              RETURN v
            """.trimIndent(),
            mapOf("id" to v(userId), "status" to v(STATUS_PENDING)),
        )

    suspend fun pendingVerifications(): List<VerificationRecord> =
        arango.queryList(
            "FOR v IN verifications FILTER v.status == @s SORT v.createdAt DESC RETURN v",
            mapOf("s" to v(STATUS_PENDING)),
        )

    suspend fun verificationById(id: String): VerificationRecord? =
        arango.queryOne("FOR v IN verifications FILTER v._key == @id RETURN v", mapOf("id" to v(id)))

    suspend fun updateVerification(key: String, patch: JsonObject): VerificationRecord =
        arango.update("verifications", key, patch)

    suspend fun insertLocationRec(doc: LocationRecRecord): LocationRecRecord =
        arango.insert("locationRecs", doc)

    suspend fun pendingLocationRecs(): List<LocationRecRecord> =
        arango.queryList(
            "FOR r IN locationRecs FILTER r.status == @s SORT r.createdAt DESC RETURN r",
            mapOf("s" to v(STATUS_PENDING)),
        )

    suspend fun locationRecById(id: String): LocationRecRecord? =
        arango.queryOne("FOR r IN locationRecs FILTER r._key == @id RETURN r", mapOf("id" to v(id)))

    suspend fun updateLocationRec(key: String, patch: JsonObject): LocationRecRecord =
        arango.update("locationRecs", key, patch)

    suspend fun insertNoShow(doc: NoShowRecord): NoShowRecord = arango.insert("noShows", doc)

    suspend fun noShowByMeetAndReporter(meetId: String, reporterId: String): NoShowRecord? =
        arango.queryOne(
            "FOR n IN noShows FILTER n.meetId == @m AND n.reporterId == @r LIMIT 1 RETURN n",
            mapOf("m" to v(meetId), "r" to v(reporterId)),
        )

    suspend fun pendingNoShows(): List<NoShowRecord> =
        arango.queryList(
            "FOR n IN noShows FILTER n.status == @s SORT n.createdAt DESC RETURN n",
            mapOf("s" to v(STATUS_PENDING)),
        )

    suspend fun noShowById(id: String): NoShowRecord? =
        arango.queryOne("FOR n IN noShows FILTER n._key == @id RETURN n", mapOf("id" to v(id)))

    suspend fun updateNoShow(key: String, patch: JsonObject): NoShowRecord =
        arango.update("noShows", key, patch)

    suspend fun adminPending(): AdminPendingDto {
        val pitches = arango.queryOne<Int>(
            "RETURN LENGTH(FOR p IN pitches FILTER p.status == @s RETURN 1)",
            mapOf("s" to v(STATUS_PENDING)),
        ) ?: 0
        val verifications = arango.queryOne<Int>(
            "RETURN LENGTH(FOR v IN verifications FILTER v.status == @s RETURN 1)",
            mapOf("s" to v(STATUS_PENDING)),
        ) ?: 0
        val noShows = arango.queryOne<Int>(
            "RETURN LENGTH(FOR n IN noShows FILTER n.status == @s RETURN 1)",
            mapOf("s" to v(STATUS_PENDING)),
        ) ?: 0
        val recs = arango.queryOne<Int>(
            "RETURN LENGTH(FOR r IN locationRecs FILTER r.status == @s RETURN 1)",
            mapOf("s" to v(STATUS_PENDING)),
        ) ?: 0
        return AdminPendingDto(pitches, verifications, noShows, recs)
    }

    suspend fun toCard(
        pitch: PitchRecord,
        viewerId: String?,
        now: Long,
        forReview: Boolean = false,
    ): PitchCardDto? {
        val author = userById(pitch.authorId) ?: return null
        val location = locationById(pitch.locationId)
        val locationDto = location?.toDto() ?: LocationDto(id = pitch.locationId, name = "Unknown", createdAt = 0)
        if (!forReview && viewerId != pitch.authorId) {
            if (!pitchIsPublic(pitch.status, pitch.remainingPitchBacks, pitch.availableUntil, author.disabled, now)) {
                return null
            }
        }
        val availability = normalizeAvailability(pitch.availability)
        return PitchCardDto(
            id = pitch.key,
            text = pitch.text,
            photoUrls = pitch.photoUrls,
            remainingPitchBacks = pitch.remainingPitchBacks,
            topic = pitch.topic,
            allowSocialPosting = pitch.allowSocialPosting,
            availableUntil = pitch.availableUntil,
            createdAt = pitch.createdAt,
            status = pitch.status,
            author = author.toPublic(),
            location = locationDto,
            isOwner = viewerId == pitch.authorId,
            availability = availability,
            nextSlots = nextOfferedSlots(availability, now, availableUntil = pitch.availableUntil)
                .map { OfferedSlotDto(it.at, it.time) },
        )
    }

    suspend fun toPitchBackDto(pb: PitchBackRecord, viewerId: String): PitchBackDto? {
        val from = userById(pb.fromUserId) ?: return null
        val to = userById(pb.toUserId) ?: return null
        val pitch = pitchById(pb.pitchId)
        val location = pitch?.let { locationById(it.locationId) }
        return PitchBackDto(
            id = pb.key,
            pitchId = pb.pitchId,
            text = pb.text,
            status = pb.status,
            createdAt = pb.createdAt,
            expiresAt = pb.expiresAt,
            fromUser = from.toPublic(),
            toUser = to.toPublic(),
            pitchPreview = pitch?.text?.take(120) ?: "",
            locationName = location?.name ?: "",
            outgoing = pb.fromUserId == viewerId,
            slotAt = pb.slotAt,
        )
    }

    suspend fun toMeetDto(meet: MeetRecord, viewerId: String): MeetDto? {
        val otherId = if (meet.ownerId == viewerId) meet.guestId else meet.ownerId
        val other = userById(otherId) ?: return null
        val me = userById(viewerId)
        val pitch = pitchById(meet.pitchId)
        val location = pitch?.let { locationById(it.locationId) }
        val cancelledBy = meet.cancelledBy?.let { userById(it)?.name }
        return MeetDto(
            id = meet.key,
            pitchId = meet.pitchId,
            status = meet.status,
            createdAt = meet.createdAt,
            otherUser = other.toPublic(),
            pitchPreview = pitch?.text?.take(120) ?: "",
            locationName = location?.name ?: "",
            locationId = pitch?.locationId ?: "",
            allowSocialPosting = pitch?.allowSocialPosting == true,
            finalMessage = otherFinalMessage(
                viewerId,
                meet.ownerId,
                meet.guestId,
                meet.ownerFinalMessage,
                meet.guestFinalMessage,
            ),
            cancelledByName = cancelledBy,
            endedByMe = viewerId in meetEndedBy(meet.endedUserIds, meet.endedBy),
            myPhotoUrl = me?.photoUrl,
            reportedNoShow = noShowByMeetAndReporter(meet.key, viewerId) != null,
            slotAt = meet.slotAt,
        )
    }

    suspend fun toVerificationDto(doc: VerificationRecord): VerificationDto? {
        val user = userById(doc.userId) ?: return null
        return VerificationDto(doc.key, user.toPublic(), doc.zaloPhone, doc.status, doc.createdAt)
    }

    suspend fun toNoShowDto(doc: NoShowRecord): NoShowDto? {
        val reporter = userById(doc.reporterId) ?: return null
        val accused = userById(doc.accusedId) ?: return null
        val meet = meetById(doc.meetId)
        val pitch = meet?.let { pitchById(it.pitchId) }
        val location = pitch?.let { locationById(it.locationId) }
        return NoShowDto(
            id = doc.key,
            meetId = doc.meetId,
            reporter = reporter.toPublic(),
            accused = accused.toPublic(),
            status = doc.status,
            createdAt = doc.createdAt,
            message = doc.message,
            locationName = location?.name ?: "",
            pitchPreview = pitch?.text?.take(160) ?: "",
            meetCreatedAt = meet?.createdAt ?: 0,
        )
    }

    suspend fun toLocationRecDto(doc: LocationRecRecord): LocationRecDto? {
        val user = userById(doc.userId) ?: return null
        return LocationRecDto(doc.key, user.toPublic(), doc.name, doc.description, doc.address, doc.status, doc.createdAt)
    }

    fun toAdminUser(user: UserRecord) = UserAdminDto(
        id = user.key,
        name = user.name,
        phone = user.phone,
        photoUrl = user.photoUrl,
        intro = user.intro,
        verified = user.verified,
        isAdmin = user.isAdmin,
        disabled = user.disabled,
        createdAt = user.createdAt,
    )
}

fun patchOf(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
    buildJsonObject(builder)
