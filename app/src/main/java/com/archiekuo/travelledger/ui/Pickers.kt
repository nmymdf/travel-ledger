@file:OptIn(ExperimentalMaterial3Api::class)

package com.archiekuo.travelledger.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun Long.dayToUtcMillis() = LocalDate.ofEpochDay(this).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
private fun Long.utcMillisToDay() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

/** "10/22(四)" — short enough that the picker header never wraps, even with large text. */
fun fmtShortDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).let { "${it.monthValue}/${it.dayOfMonth}(${weekday(it).removePrefix("週")})" }

/** Single-day picker with a short header. [minDay] disables earlier days (e.g. return before departure). */
@Composable
fun DayPickerDialog(
    title: String,
    initialDay: Long,
    minDay: Long? = null,
    confirmText: String = "確定",
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val selectable = remember(minDay) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = minDay == null || utcTimeMillis.utcMillisToDay() >= minDay
        }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDay.dayToUtcMillis(), selectableDates = selectable)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({ state.selectedDateMillis?.let { onPick(it.utcMillisToDay()) } ?: onDismiss() }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    ) {
        DatePicker(
            state,
            showModeToggle = false,
            title = { Text(title, Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
            headline = {
                Text(
                    state.selectedDateMillis?.let { fmtShortDate(it.utcMillisToDay()) } ?: "",
                    Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.headlineMedium,
                )
            },
        )
    }
}
