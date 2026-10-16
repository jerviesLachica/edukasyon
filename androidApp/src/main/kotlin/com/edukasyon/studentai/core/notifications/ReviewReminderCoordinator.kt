package com.edukasyon.studentai.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.data.local.dao.ExamDao
import com.edukasyon.studentai.data.local.dao.FlashcardDao
import com.edukasyon.studentai.data.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Daily reconciliation for study review reminders: checks the opt-in preference
 * and the due-today flashcard count, then enqueues a single one-shot
 * ReminderWorker (via ReminderScheduler) at the next ~19:00 local slot. Cancels
 * everything when the preference is off — no notification without opt-in.
 */
@HiltWorker
class ReviewReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferences: UserPreferences,
    private val flashcardDao: FlashcardDao,
    private val examDao: ExamDao,
    private val reminderScheduler: ReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val enabled = preferences.reviewReminderEnabled.first() &&
            preferences.notificationsEnabled.first()
        if (!enabled) {
            ReviewReminderCoordinator.cancel(applicationContext)
            return Result.success()
        }
        ReviewReminderCoordinator.scheduleNextNotification(
            context = applicationContext,
            preferences = preferences,
            flashcardDao = flashcardDao,
            examDao = examDao,
            reminderScheduler = reminderScheduler
        )
        return Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReviewReminderEntryPoint {
    fun userPreferences(): UserPreferences
    fun flashcardDao(): FlashcardDao
    fun examDao(): ExamDao
    fun reminderScheduler(): ReminderScheduler
}

/** Resolved urgent-exam signal that promotes the daily review reminder. */
internal data class UrgentExamNudge(
    val countdown: String,
    val message: String,
)

/**
 * Entry point the parent calls from app-start or settings-save after toggling
 * UserPreferences.reviewReminderEnabled: sync() schedules or cancels, cancel()
 * tears both work items down, status() reports the current opt-in.
 */
object ReviewReminderCoordinator {
    const val PERIODIC_WORK_NAME = "review_reminder_daily"
    const val NOTIFICATION_WORK_NAME = "review_reminder_notification"
    private const val REMINDER_HOUR_OF_DAY = 19

    suspend fun sync(context: Context) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors
            .fromApplication(appContext, ReviewReminderEntryPoint::class.java)
        val preferences = entryPoint.userPreferences()
        val enabled = preferences.reviewReminderEnabled.first() &&
            preferences.notificationsEnabled.first()
        if (!enabled) {
            cancel(appContext)
            return
        }
        scheduleNextNotification(
            context = appContext,
            preferences = preferences,
            flashcardDao = entryPoint.flashcardDao(),
            examDao = entryPoint.examDao(),
            reminderScheduler = entryPoint.reminderScheduler()
        )
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        runCatching {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(NOTIFICATION_WORK_NAME)
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }

    suspend fun status(context: Context): Boolean {
        val entryPoint = EntryPointAccessors
            .fromApplication(context.applicationContext, ReviewReminderEntryPoint::class.java)
        return entryPoint.userPreferences().reviewReminderEnabled.first()
    }

    internal suspend fun scheduleNextNotification(
        context: Context,
        preferences: UserPreferences,
        flashcardDao: FlashcardDao,
        examDao: ExamDao,
        reminderScheduler: ReminderScheduler
    ) {
        scheduleReviewNotification(context, preferences, flashcardDao, examDao, reminderScheduler)
    }

    /**
     * Pure decision helper (unit-testable): urgent when the exam is within 3
     * days AND readiness shows >40% of cards due (or an empty deck with an
     * imminent exam). Offline signals only; caller respects opt-in.
     */
    fun isUrgentExamNudge(
        daysUntilExam: Long,
        dueCards: Int,
        totalCards: Int,
    ): Boolean {
        if (daysUntilExam > 3) return false
        if (totalCards <= 0) return true
        return dueCards.toDouble() / totalCards > 0.4
    }

    /**
     * Resolves the next upcoming exam and applies [isUrgentExamNudge] against
     * its linked deck (unlinked/empty deck counts as zero cards). Returns null
     * when no exam qualifies. Local signals only — no network.
     */
    internal suspend fun urgentExamNudge(
        examDao: ExamDao,
        flashcardDao: FlashcardDao,
        nowMillis: Long,
    ): UrgentExamNudge? {
        val exam = runCatching {
            examDao.observeUpcoming(DateUtils.startOfDay(nowMillis), 1).first().firstOrNull()
        }.getOrNull() ?: return null
        val days = ((DateUtils.startOfDay(exam.examDate) - DateUtils.startOfDay(nowMillis)) / 86_400_000L)
            .coerceAtLeast(0L)
        val deckId = exam.linkedDeckId
        val total = if (deckId == null) 0 else runCatching {
            flashcardDao.observeByDeck(deckId).first().size
        }.getOrDefault(0)
        val due = if (deckId == null) 0 else runCatching {
            flashcardDao.observeDueByDeck(deckId, nowMillis).first().size
        }.getOrDefault(0)
        if (!isUrgentExamNudge(days, due, total)) return null
        val countdown = when (days) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "in $days days"
        }
        val message = when {
            deckId == null ->
                "${exam.title} is $countdown — no study deck linked yet. Start prep now."
            total == 0 ->
                "${exam.title} is $countdown and its deck is empty — start prep now."
            else ->
                "${exam.title} is $countdown — $due of $total cards are due for review."
        }
        return UrgentExamNudge(countdown = countdown, message = message)
    }

    internal suspend fun scheduleReviewNotification(
        context: Context,
        preferences: UserPreferences,
        flashcardDao: FlashcardDao,
        examDao: ExamDao,
        reminderScheduler: ReminderScheduler
    ) {
        val now = System.currentTimeMillis()
        val dueCount = runCatching {
            flashcardDao.observeDueCount(now).first()
        }.getOrDefault(0)

        // P1 urgent exam nudge: an exam inside the urgent window promotes
        // today's reminder to exam-specific copy. It stays on the REVIEW
        // channel so the study-review opt-in governs scheduling and delivery
        // alike; that caller-side gate is the "respect opt-in" contract.
        val nudge = urgentExamNudge(examDao, flashcardDao, now)
        if (nudge != null) {
            reminderScheduler.scheduleReminder(
                uniqueWorkName = NOTIFICATION_WORK_NAME,
                type = ReminderType.REVIEW,
                title = "Exam ${nudge.countdown}",
                message = nudge.message,
                triggerAtMillis = nextReminderTriggerMillis(now)
            )
            return
        }

        if (dueCount <= 0) {
            reminderScheduler.cancelReminder(NOTIFICATION_WORK_NAME)
            return
        }
        reminderScheduler.scheduleReminder(
            uniqueWorkName = NOTIFICATION_WORK_NAME,
            type = ReminderType.REVIEW,
            title = "Time to review",
            message = "$dueCount ${if (dueCount == 1) "card" else "cards"} ready",
            triggerAtMillis = nextReminderTriggerMillis(now)
        )
    }

    internal fun nextReminderTriggerMillis(nowMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR_OF_DAY)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }
}
