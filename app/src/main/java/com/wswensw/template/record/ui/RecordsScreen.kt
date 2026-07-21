package com.wswensw.template.record.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.ui.TemplateDivider
import com.wswensw.template.ui.TemplateListItem
import com.wswensw.template.ui.TemplatePrimaryAction
import com.wswensw.template.ui.TemplateSection
import com.wswensw.template.ui.TemplateStyle
import java.util.Locale
import kotlin.math.roundToInt

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
                val goals = state.activeItems.filter { it.goalValue != null && it.goalPeriod != null }
                if (goals.isNotEmpty()) {
                    item { GoalRingsRow(items = goals, values = state.goalValues) }
                }
                item {
                    AnimatedContent(
                        targetState = state.entries,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(90)) },
                        label = "TodayRecords",
                    ) { entries ->
                        TemplateSection(title = "Today") {
                            if (entries.isEmpty()) {
                                TemplateListItem(
                                    title = "No records today",
                                    subtitle = "Use Record to add the first value.",
                                )
                            } else {
                                entries.forEachIndexed { index, entry ->
                                    RecordEntryRow(entry = entry)
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

@Composable
private fun GoalRingsRow(items: List<RecordItem>, values: Map<Long, Double>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                start = TemplateStyle.spacing.pageHorizontal,
                end = TemplateStyle.spacing.pageHorizontal,
                bottom = 8.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val goal = checkNotNull(item.goalValue)
            val value = values[item.id] ?: 0.0
            GoalRing(
                item = item,
                value = value,
                goal = goal,
                modifier = Modifier.width(92.dp),
            )
        }
    }
}

@Composable
private fun GoalRing(
    item: RecordItem,
    value: Double,
    goal: Double,
    modifier: Modifier = Modifier,
) {
    val rawProgress = (value / goal).toFloat().coerceAtLeast(0f)
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceAtMost(1f),
        label = "GoalProgress",
    )
    val overflow by animateFloatAsState(
        targetValue = (rawProgress - 1f).coerceIn(0f, 1f),
        label = "GoalOverflow",
    )
    val primary = MaterialTheme.colorScheme.primary
    val primaryTrack = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val overflowColor = MaterialTheme.colorScheme.tertiary
    val overflowTrack = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val primaryStroke = 10.dp.toPx()
                val primaryDiameter = size.minDimension - primaryStroke
                val primaryTopLeft = Offset(
                    x = (size.width - primaryDiameter) / 2f,
                    y = (size.height - primaryDiameter) / 2f,
                )
                val primarySize = Size(primaryDiameter, primaryDiameter)
                drawArc(
                    color = primaryTrack,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = primaryTopLeft,
                    size = primarySize,
                    style = Stroke(width = primaryStroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = primary,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    topLeft = primaryTopLeft,
                    size = primarySize,
                    style = Stroke(width = primaryStroke, cap = StrokeCap.Round),
                )
                if (overflow > 0f) {
                    val overflowStroke = 6.dp.toPx()
                    val overflowDiameter = primaryDiameter - primaryStroke - overflowStroke
                    val overflowTopLeft = Offset(
                        x = (size.width - overflowDiameter) / 2f,
                        y = (size.height - overflowDiameter) / 2f,
                    )
                    drawArc(
                        color = overflowTrack,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = overflowTopLeft,
                        size = Size(overflowDiameter, overflowDiameter),
                        style = Stroke(width = overflowStroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = overflowColor,
                        startAngle = -90f,
                        sweepAngle = overflow * 360f,
                        useCenter = false,
                        topLeft = overflowTopLeft,
                        size = Size(overflowDiameter, overflowDiameter),
                        style = Stroke(width = overflowStroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                text = "${(rawProgress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = goalProgressLabel(item, value, goal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordEntryRow(entry: RecordEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordItemInitial(entry.itemName)
        Spacer(Modifier.width(12.dp))
        Text(
            text = dropFirstRecordCharacter(entry.itemName),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${formatValue(entry.value)} ${entry.itemUnit}",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatMinute(entry.localMinute),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun formatMinute(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minute / 60,
    minute % 60,
)

private fun goalProgressLabel(item: RecordItem, value: Double, goal: Double): String =
    "${formatValue(value)} / ${formatValue(goal)} ${item.unit} ${if (item.goalPeriod == GoalPeriod.DAY) "today" else "this week"}"
