package com.wswensw.woolbox.record.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.TodoStep
import com.wswensw.woolbox.record.model.TodoTask
import com.wswensw.woolbox.ui.AppStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskProgressSheet(
    task: TodoTask,
    onDismiss: () -> Unit,
    onToggleStep: (TodoStep, Boolean, (String?) -> Unit) -> Unit,
) {
    var changingStepId by rememberSaveable(task.id) { mutableStateOf<Long?>(null) }
    var errorMessage by rememberSaveable(task.id) { mutableStateOf<String?>(null) }
    val completed = task.completedStepCount
    val progress = if (task.steps.isEmpty()) 0f else completed.toFloat() / task.steps.size.toFloat()
    val reduceMotion = prefersReducedMotion()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 260),
        label = "TaskSheetProgress",
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppStyle.spacing.pageHorizontal)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.subtasks_count,
                    task.steps.size,
                    completed,
                    task.steps.size,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(6.dp),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(task.steps, key = TodoStep::id) { step ->
                    TaskProgressRow(
                        step = step,
                        enabled = changingStepId == null,
                        onToggle = { checked ->
                            changingStepId = step.id
                            errorMessage = null
                            onToggleStep(step, checked) { error ->
                                changingStepId = null
                                errorMessage = error
                            }
                        },
                    )
                    if (step.id != task.steps.last().id) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TaskProgressRow(
    step: TodoStep,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = step.isCompleted,
            onCheckedChange = onToggle,
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                color = if (step.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            step.completedAt?.let { completedAt ->
                Text(
                    text = taskCompletedAtLabel(completedAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

internal fun taskCompletedAtLabel(epochMillis: Long): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
