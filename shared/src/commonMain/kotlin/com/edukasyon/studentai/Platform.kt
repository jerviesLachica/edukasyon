package com.edukasyon.studentai

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
