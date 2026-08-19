package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.core.util.DateUtils
import java.util.Calendar

data class PlannerScheduleInput(
    val hasDueDate: Boolean = true,
    val dueDateMillis: Long = DateUtils.tomorrowStartOfDay(),
    val dueHour: Int = 23,
    val dueMinute: Int = 59,
    val enableReminder: Boolean = true,
    val reminderCustomized: Boolean = false,
    val reminderDateMillis: Long = DateUtils.tomorrowStartOfDay(),
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0
) {
    val dueTime: String get() = DateUtils.toTimeString(dueHour, dueMinute)

    fun dueDateForSave(): Long? = if (hasDueDate) DateUtils.startOfDay(dueDateMillis) else null

    fun dueTimeForSave(): String? = if (hasDueDate) dueTime else null

    fun reminderAtForSave(): Long? {
        if (!hasDueDate) return null
        if (!enableReminder) return 0L
        if (!reminderCustomized) return null
        return DateUtils.combineDateAndTime(
            DateUtils.startOfDay(reminderDateMillis),
            DateUtils.toTimeString(reminderHour, reminderMinute)
        )
    }

    fun effectiveDueMillis(): Long? =
        DateUtils.effectiveDueMillis(dueDateForSave(), dueTimeForSave())

    companion object {
        fun default(): PlannerScheduleInput {
            val dueDate = DateUtils.tomorrowStartOfDay()
            val dueMillis = DateUtils.combineDateAndTime(dueDate, "23:59")
            val defaultReminder = DateUtils.defaultReminderAt(dueMillis)
            val reminderCal = Calendar.getInstance().apply { timeInMillis = defaultReminder }
            return PlannerScheduleInput(
                dueDateMillis = dueDate,
                reminderDateMillis = DateUtils.startOfDay(defaultReminder),
                reminderHour = reminderCal.get(Calendar.HOUR_OF_DAY),
                reminderMinute = reminderCal.get(Calendar.MINUTE)
            )
        }

        fun defaultForExam(): PlannerScheduleInput {
            val dueDate = DateUtils.tomorrowStartOfDay()
            val dueMillis = DateUtils.combineDateAndTime(dueDate, "08:00")
            val defaultReminder = DateUtils.defaultReminderAt(dueMillis)
            val reminderCal = Calendar.getInstance().apply { timeInMillis = defaultReminder }
            return PlannerScheduleInput(
                dueDateMillis = dueDate,
                dueHour = 8,
                dueMinute = 0,
                reminderDateMillis = DateUtils.startOfDay(defaultReminder),
                reminderHour = reminderCal.get(Calendar.HOUR_OF_DAY),
                reminderMinute = reminderCal.get(Calendar.MINUTE)
            )
        }

        fun fromDue(dueDate: Long?, dueTime: String?, reminderAt: Long?): PlannerScheduleInput {
            if (dueDate == null) {
                return PlannerScheduleInput(hasDueDate = false)
            }
            if (reminderAt == 0L) {
                val dueParts = dueTime?.split(":") ?: listOf("23", "59")
                return PlannerScheduleInput(
                    hasDueDate = true,
                    dueDateMillis = DateUtils.startOfDay(dueDate),
                    dueHour = dueParts.getOrNull(0)?.toIntOrNull() ?: 23,
                    dueMinute = dueParts.getOrNull(1)?.toIntOrNull() ?: 59,
                    enableReminder = false
                )
            }
            val dueParts = dueTime?.split(":") ?: listOf("23", "59")
            val dueHour = dueParts.getOrNull(0)?.toIntOrNull() ?: 23
            val dueMinute = dueParts.getOrNull(1)?.toIntOrNull() ?: 59
            val dueMillis = DateUtils.effectiveDueMillis(dueDate, dueTime) ?: dueDate
            val reminderMillis = reminderAt ?: DateUtils.defaultReminderAt(dueMillis)
            val reminderCal = Calendar.getInstance().apply { timeInMillis = reminderMillis }
            return PlannerScheduleInput(
                hasDueDate = true,
                dueDateMillis = DateUtils.startOfDay(dueDate),
                dueHour = dueHour,
                dueMinute = dueMinute,
                enableReminder = true,
                reminderCustomized = reminderAt != null,
                reminderDateMillis = DateUtils.startOfDay(reminderMillis),
                reminderHour = reminderCal.get(Calendar.HOUR_OF_DAY),
                reminderMinute = reminderCal.get(Calendar.MINUTE)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScheduleFields(
    schedule: PlannerScheduleInput,
    onScheduleChange: (PlannerScheduleInput) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showDueTimePicker by remember { mutableStateOf(false) }
    var showReminderDatePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }

    fun applyDefaultReminder(input: PlannerScheduleInput): PlannerScheduleInput {
        val dueMillis = input.effectiveDueMillis() ?: return input
        val defaultReminder = DateUtils.defaultReminderAt(dueMillis)
        val cal = Calendar.getInstance().apply { timeInMillis = defaultReminder }
        return input.copy(
            reminderDateMillis = DateUtils.startOfDay(defaultReminder),
            reminderHour = cal.get(Calendar.HOUR_OF_DAY),
            reminderMinute = cal.get(Calendar.MINUTE),
            reminderCustomized = false
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Due date & time", style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = schedule.hasDueDate,
                onCheckedChange = { enabled ->
                    onScheduleChange(
                        if (enabled) applyDefaultReminder(PlannerScheduleInput.default())
                        else schedule.copy(hasDueDate = false, enableReminder = false)
                    )
                }
            )
        }

        if (schedule.hasDueDate) {
            OutlinedButton(
                onClick = { showDueDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Text(
                    DateUtils.formatFullDate(schedule.dueDateMillis),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            OutlinedButton(
                onClick = { showDueTimePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null)
                Text(
                    DateUtils.formatTime12h(schedule.dueTime),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Remind me", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = schedule.enableReminder,
                    onCheckedChange = { enabled ->
                        onScheduleChange(
                            if (enabled) applyDefaultReminder(schedule.copy(enableReminder = true))
                            else schedule.copy(enableReminder = false)
                        )
                    }
                )
            }

            if (schedule.enableReminder) {
                OutlinedButton(
                    onClick = { showReminderDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Text(
                        DateUtils.formatFullDate(schedule.reminderDateMillis),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                OutlinedButton(
                    onClick = { showReminderTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null)
                    Text(
                        DateUtils.formatTime12h(DateUtils.toTimeString(schedule.reminderHour, schedule.reminderMinute)),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    text = if (schedule.reminderCustomized) {
                        "Custom reminder: ${DateUtils.formatReminderAt(schedule.reminderAtForSave()!!)}"
                    } else {
                        "Default: 1 day before due at 9:00 AM"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDueDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = schedule.dueDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        val updated = schedule.copy(dueDateMillis = DateUtils.startOfDay(selected))
                        onScheduleChange(
                            if (updated.reminderCustomized) updated
                            else applyDefaultReminder(updated)
                        )
                    }
                    showDueDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDueDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showDueTimePicker) {
        val state = rememberTimePickerState(initialHour = schedule.dueHour, initialMinute = schedule.dueMinute)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDueTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val updated = schedule.copy(dueHour = state.hour, dueMinute = state.minute)
                    onScheduleChange(
                        if (updated.reminderCustomized) updated
                        else applyDefaultReminder(updated)
                    )
                    showDueTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDueTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) }
        )
    }

    if (showReminderDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = schedule.reminderDateMillis)
        DatePickerDialog(
            onDismissRequest = { showReminderDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        onScheduleChange(
                            schedule.copy(
                                reminderDateMillis = DateUtils.startOfDay(selected),
                                reminderCustomized = true
                            )
                        )
                    }
                    showReminderDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showReminderTimePicker) {
        val state = rememberTimePickerState(
            initialHour = schedule.reminderHour,
            initialMinute = schedule.reminderMinute
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReminderTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onScheduleChange(
                        schedule.copy(
                            reminderHour = state.hour,
                            reminderMinute = state.minute,
                            reminderCustomized = true
                        )
                    )
                    showReminderTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showReminderTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) }
        )
    }
}
