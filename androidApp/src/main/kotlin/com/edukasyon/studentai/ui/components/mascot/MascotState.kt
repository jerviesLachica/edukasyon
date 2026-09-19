package com.edukasyon.studentai.ui.components.mascot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.Calendar

/**
 * Represents the emotional or functional state of the SchedMate Red Panda mascot.
 */
enum class MascotMood(
    val rawResName: String,
    val description: String,
) {
    /** Main dashboard default state: Panda resting on the calendar. */
    Idle("mascot_idle", "SchedMate mascot idling happily on your planner"),

    /** AI work state: Typing on laptop (schedule scanning, quiz, flashcard creation). */
    Planning("mascot_planning", "SchedMate mascot busily planning and typing on laptop"),

    /** AI filler/comfy state: Sipping coffee (podcast script generation, study breaks). */
    Coffee("mascot_coffee", "SchedMate mascot sipping cozy coffee"),

    /** Success state: Hitting the big SUBMIT button when tasks or generation complete. */
    Submitting("mascot_submitting", "SchedMate mascot triumphantly hitting submit"),

    /** Motivation state: Waving the calendar flag (Planner top nav, streak achievements). */
    Motivated("mascot_motivated", "SchedMate mascot cheering with calendar flag"),

    /** Error or rest state: Sleeping peacefully curled up with Zzz. */
    Resting("mascot_resting", "SchedMate mascot resting peacefully"),

    /** Learning / study state: Mascot reading the orange calendar book. */
    Learning("mascot_learning", "SchedMate mascot reading study materials"),

    /** Time-based state: Late night study mode (9:00 PM to 4:00 AM) on the dashboard. */
    LateNight("mascot_latenight", "SchedMate mascot burning the midnight oil on laptop");

    companion object {
        /**
         * Resolves the appropriate mascot mood for the home dashboard based on the time of day.
         * Between 9:00 PM (21:00) and 4:00 AM (04:00), the mascot switches to LateNight mode.
         */
        fun resolveDashboardMood(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): MascotMood {
            return if (hour >= 21 || hour < 4) {
                LateNight
            } else {
                Idle
            }
        }
    }
}

/**
 * Remembers the current dashboard mascot mood, automatically checking current system hour.
 */
@Composable
fun rememberDashboardMascotMood(): MascotMood {
    return remember {
        MascotMood.resolveDashboardMood()
    }
}
