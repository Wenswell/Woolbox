package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.DailyCheckin
import com.wswensw.woolbox.record.model.TodayTask
import com.wswensw.woolbox.record.model.TodayTaskStep
import com.wswensw.woolbox.record.model.currentCheckinHour
import com.wswensw.woolbox.ui.AppStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyCheckinSheet(
    checkin: DailyCheckin,
    tasks: List<TodayTask>,
    onDismiss: () -> Unit,
    onToggleStep: (TodayTask, TodayTaskStep, Boolean, (String?) -> Unit) -> Unit,
    onCheckIn: (Int, (String?) -> Unit) -> Unit,
) {
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val hour = checkin.currentCheckinHour(System.currentTimeMillis())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = AppStyle.spacing.pageHorizontal)
                .padding(bottom = 28.dp),
        ) {
            Text(
                stringResource(R.string.hourly_checkin),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(
                    tasks.flatMap { task -> task.steps.map { task to it } },
                    key = { (_, step) -> step.id },
                ) { (task, step) ->
                    TodayCheckinStepRow(
                        task = task,
                        step = step,
                        enabled = !busy,
                        onToggle = { completed ->
                            busy = true
                            error = null
                            onToggleStep(task, step, completed) { result -> busy = false; error = result }
                        },
                    )
                }
            }
            Button(
                onClick = {
                    val value = hour ?: return@Button
                    busy = true
                    error = null
                    onCheckIn(value) { result ->
                        busy = false
                        error = result
                        if (result == null) onDismiss()
                    }
                },
                enabled = !busy && hour != null,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.check_in_now)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun TodayCheckinStepRow(
    task: TodayTask,
    step: TodayTaskStep,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = step.isCompleted, onCheckedChange = onToggle, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(step.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
