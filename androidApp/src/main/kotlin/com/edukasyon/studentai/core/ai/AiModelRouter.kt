package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.domain.model.AiModel

/**
 * Client-side model hints for the backend proxy.
 *
 * Server routing ([backend/ai/AiProvider.js]):
 * - User-selected **auto** → text uses auto; vision (image/scan) always routes
 *   to **agnes-2.5-flash** on the server (`resolveChatModel()` / `resolveVisionModel()`).
 * - User-selected **agnes-2.5-flash** → text and vision use agnes (subject to 25 req / 10 min quota).
 *   Legacy `step-3.7-flash` slug is normalized to agnes for old clients.
 *
 * UI capability tags in [JeviChatInput] show Vision on the reasoning model; auto is text-only.
 */
object AiModelRouter {
    /** Model slug sent to backend when the user picks a non-default chat model. */
    fun chatModelOverride(preference: AiModel): String? =
        preference.takeIf { it == AiModel.REASONING }?.slug

    @Deprecated("Use chatModelOverride", ReplaceWith("chatModelOverride(preference)"))
    fun textChatModelOverride(preference: AiModel): String? = chatModelOverride(preference)
}
