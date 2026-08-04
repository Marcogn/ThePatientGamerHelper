package com.marcogn.thepatientgamerhelper.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marcogn.thepatientgamerhelper.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFieldFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * A button that opens a Material date picker. When [clearable] is true, the dialog offers a
 * "Rimuovi" action that sets the date back to null; otherwise it only confirms/cancels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Text(date?.format(dateFieldFormatter) ?: label)
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    onDateChange(millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    showPicker = false
                }) { Text(stringResource(R.string.common_date_picker_confirm)) }
            },
            dismissButton = {
                if (clearable) {
                    TextButton(onClick = {
                        onDateChange(null)
                        showPicker = false
                    }) { Text(stringResource(R.string.common_date_picker_remove)) }
                } else {
                    TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
