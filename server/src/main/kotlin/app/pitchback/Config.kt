package app.pitchback

import java.io.File
import java.security.MessageDigest

data class AppConfig(
    val env: String,
    val port: Int,
    val arangoUrl: String,
    val arangoUser: String,
    val arangoPassword: String,
    val arangoDatabase: String,
    val dashscopeKey: String,
    val dashscopeBase: String,
    val qwenModel: String,
    val sessionSecret: ByteArray,
    val uploadDir: File,
    val isProd: Boolean,
    val publicBaseUrl: String,
) {
    companion object {
        fun load(): AppConfig {
            val env = Env
            env.load()
            val environment = env["APP_ENV", "dev"]
            val upload = File(env["UPLOAD_DIR", "./data/uploads"])
            upload.mkdirs()
            val secretRaw = env["SESSION_SECRET", "dev-secret-change-me"]
            val secret = MessageDigest.getInstance("SHA-256").digest(secretRaw.toByteArray())
            return AppConfig(
                env = environment,
                port = env["PORT", "8080"].toInt(),
                arangoUrl = env["ARANGO_URL", "http://127.0.0.1:8529"].trimEnd('/'),
                arangoUser = env["ARANGO_USER", "root"],
                arangoPassword = env["ARANGO_PASSWORD", ""],
                arangoDatabase = env["ARANGO_DATABASE", "saigon_social"],
                dashscopeKey = env["DASHSCOPE_API_KEY", env["QWEN_API_KEY", ""]],
                dashscopeBase = env["DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"]
                    .trimEnd('/'),
                qwenModel = env["QWEN_MODEL", "qwen3.8-flash"],
                sessionSecret = secret,
                uploadDir = upload,
                isProd = environment == "prod" || environment == "production",
                publicBaseUrl = env["PUBLIC_BASE_URL", "http://localhost:8080"].trimEnd('/'),
            )
        }
    }
}

object Env {
    private val fileValues = mutableMapOf<String, String>()

    fun load() {
        if (fileValues.isNotEmpty()) return
        val candidates = listOf(
            File(".env"),
            File(System.getProperty("user.dir"), ".env"),
            File(System.getProperty("user.dir"), "../.env"),
            File("server/.env"),
        )
        val file = candidates.firstOrNull { it.isFile } ?: return
        file.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val eq = trimmed.indexOf('=')
            if (eq <= 0) return@forEach
            val key = trimmed.substring(0, eq).trim()
            var value = trimmed.substring(eq + 1).trim()
            if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length - 1)
            }
            fileValues[key] = value
        }
    }

    operator fun get(key: String, default: String = ""): String =
        System.getenv(key) ?: fileValues[key] ?: default
}
