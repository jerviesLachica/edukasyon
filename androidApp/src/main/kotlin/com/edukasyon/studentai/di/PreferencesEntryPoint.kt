package com.edukasyon.studentai.di

import com.edukasyon.studentai.data.preferences.UserPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesEntryPoint {
    fun userPreferences(): UserPreferences
}
