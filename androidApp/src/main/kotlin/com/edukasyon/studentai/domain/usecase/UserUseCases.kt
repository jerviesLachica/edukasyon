package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.domain.model.UserProfile
import kotlinx.coroutines.flow.first
import com.edukasyon.studentai.domain.repository.UserRepository
import javax.inject.Inject

class GetUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) : UseCase<Unit, UserProfile?> {
    override suspend fun execute(params: Unit): UserProfile? = userRepository.observeUser().first()
}

class SaveUserUseCase @Inject constructor(
    private val userRepository: UserRepository
) : UseCase<UserProfile, Unit> {
    override suspend fun execute(params: UserProfile): Unit = userRepository.saveUser(params)
}