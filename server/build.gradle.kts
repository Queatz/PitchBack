plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "app.pitchback"
version = "1.0.0"
application {
    mainClass = "app.pitchback.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.jbcrypt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverSessions)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverSse)
    implementation(libs.ktor.serverCompression)
    implementation(libs.ktor.serverDefaultHeaders)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
