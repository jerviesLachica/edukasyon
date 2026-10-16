package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.BattlePlan
import com.edukasyon.studentai.domain.model.ExamReadinessStatus
import com.edukasyon.studentai.domain.model.SuggestedAction
import com.edukasyon.studentai.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * P1: Today's Battle Plan — composes existing repositories into one
 * structured Home decision. No new tables, no network. Pure combine().
 */
class GetTodaysBattlePlanUseCase @Inject constructor(
    private val getTodaySchedule: GetTodayScheduleUseCase,
    private val getUpcomingTasks: GetUpcomingTasksUseCase,
    private val getUpcomingExams: GetUpcomingExamsUseCase,
    private val observeExamReadinessMap: ObserveExamReadinessMapUseCase,
) {
    fun observe(): Flow<BattlePlan> = combine(
        kotlinx.coroutines.flow.flow { emit(runCatching { getTodaySchedule.execute(Unit) }.getOrDefault(emptyList())) },
        kotlinx.coroutines.flow.flow { emit(runCatching { getUpcomingTasks.execute(20) }.getOrDefault(emptyList())) },
        kotlinx.coroutines.flow.flow { emit(runCatching { getUpcomingExams.execute(5) }.getOrDefault(emptyList())) },
        observeExamReadinessMap(),
    ) { todaySchedule, tasks, exams, readinessMap ->
        decide(
            todaySchedule = todaySchedule,
            tasks = tasks,
            exams = exams,
            readinessMap = readinessMap,
            nowMillis = System.currentTimeMillis(),
        )
    }

    companion object {
        internal fun decide(
            todaySchedule: List<com.edukasyon.studentai.domain.model.ScheduleItem>,
            tasks: List<com.edukasyon.studentai.domain.model.Task>,
            exams: List<com.edukasyon.studentai.domain.model.Exam>,
            readinessMap: Map<String, com.edukasyon.studentai.domain.model.ExamReadiness>,
            nowMillis: Long,
        ): BattlePlan {
        val now = nowMillis
        val startOfToday = DateUtils.startOfDay(now)
        val endOfToday = startOfToday + 86_400_000L

        val overdue = tasks.filter {
            it.status != TaskStatus.COMPLETED && it.status != TaskStatus.ARCHIVED &&
                it.dueDate != null && it.dueDate < startOfToday
        }
        val dueToday = tasks.filter {
            it.status != TaskStatus.COMPLETED && it.status != TaskStatus.ARCHIVED &&
                it.dueDate != null && it.dueDate in startOfToday until endOfToday
        }
        val nowMinutes = java.util.Calendar.getInstance().let {
            it.timeInMillis = nowMillis
            it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE)
        }
        val sortedToday = todaySchedule.sortedBy { DateUtils.parseTimeToMinutes(it.startTime) }
        val nextClass = sortedToday.firstOrNull {
            DateUtils.parseTimeToMinutes(it.startTime) >= nowMinutes
        }
        val nextExam = exams.minByOrNull { it.examDate }
        val weakest = readinessMap.values
            .filter { it.status == ExamReadinessStatus.READY && it.readinessPercent != null }
            .sortedBy { it.readinessPercent }
            .take(3)
        val dueCards = readinessMap.values.sumOf { it.dueCards }

        val (action, headline) = when {
            nextClass != null && DateUtils.parseTimeToMinutes(nextClass.startTime) - nowMinutes <= 30 ->
                SuggestedAction.ATTEND_NEXT_CLASS to "Head to ${nextClass.subjectName} — starts ${nextClass.startTime}."
            overdue.isNotEmpty() ->
                SuggestedAction.CLEAR_OVERDUE to "Clear ${overdue.size} overdue ${if (overdue.size == 1) "task" else "tasks"} first."
            dueCards > 0 ->
                SuggestedAction.REVIEW_DUE_CARDS to "Review $dueCards due card${if (dueCards == 1) "" else "s"} to protect your streak."
            weakest.isNotEmpty() ->
                SuggestedAction.START_FOCUS_WEAK_SUBJECT to "Focus ${(weakest.first().subjectName ?: weakest.first().linkedDeckTitle ?: "your weakest deck")} — ${weakest.first().readinessPercent}% ready."
            nextExam != null ->
                SuggestedAction.PREPARE_NEXT_EXAM to "${nextExam.title} ${DateUtils.formatCountdown(nextExam.examDate)} — start prep now."
            else ->
                SuggestedAction.PLAN_DAY to "Plan your day — nothing urgent pending."
        }

        return BattlePlan(
            nextClass = nextClass,
            classesToday = sortedToday.size,
            overdueTasks = overdue,
            dueTodayTasks = dueToday,
            nextExam = nextExam,
            weakestReadiness = weakest,
            dueFlashcardsCount = dueCards,
            suggestedAction = action,
            headline = headline,
        )
        }
    }
}
