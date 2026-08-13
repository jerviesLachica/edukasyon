package com.edukasyon.studentai.widget

import com.edukasyon.studentai.data.local.dao.CalendarEventDao
import com.edukasyon.studentai.data.local.dao.ScheduleDao
import com.edukasyon.studentai.data.local.dao.TaskDao
import com.edukasyon.studentai.data.preferences.UserPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun taskDao(): TaskDao
    fun scheduleDao(): ScheduleDao
    fun calendarEventDao(): CalendarEventDao
    fun userPreferences(): UserPreferences
}
