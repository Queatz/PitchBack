package app.pitchback

import java.io.File
import java.security.MessageDigest

data class CorsOrigin(
    val host: String,
    val schemes: List<String>,
    val subDomains: List<String> = emptyList(),
)

data class AppConfig(
    val env: String,
    val port: Int,
    val host: String,
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
    val corsOrigins: List<CorsOrigin>,
) {
    companion object {
        fun load(): AppConfig {
            val env = Env
            env.load()
            val environment = env["APP_ENV", "dev"]
            val isProd = environment == "prod" || environment == "production"
            val upload = File(env["UPLOAD_DIR", "./data/uploads"])
            upload.mkdirs()
            val secretRaw = env["SESSION_SECRET", "dev-secret-change-me"]
            val secret = MessageDigest.getInstance("SHA-256").digest(secretRaw.toByteArray())
            val publicBaseUrl = env["PUBLIC_BASE_URL", "http://localhost:8080"].trimEnd('/')
            return AppConfig(
                env = environment,
                port = env["PORT", "8080"].toInt(),
                host = env["HOST", if (isProd) "127.0.0.1" else "0.0.0.0"],
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
                isProd = isProd,
                publicBaseUrl = publicBaseUrl,
                corsOrigins = parseCorsOrigins(publicBaseUrl, env["CORS_HOSTS"]),
            )
        }
    }
}

fun parseCorsOrigins(publicBaseUrl: String, extraHosts: String = ""): List<CorsOrigin> {
    data class Acc(val schemes: MutableSet<String> = linkedSetOf(), val subDomains: MutableSet<String> = linkedSetOf())

    val map = linkedMapOf<String, Acc>()

    fun add(host: String, scheme: String, subDomains: List<String> = emptyList()) {
        val h = host.trim().trimEnd('.')
        if (h.isEmpty()) return
        val acc = map.getOrPut(h) { Acc() }
        acc.schemes += scheme
        acc.subDomains += subDomains
    }

    fun addUrl(raw: String) {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return
        val scheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> "https"
            trimmed.startsWith("http://", ignoreCase = true) -> "http"
            else -> null
        }
        val rest = if (scheme != null) trimmed.substringAfter("://") else trimmed
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').trim()
        if (host.isEmpty()) return
        val schemes = if (scheme != null) listOf(scheme) else listOf("https", "http")
        val isLocal = host.startsWith("localhost", ignoreCase = true) ||
            host.startsWith("127.0.0.1") ||
            host.contains(':')
        val subs = if (!isLocal && !host.startsWith("www.", ignoreCase = true)) listOf("www") else emptyList()
        schemes.forEach { add(host, it, subs) }
    }

    listOf("localhost:8081", "127.0.0.1:8081", "localhost:8080", "127.0.0.1:8080").forEach { h ->
        add(h, "http")
        add(h, "https")
    }
    addUrl(publicBaseUrl)
    extraHosts.split(',').forEach { addUrl(it) }
    return map.map { (host, acc) -> CorsOrigin(host, acc.schemes.toList(), acc.subDomains.toList()) }
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
