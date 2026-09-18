package app.pitchback.auth

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(val userId: String)
