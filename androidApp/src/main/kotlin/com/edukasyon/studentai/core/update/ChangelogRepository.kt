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
            versionName = "2.1.4",
            versionCode = 15,
            releaseDate = "2026-09-25",
            isMandatory = false,
            notes = listOf(
                "Feedback & Bug Reports: Direct in-app suggestion button in Profile and Settings to submit feature requests and report bugs directly to our admin team.",
                "Transparent Navigation Bar: Modern translucent bottom navigation dock with stylish gray pill backdrop and animated JEVI mascot indicator.",
                "Notification Dismiss Fix: Resolved issue where dismissed assignment and exam alerts kept recurring; dismissed items now stay properly cleared.",
                "Spam & Bot Guardrails: Anti-spam rate limiting and cooldown guardrails for community feedback.",
            ),
        ),
        ChangelogEntry(
            versionName = "2.1.3",
            versionCode = 14,
            releaseDate = "2026-09-21",
            isMandatory = false,
            notes = listOf(
                "Custom MP3 / Audio Fix: Resolved crash when selecting custom MP3 alarm tones; custom audio is now safely copied locally to survive restarts.",
                "Notification Channel Fallback: Hardened custom sound channel registration with resilient fallback to system default alert sounds.",
            ),
        ),
        ChangelogEntry(
            versionName = "2.1.2",
            versionCode = 13,
            releaseDate = "2026-09-21",
            isMandatory = false,
            notes = listOf(
                "Instant AI Cold Starts: Fully migrated backend to ultra-fast serverless infrastructure with sub-second wakeups, eliminating 50-90s cold start delays.",
                "High-Speed Multi-Provider AI: Seamless instant answers with Groq, Google Gemini, OpenRouter, and HCNsec fallback routing.",
                "Enhanced Connectivity: Streamlined backend connectivity and update checks with zero dropped requests.",
            ),
        ),
        ChangelogEntry(
            versionName = "2.1.1",
            versionCode = 12,
            releaseDate = "2026-09-19",
            isMandatory = false,
            notes = listOf(
                "Multi-Provider Free AI Engine: Zero-downtime AI study assistant backed by a multi-provider round-robin pool (Groq, Google Gemini, OpenRouter, and HCNsec).",
                "Sub-Second Responses: Ultra-fast Groq integration delivers quiz generation, flashcard creation, and study plan answers in under 800ms.",
                "Automatic Circuit-Breaker Failover: Seamless retry logic prevents rate limits and server errors with zero dropped requests.",
                "Multi-Modal Vision Load Balancing: Enhanced assignment scanning and document OCR powered by Gemini 2.5 Flash and fast vision routing.",
            ),
        ),
        ChangelogEntry(
            versionName = "2.1.0",
            versionCode = 11,
            releaseDate = "2026-09-19",
            isMandatory = false,
            notes = listOf(
                "Doc-to-Study Studio: Instant flashcards, quizzes, study guides, and mind maps from your PDFs, notes, and photos.",
                "SchedMate Red Panda Companion: Interactive study mascot with dynamic expressions and timely study motivation.",
                "Lecture Files & Decks Fix: Resolved list display clipping and subject filter limits so all your files and decks are visible.",
                "Notes Screen Redesign: Clean, modern card layout with tags, dates, and quick pill navigation between Notes, Files, and Tasks.",
                "Dashboard Deck Stats: Real-time card, due, and mastered flashcard counts on home screen deck cards.",
                "SmartStudy Fallback Engine & LaTeX math rendering in chat.",
                "Audio Overviews & Custom Alert Tones: 5 new notification tones and improved podcast-style deck overviews.",
            ),
        ),
        ChangelogEntry(
            versionName = "2.0.0",
            versionCode = 10,
            releaseDate = "2026-09-15",
            isMandatory = false,
            notes = listOf(
                "Jevi can now show real math equations, diagrams and charts right in chat — ask it to draw a graph or solve an equation.",
                "Widget photo backgrounds now actually appear on your home screen (no more design fallback), keep their aspect ratio, and clean up storage properly.",
                "Share your timetable or any JEVI deck with classmates using a private 6-char code or QR — no scanning or retyping needed.",
                "Podcast-style Audio Overviews for decks: 4 themes, A/B voice picker, and audition previews.",
                "Brand-new launcher icon that fits every phone's safe zone (Huawei/Xiaomi/Samsung), plus proper notification icon.",
                "Nav bar and chat box layout polish — the input sits snug above the tab bar with no white band.",
                "Imported schedules animate into place and skip classes you already have.",
            ),
        ),
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
