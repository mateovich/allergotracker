package io.github.mateovich.allergotracker.core.designsystem

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun DateTimePickerSection(
    title: String,
    timeLabel: String,
    dateButtonLabel: String,
    timeButtonLabel: String,
    selectedDate: LocalDate,
    selectedTime: LocalTime,
    sectionTag: String,
    labelTag: String,
    dateButtonTag: String,
    timeButtonTag: String,
    errorTag: String,
    error: String?,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    presetContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .testTag(sectionTag)
            .semantics {
                error?.let { message ->
                    error(message)
                }
            },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacingSmall)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(text = timeLabel, modifier = Modifier.testTag(labelTag))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacingSmall)) {
            OutlinedButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            onDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
                        },
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth
                    ).show()
                },
                modifier = Modifier.testTag(dateButtonTag)
            ) {
                Text(dateButtonLabel)
            }
            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            onTimeSelected(LocalTime.of(hourOfDay, minute))
                        },
                        selectedTime.hour,
                        selectedTime.minute,
                        true
                    ).show()
                },
                modifier = Modifier.testTag(timeButtonTag)
            ) {
                Text(timeButtonLabel)
            }
        }
        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(errorTag))
        }
        presetContent()
    }
}

private val MaterialTheme.spacingSmall: androidx.compose.ui.unit.Dp
    @Composable get() = 8.dp
