package app.pitchback.db

import app.pitchback.api.DayHours
import app.pitchback.api.LocationDto
import app.pitchback.api.LocationSummary
import app.pitchback.api.OpeningHours
import app.pitchback.api.PublicUser
import app.pitchback.api.WeeklyAvailability
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserRecord(
    @SerialName("_key") val key: String,
    val name: String,
    val phone: String,
    val passwordHash: String,
    val photoUrl: String? = null,
    val intro: String = "",
    val verified: Boolean = false,
    val isAdmin: Boolean = false,
    val disabled: Boolean = false,
    val language: String? = null,
    val createdAt: Long,
    val lastActiveAt: Long = 0,
) {
    fun toPublic(self: Boolean = false) = PublicUser(
        id = key,
        name = name,
        photoUrl = photoUrl,
        intro = intro,
        verified = verified,
        isAdmin = if (self) isAdmin else false,
        language = if (self) language else null,
        phone = if (self) phone else null,
    )
}

@Serializable
data class LocationRecord(
    @SerialName("_key") val key: String,
    val name: String,
    val description: String = "",
    val photoUrls: List<String> = emptyList(),
    val openingHours: OpeningHours = OpeningHours(),
    val address: String = "",
    val directionsUrl: String = "",
    val highlights: List<String> = emptyList(),
    val createdAt: Long,
) {
    fun toDto() = LocationDto(
        id = key,
        name = name,
        description = description,
        photoUrls = photoUrls,
        openingHours = openingHours,
        address = address,
        directionsUrl = directionsUrl,
        highlights = highlights,
        createdAt = createdAt,
    )

    fun toSummary() = LocationSummary(
        id = key,
        name = name,
        address = address,
        photoUrls = photoUrls,
    )
}

@Serializable
data class PitchRecord(
    @SerialName("_key") val key: String,
    val authorId: String,
    val text: String,
    val photoUrls: List<String>,
    val remainingPitchBacks: Int,
    val availableUntil: Long? = null,
    val topic: String = "",
    val locationId: String,
    val allowSocialPosting: Boolean = true,
    val status: String,
    val createdAt: Long,
    val availability: WeeklyAvailability = WeeklyAvailability(),
)

@Serializable
data class PitchBackRecord(
    @SerialName("_key") val key: String,
    val pitchId: String,
    val fromUserId: String,
    val toUserId: String,
    val text: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long,
    val slotAt: Long? = null,
)

@Serializable
data class MeetRecord(
    @SerialName("_key") val key: String,
    val pitchId: String,
    val pitchBackId: String,
    val ownerId: String,
    val guestId: String,
    val status: String,
    val cancelledBy: String? = null,
    val endedBy: String? = null,
    val endedUserIds: List<String> = emptyList(),
    val finalMessage: String? = null,
    val ownerFinalMessage: String? = null,
    val guestFinalMessage: String? = null,
    val dismissedBy: List<String> = emptyList(),
    val createdAt: Long,
    val slotAt: Long? = null,
)

@Serializable
data class VerificationRecord(
    @SerialName("_key") val key: String,
    val userId: String,
    val zaloPhone: String,
    val status: String,
    val createdAt: Long,
)

@Serializable
data class LocationRecRecord(
    @SerialName("_key") val key: String,
    val userId: String,
    val name: String,
    val description: String = "",
    val address: String = "",
    val status: String,
    val createdAt: Long,
)

@Serializable
data class NoShowRecord(
    @SerialName("_key") val key: String,
    val meetId: String,
    val reporterId: String,
    val accusedId: String,
    val status: String,
    val createdAt: Long,
    val message: String = "",
)

@Serializable
data class SessionRecord(
    @SerialName("_key") val key: String,
    val value: String,
    val updatedAt: Long,
    val expiresAt: Long,
)

@Serializable
data class ActivityEventRecord(
    @SerialName("_key") val key: String,
    val type: String,
    val actorUserId: String,
    val targetUserId: String? = null,
    val entityId: String? = null,
    val entityType: String? = null,
    val summary: String = "",
    val createdAt: Long,
)

fun defaultOpeningHours() = OpeningHours(
    mon = DayHours(),
    tue = DayHours(),
    wed = DayHours(),
    thu = DayHours(),
    fri = DayHours(),
    sat = DayHours(),
    sun = DayHours(),
)
