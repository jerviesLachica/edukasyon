package com.edukasyon.studentai.core.update

data class ChangelogEntry(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val notes: List<String>,
    val isMandatory: Boolean = false,
)

object ChangelogRepository {
    val changelog = listOf(
        ChangelogEntry(
            versionName = "1.2.6",
            versionCode = 9,
            releaseDate = "2026-08-31",
            isMandatory = false,
            notes = listOf(
                "Fixed the in-app update downloader — GitHub release URLs are now allowed.",
                "Includes all v1.2.5 improvements: instant widget loading and faster schedule scanning.",
            ),
        ),
        ChangelogEntry(
            versionName = "1.2.5",
            versionCode = 8,
            releaseDate = "2026-08-31",
            isMandatory = false,
            notes = listOf(
                "Instant widget loading — the home-screen widget refreshes without opening the app.",
                "Schedule scanning is now up to 60% faster.",
            ),
        ),
        ChangelogEntry(
            versionName = "1.2.4",
            versionCode = 7,
            releaseDate = "2026-08-30",
            isMandatory = false,
            notes = listOf(
                "Schedule scans now surface the real backend error instead of a generic \"unreadable\" message.",
                "JSON parsing hardened for reasoning-model output — fewer scan failures.",
            ),
        ),
        ChangelogEntry(
            versionName = "1.2.3",
            versionCode = 7,
            releaseDate = "2026-08-28",
            isMandatory = false,
            notes = listOf(
                "Fixed build issues and corrected the update download URL and checksum.",
            ),
        ),
        ChangelogEntry(
            versionName = "1.2.2",
            versionCode = 6,
            releaseDate = "2026-08-24",
            isMandatory = false,
            notes = listOf(
                "Fixed schedule scanning sometimes returning no results for photos with tricky time or day formats.",
                "Schedule scans now read fine print more reliably — images are analyzed at higher quality.",
                "Home screen widget backgrounds now render faster when the app starts fresh.",
                "The Features Guide now shows which release each new feature arrived in (New in 1.2).",
            ),
        ),
        ChangelogEntry(
            versionName = "1.2.1",
            versionCode = 4,
            releaseDate = "2026-08-23",
            isMandatory = false,
            notes = listOf(
                "New onboarding gate: fresh installs now set up cloud sync from first launch.",
                "Returning users' saved profiles are restored from the cloud instead of reset.",
                "Added an in-app changelog screen with full release history.",
                "New home screen widget promo card with one-tap Add to Home Screen.",
            ),
        ),
        ChangelogEntry(
            versionName = "1.2.0",
            versionCode = 3,
            releaseDate = "2026-08-23",
            isMandatory = false,
            notes = listOf(
                "Redesigned schedule scanner with improved camera UI and scan frame guide.",
                "Fixed Google Sign-In error handling with clearer messages.",
                "Added instant in-app update notifications via FCM topic broadcast.",
                "Improved download landing page with better version metadata.",
            ),
        ),
        ChangelogEntry(
            versionName = "1.1.0",
            versionCode = 2,
            releaseDate = "2026-08-19",
            isMandatory = false,
            notes = listOf(
                "Introduced update check and download flow from the landing page.",
                "Added widget boot receiver and periodic refresh support.",
                "Improved update notification service and backend broadcast endpoint.",
            ),
        ),
        ChangelogEntry(
            versionName = "1.0.0",
            versionCode = 1,
            releaseDate = "2026-08-15",
            isMandatory = false,
            notes = listOf(
                "Initial public release of SchedMate.",
                "Class schedule, task tracker, notes, GPA calculator, and offline AI tutor.",
            ),
        ),
    )
}
