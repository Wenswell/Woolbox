package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.TodayTask
import com.wswensw.woolbox.record.model.TodayTaskStep
import com.wswensw.woolbox.ui.AppStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayTaskDetailsSheet(
    task: TodayTask,
    onDismiss: () -> Unit,
    onToggleStep: (TodayTaskStep, Boolean, (String?) -> Unit) -> Unit,
) {
    var busy by rememberSaveable(task.id, task.revision) { mutableStateOf(false) }
    var error by rememberSaveable(task.id, task.revision) { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = AppStyle.spacing.pageHorizontal)
                .padding(bottom = 28.dp),
        ) {
            Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.task_steps_progress, task.completedStepCount, task.steps.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(task.steps, key = TodayTaskStep::id) { step ->
                    TaskDetailStepRow(
                        step = step,
                        enabled = !busy,
                        onToggle = { completed ->
                            busy = true
                            error = null
                            onToggleStep(step, completed) { result ->
                                busy = false
                                error = result
                            }
                        },
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun TaskDetailStepRow(
    step: TodayTaskStep,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = step.isCompleted, onCheckedChange = onToggle, enabled = enabled)
        Text(
            step.title,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
