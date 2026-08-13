package com.edukasyon.studentai.core.mlkit

/** Result of on-device ML Kit text recognition. */
data class MlKitTextResult(
    val text: String,
    val blockCount: Int,
    val success: Boolean,
    val error: String? = null,
) {
    val hasUsableText: Boolean get() = success && text.length >= MIN_USABLE_TEXT_LENGTH

    companion object {
        /** Ignore very short/noisy OCR output. */
        const val MIN_USABLE_TEXT_LENGTH = 12
    }
}
