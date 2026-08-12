package com.edukasyon.studentai.di

import com.edukasyon.studentai.core.firebase.FirebaseAuthManager
import com.edukasyon.studentai.core.firebase.FirestoreSyncService
import com.edukasyon.studentai.data.preferences.UserPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FirebaseEntryPoint {
    fun firebaseAuthManager(): FirebaseAuthManager
    fun firestoreSyncService(): FirestoreSyncService
    fun userPreferences(): UserPreferences
}
