package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.repository.ScheduleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GetTodayScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : UseCase<Unit, List<ScheduleItem>> {
    override suspend fun execute(params: Unit): List<ScheduleItem> {
        val today = DateUtils.getTodayDayOfWeek()
        return scheduleRepository.observeByDay(today).first()
    }
}

class GetAllScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : UseCase<Unit, List<ScheduleItem>> {
    override suspend fun execute(params: Unit): List<ScheduleItem> = scheduleRepository.observeSchedule().first()
}

class GetScheduleByDayUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : UseCase<DayOfWeek, List<ScheduleItem>> {
    override suspend fun execute(params: DayOfWeek): List<ScheduleItem> = scheduleRepository.observeByDay(params).first()
}

class AddScheduleItemUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : UseCase<ScheduleItem, Unit> {
    override suspend fun execute(params: ScheduleItem): Unit = scheduleRepository.addScheduleItem(params)
}

class UpdateScheduleItemUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : UseCase<ScheduleItem, Unit> {
    override suspend fun execute(params: ScheduleItem): Unit = scheduleRepository.updateScheduleItem(params)
}

class DeleteScheduleItemUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : UseCase<String, Unit> {
    override suspend fun execute(params: String): Unit = scheduleRepository.deleteScheduleItem(params)
}

class SearchScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : UseCase<String, List<ScheduleItem>> {
    override suspend fun execute(params: String): List<ScheduleItem> = scheduleRepository.search(params).first()
}