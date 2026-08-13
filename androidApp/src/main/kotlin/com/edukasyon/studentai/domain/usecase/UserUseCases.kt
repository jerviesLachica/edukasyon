package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.core.firebase.FirestoreSyncService
import com.edukasyon.studentai.domain.model.ProfileEditPolicy
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

data class UpdateProfileParams(
    val displayName: String,
    val school: String,
    val preferredStatus: String,
    val bio: String,
)

sealed class ProfileUpdateResult {
    data object Success : ProfileUpdateResult()
    data class RateLimited(val daysRemaining: Int) : ProfileUpdateResult()
    data class Error(val message: String) : ProfileUpdateResult()
}

class UpdateProfileUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val firestoreSyncService: FirestoreSyncService,
) : UseCase<UpdateProfileParams, ProfileUpdateResult> {
    override suspend fun execute(params: UpdateProfileParams): ProfileUpdateResult {
        val existing = userRepository.observeUser().first()
            ?: return ProfileUpdateResult.Error("No profile found. Complete onboarding first.")

        val now = System.currentTimeMillis()
        if (!ProfileEditPolicy.canEditProfile(now, existing.lastProfileEditAt)) {
            return ProfileUpdateResult.RateLimited(
                ProfileEditPolicy.daysUntilNextEdit(now, existing.lastProfileEditAt)
            )
        }

        val displayName = params.displayName.trim().ifBlank { "Student" }
        val school = params.school.trim()
        val preferredStatus = params.preferredStatus.trim()
        val bio = params.bio.trim().take(ProfileEditPolicy.BIO_MAX_LENGTH)

        val unchanged = existing.displayName == displayName &&
            existing.school == school &&
            existing.preferredStatus == preferredStatus &&
            existing.bio == bio
        if (unchanged) {
            return ProfileUpdateResult.Success
        }

        val updated = existing.copy(
            displayName = displayName,
            school = school,
            preferredStatus = preferredStatus,
            bio = bio,
            lastProfileEditAt = now,
        )
        userRepository.saveUser(updated)
        firestoreSyncService.syncUserProfile(updated)
        return ProfileUpdateResult.Success
    }
}