package app.pitchback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorsOriginsTest {
    @Test
    fun includesLocalDevHosts() {
        val origins = parseCorsOrigins("http://localhost:8080")
        val hosts = origins.map { it.host }
        assertTrue("localhost:8081" in hosts)
        assertTrue("127.0.0.1:8081" in hosts)
        assertTrue("localhost:8080" in hosts)
        assertTrue("127.0.0.1:8080" in hosts)
    }

    @Test
    fun includesPublicHostFromBaseUrl() {
        val origins = parseCorsOrigins("https://api.ranh.app", "https://ranh.app")
        val api = origins.first { it.host == "api.ranh.app" }
        assertEquals(listOf("https"), api.schemes)
        assertEquals(listOf("www"), api.subDomains)

        val web = origins.first { it.host == "ranh.app" }
        assertEquals(listOf("https"), web.schemes)
        assertEquals(listOf("www"), web.subDomains)
    }

    @Test
    fun extraHostsAllowSchemeLessAndAbsolute() {
        val origins = parseCorsOrigins(
            "https://ranh.app",
            "http://localhost:3000, api.example.com",
        )
        val local = origins.first { it.host == "localhost:3000" }
        assertEquals(listOf("http"), local.schemes)
        val api = origins.first { it.host == "api.example.com" }
        assertTrue("https" in api.schemes)
        assertTrue("http" in api.schemes)
        assertEquals(listOf("www"), api.subDomains)
    }
}
