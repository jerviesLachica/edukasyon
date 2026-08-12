package com.edukasyon.studentai.domain.usecase

interface UseCase<in Params, Result> {
    suspend fun execute(params: Params): Result
}