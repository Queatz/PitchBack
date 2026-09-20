package app.pitchback.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorBody(val error: String, val code: String = "ERROR")

@Serializable
data class PublicUser(
    val id: String,
    val name: String,
    val photoUrl: String? = null,
    val intro: String = "",
    val verified: Boolean = false,
    val isAdmin: Boolean = false,
    val language: String? = null,
    val phone: String? = null,
)

@Serializable
data class MeDto(
    val user: PublicUser,
    val remainingSlots: Int = 0,
    val adminPending: AdminPendingDto? = null,
    val verificationPending: Boolean = false,
)

@Serializable
data class AdminPendingDto(
    val pitches: Int = 0,
    val verifications: Int = 0,
    val noShows: Int = 0,
    val locationRecs: Int = 0,
)

@Serializable
data class AdminStatsDto(
    val activeUsersToday: Int = 0,
    val activeUsersWeek: Int = 0,
    val activeUsersMonth: Int = 0,
    val users: Int = 0,
    val pitches: Int = 0,
    val locations: Int = 0,
    val activeMeets: Int = 0,
)

@Serializable
data class AdminEventDto(
    val id: String,
    val type: String,
    val createdAt: Long,
    val actor: PublicUser? = null,
    val target: PublicUser? = null,
    val entityId: String? = null,
    val entityType: String? = null,
    val summary: String = "",
)

@Serializable
data class AdminEventsResponse(
    val items: List<AdminEventDto>,
    val nextBefore: Long? = null,
)

@Serializable
data class DayHours(
    val closed: Boolean = false,
    val open: String? = "09:00",
    val close: String? = "21:00",
)

@Serializable
data class OpeningHours(
    val mon: DayHours = DayHours(),
    val tue: DayHours = DayHours(),
    val wed: DayHours = DayHours(),
    val thu: DayHours = DayHours(),
    val fri: DayHours = DayHours(),
    val sat: DayHours = DayHours(),
    val sun: DayHours = DayHours(),
)

@Serializable
data class WeeklyAvailability(
    val mon: List<String> = emptyList(),
    val tue: List<String> = emptyList(),
    val wed: List<String> = emptyList(),
    val thu: List<String> = emptyList(),
    val fri: List<String> = emptyList(),
    val sat: List<String> = emptyList(),
    val sun: List<String> = emptyList(),
)

@Serializable
data class OfferedSlotDto(
    val at: Long,
    val time: String = "",
)

@Serializable
data class LocationSummary(
    val id: String,
    val name: String,
    val address: String = "",
    val photoUrls: List<String> = emptyList(),
)

@Serializable
data class LocationDto(
    val id: String,
    val name: String,
    val description: String = "",
    val photoUrls: List<String> = emptyList(),
    val openingHours: OpeningHours = OpeningHours(),
    val address: String = "",
    val directionsUrl: String = "",
    val highlights: List<String> = emptyList(),
    val createdAt: Long,
)

@Serializable
data class PitchCardDto(
    val id: String,
    val text: String,
    val photoUrls: List<String>,
    val remainingPitchBacks: Int,
    val topic: String = "",
    val allowSocialPosting: Boolean = true,
    val availableUntil: Long? = null,
    val createdAt: Long,
    val status: String = "approved",
    val author: PublicUser,
    val location: LocationDto,
    val isOwner: Boolean = false,
    val availability: WeeklyAvailability = WeeklyAvailability(),
    val nextSlots: List<OfferedSlotDto> = emptyList(),
)

@Serializable
data class FeedResponse(
    val items: List<PitchCardDto>,
    val nextBefore: Long? = null,
)

@Serializable
data class RegisterRequest(
    val name: String,
    val phone: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val phone: String,
    val password: String,
)

@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val intro: String? = null,
    val photoUrl: String? = null,
    val language: String? = null,
    val phone: String? = null,
)

@Serializable
data class CreatePitchRequest(
    val text: String,
    val photoUrls: List<String>,
    val remainingPitchBacks: Int = 3,
    val availableUntil: Long? = null,
    val topic: String = "",
    val locationId: String,
    val allowSocialPosting: Boolean = true,
    val recommendLocationName: String? = null,
    val recommendLocationNote: String? = null,
    val availability: WeeklyAvailability = WeeklyAvailability(),
)

@Serializable
data class UpdateRemainingRequest(val remainingPitchBacks: Int)

@Serializable
data class PitchBackRequest(val text: String, val slotAt: Long? = null)

@Serializable
data class EndMeetRequest(val message: String = "")

@Serializable
data class NoShowRequest(val message: String = "")

@Serializable
data class VerificationRequestBody(val zaloPhone: String)

@Serializable
data class LocationWriteRequest(
    val name: String,
    val description: String = "",
    val photoUrls: List<String> = emptyList(),
    val openingHours: OpeningHours = OpeningHours(),
    val address: String = "",
    val directionsUrl: String = "",
    val highlights: List<String> = listOf("", "", ""),
)

@Serializable
data class RecommendLocationRequest(
    val name: String,
    val description: String = "",
    val address: String = "",
)

@Serializable
data class UploadResponse(val url: String)

@Serializable
data class TopicDto(val topic: String, val count: Int)

@Serializable
data class PitchBackDto(
    val id: String,
    val pitchId: String,
    val text: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long,
    val fromUser: PublicUser,
    val toUser: PublicUser,
    val pitchPreview: String,
    val locationName: String = "",
    val outgoing: Boolean = false,
    val slotAt: Long? = null,
)

@Serializable
data class MeetDto(
    val id: String,
    val pitchId: String,
    val status: String,
    val createdAt: Long,
    val otherUser: PublicUser,
    val pitchPreview: String,
    val locationName: String = "",
    val locationId: String = "",
    val allowSocialPosting: Boolean = false,
    val finalMessage: String? = null,
    val cancelledByName: String? = null,
    val endedByMe: Boolean = false,
    val myPhotoUrl: String? = null,
    val reportedNoShow: Boolean = false,
    val slotAt: Long? = null,
)

@Serializable
data class InboxDto(
    val finalMessages: List<MeetDto> = emptyList(),
    val meets: List<MeetDto> = emptyList(),
    val cancelledMeets: List<MeetDto> = emptyList(),
    val incoming: List<PitchBackDto> = emptyList(),
    val outgoing: List<PitchBackDto> = emptyList(),
    val badge: Int = 0,
)

@Serializable
data class AiStreamRequest(
    val kind: String,
    val extra: String = "",
    val language: String = "en",
    val existing: String = "",
    val starter: String = "",
)

@Serializable
data class UserAdminDto(
    val id: String,
    val name: String,
    val phone: String,
    val photoUrl: String? = null,
    val intro: String = "",
    val verified: Boolean = false,
    val isAdmin: Boolean = false,
    val disabled: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class VerificationDto(
    val id: String,
    val user: PublicUser,
    val zaloPhone: String,
    val status: String,
    val createdAt: Long,
)

@Serializable
data class NoShowDto(
    val id: String,
    val meetId: String,
    val reporter: PublicUser,
    val accused: PublicUser,
    val status: String,
    val createdAt: Long,
    val message: String = "",
    val locationName: String = "",
    val pitchPreview: String = "",
    val meetCreatedAt: Long = 0,
)

@Serializable
data class LocationRecDto(
    val id: String,
    val user: PublicUser,
    val name: String,
    val description: String = "",
    val address: String = "",
    val status: String,
    val createdAt: Long,
)

@Serializable
data class AdminPitchesDto(val items: List<PitchCardDto>)

@Serializable
data class OkDto(val ok: Boolean = true)
