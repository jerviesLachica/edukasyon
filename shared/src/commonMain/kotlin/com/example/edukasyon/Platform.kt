package com.example.edukasyon

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform