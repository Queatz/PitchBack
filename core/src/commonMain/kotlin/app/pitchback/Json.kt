package app.pitchback

import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}
