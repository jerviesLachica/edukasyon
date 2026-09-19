package com.edukasyon.studentai.di

import android.content.Context
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.data.local.dao.PageNoteCacheDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt EntryPoint for non-di construction sites (e.g. Compose remember blocks
 * in Activities/Screens that build [com.edukasyon.studentai.core.document.DocumentPipeline]
 * manually). Mirrors the PreferencesEntryPoint/FirebaseEntryPoint pattern.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DocumentEntryPoint {
    fun aiApiService(): AiApiService
    fun pageNoteCacheDao(): PageNoteCacheDao
    fun mlKitTextRecognizer(): com.edukasyon.studentai.core.mlkit.MlKitTextRecognizer
}

/** Convenience accessors used by JeviScreens' DocumentPipeline wiring. */
object HiltEntryPoint {
    fun aiApiService(context: Context): AiApiService =
        EntryPointAccessors.fromApplication(
            context.applicationContext, DocumentEntryPoint::class.java
        ).aiApiService()

    fun pageNoteCacheDao(context: Context): PageNoteCacheDao =
        EntryPointAccessors.fromApplication(
            context.applicationContext, DocumentEntryPoint::class.java
        ).pageNoteCacheDao()

    fun mlKitTextRecognizer(context: Context): com.edukasyon.studentai.core.mlkit.MlKitTextRecognizer =
        EntryPointAccessors.fromApplication(
            context.applicationContext, DocumentEntryPoint::class.java
        ).mlKitTextRecognizer()
}
