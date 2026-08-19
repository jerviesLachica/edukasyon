package com.edukasyon.studentai.core.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val mandatoryUpdate: Boolean = false,
)