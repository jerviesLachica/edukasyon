package com.edukasyon.studentai.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createPlatformHttpClient(): HttpClient

fun createHttpClient(): HttpClient = createPlatformHttpClient().config {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            },
        )
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                println("SchedMate-HTTP: $message")
            }
        }
        level = LogLevel.INFO
    }
}
