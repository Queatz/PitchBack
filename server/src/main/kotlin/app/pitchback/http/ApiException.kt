package app.pitchback.http

import io.ktor.http.HttpStatusCode

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    message: String,
) : RuntimeException(message) {
    companion object {
        fun bad(code: String, message: String) = ApiException(HttpStatusCode.BadRequest, code, message)
        fun unauthorized(message: String = "AUTH_REQUIRED") =
            ApiException(HttpStatusCode.Unauthorized, "AUTH", message)
        fun forbidden(code: String = "FORBIDDEN", message: String = "NOT_ALLOWED") =
            ApiException(HttpStatusCode.Forbidden, code, message)
        fun notFound(message: String = "NOT_FOUND") =
            ApiException(HttpStatusCode.NotFound, "NOT_FOUND", message)
        fun conflict(code: String, message: String) =
            ApiException(HttpStatusCode.Conflict, code, message)
    }
}
