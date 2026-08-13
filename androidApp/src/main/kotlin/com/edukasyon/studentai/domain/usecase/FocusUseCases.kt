package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.core.ai.AiService
import com.edukasyon.studentai.core.util.FocusPlanValidator
import com.edukasyon.studentai.domain.model.FocusPlan
import com.edukasyon.studentai.domain.model.FocusPlanContext
import javax.inject.Inject

class GenerateFocusPlanUseCase @Inject constructor(
    private val aiService: AiService,
) : UseCase<FocusPlanContext, FocusPlan> {
    override suspend fun execute(params: FocusPlanContext): FocusPlan {
        val plan = aiService.generateFocusPlan(params)
        return FocusPlanValidator.validate(plan).getOrElse { error ->
            throw IllegalArgumentException(error.message ?: "Invalid focus plan from AI.")
        }
    }
}
