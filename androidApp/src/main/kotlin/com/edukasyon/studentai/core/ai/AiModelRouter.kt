package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.domain.model.AiModel

/**
 * Client-side model hints for the backend proxy.
 *
 * Server routing ([backend/ai/AiProvider.js]):
 * - User-selected **auto** → text uses auto; image attachments also stay on **auto**
 *   (`VISION_CAPABLE_MODELS` includes `auto`; `resolveChatModel()` does not force step).
 * - User-selected **step-3.7-flash** → text and vision use step (subject to 25 req / 10 min quota).
 *
 * UI capability tags in [JeviChatInput] show Vision on both models because auto is vision-capable.
 */
object AiModelRouter {
    /** Model slug sent to backend when the user picks a non-default chat model. */
    fun chatModelOverride(preference: AiModel): String? =
        preference.takeIf { it == AiModel.REASONING }?.slug

    @Deprecated("Use chatModelOverride", ReplaceWith("chatModelOverride(preference)"))
    fun textChatModelOverride(preference: AiModel): String? = chatModelOverride(preference)
}
