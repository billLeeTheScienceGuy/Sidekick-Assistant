package com.sidekick.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

@Composable
internal fun DateTimeChoice(label: String, value: String, zone: ZoneId, tag: String, pickerTag: String = "pick-$tag", onChange: (String) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Text(label)
        Text(value.ifBlank { "Not set" }, Modifier.testTag(tag))
        OutlinedButton(modifier = Modifier.fillMaxWidth().testTag(pickerTag), onClick = {
            val format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
            val initial = runCatching { LocalDateTime.parse(value, format) }
                .getOrElse { LocalDateTime.now(zone).plusHours(1) }
            DatePickerDialog(context, { _, year, month, day ->
                TimePickerDialog(context, { _, hour, minute ->
                    onChange(LocalDateTime.of(year, month + 1, day, hour, minute).format(format))
                }, initial.hour, initial.minute, true).show()
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }) { Text("Choose date & time", maxLines = 1) }
    }
}
