package com.edukasyon.studentai.util

import kotlin.random.Random

object IdGenerator {
    fun newId(): String = buildString {
        repeat(4) {
            if (isNotEmpty()) append('-')
            append(Random.nextLong().toULong().toString(16).takeLast(8))
        }
    }
}

expect fun currentTimeMillis(): Long
