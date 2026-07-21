package com.wswensw.template.record.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.ui.TemplateDivider
import com.wswensw.template.ui.TemplateListItem
import com.wswensw.template.ui.TemplatePrimaryAction
import com.wswensw.template.ui.TemplateSection
import com.wswensw.template.ui.TemplateStyle
import java.util.Locale

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    onManage: () -> Unit,
    onCreateItem: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    showRecordSheet: Boolean,
    onDismissRecordSheet: () -> Unit,
    onRecord: (RecordItem, Double, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TemplateStyle.spacing.pageHorizontal,
                        end = TemplateStyle.spacing.pageHorizontal,
                        top = TemplateStyle.spacing.pageTop,
                    ),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onManage) {
                    Text("Manage")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        when {
            state.isLoading -> item {
                CircularProgressIndicator(modifier = Modifier.padding(48.dp))
            }
            state.error != null -> item {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
                TemplatePrimaryAction(
                    label = if (state.connection.isConfigured) "Retry" else "Configure connection",
                    iconRes = R.drawable.ic_records_24,
                    enabled = true,
                    onClick = if (state.connection.isConfigured) onRetry else onConfigureConnection,
                )
            }
            state.activeItems.isEmpty() -> {
                item {
                    Text(
                        text = "Create a record item before recording.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    TemplatePrimaryAction(
                        label = "Create record item",
                        iconRes = R.drawable.ic_records_24,
                        enabled = true,
                        onClick = onCreateItem,
                    )
                }
            }

            else -> {
                if (state.activeItems.any { it.goalValue != null && it.goalPeriod != null }) {
                    item {
                        GoalProgressSection(items = state.activeItems, values = state.goalValues)
                    }
                }
                item {
                    AnimatedContent(
                        targetState = state.entries,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(90)) },
                        label = "TodayRecords",
                    ) { entries ->
                        TemplateSection(title = "Today") {
                            if (entries.isEmpty()) {
                                TemplateListItem(title = "No records today", subtitle = "Use Record to add the first value.")
                            } else {
                                entries.forEachIndexed { index, entry ->
                                    TemplateListItem(
                                        title = entry.itemName,
                                        subtitle = "${formatValue(entry.value)} ${entry.itemUnit}",
                                        leadingContent = { RecordItemInitial(entry.itemName) },
                                        trailing = { Text(formatMinute(entry.localMinute), style = MaterialTheme.typography.labelLarge) },
                                    )
                                    if (index != entries.lastIndex) TemplateDivider()
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(TemplateStyle.spacing.pageBottom)) }
    }

    if (showRecordSheet) {
        RecordSheet(
            items = state.activeItems,
            onDismiss = onDismissRecordSheet,
            onRecord = onRecord,
        )
    }
}

private fun formatMinute(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minute / 60,
    minute % 60,
)

@Composable
private fun GoalProgressSection(items: List<RecordItem>, values: Map<Long, Double>) {
    val goals = items.filter { it.goalValue != null && it.goalPeriod != null }
    if (goals.isEmpty()) return
    TemplateSection(title = "Goals") {
        goals.forEachIndexed { index, item ->
            val value = values[item.id] ?: 0.0
            val goal = checkNotNull(item.goalValue)
            val progress by animateFloatAsState(
                targetValue = (value / goal).coerceIn(0.0, 1.0).toFloat(),
                label = "GoalProgress",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecordItemInitial(item.name)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = goalProgressLabel(item, value, goal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 52.dp, top = 10.dp)
                        .height(6.dp),
                )
            }
            if (index != goals.lastIndex) TemplateDivider()
        }
    }
}

private fun goalProgressLabel(item: RecordItem, value: Double, goal: Double): String =
    "${formatValue(value)} / ${formatValue(goal)} ${item.unit} ${if (item.goalPeriod == GoalPeriod.DAY) "today" else "this week"}"
