package com.wswensw.template.record.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.ItemStreak
import com.wswensw.template.record.model.streakLabel
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.ui.TemplateStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    onGoals: () -> Unit,
    onManage: () -> Unit,
    onCreateItem: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    onRecordItem: (RecordItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TemplateStyle.spacing.pageHorizontal,
            top = TemplateStyle.spacing.pageTop,
            end = TemplateStyle.spacing.pageHorizontal,
            bottom = TemplateStyle.spacing.pageBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(TemplateStyle.spacing.block),
    ) {
        item {
            val orderedItems = orderedRecordItems(state.activeItems)
            val focusGoals = goalItems(orderedItems)
            val completion = focusGoalCompletion(focusGoals, state.goalValues)
            RecordsHeader(
                date = state.selectedDate,
                completion = completion,
                onGoals = onGoals,
                onManage = onManage,
            )
        }

        when {
            state.isLoading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> item {
                RecordsMessage(
                    message = state.error,
                    actionLabel = if (state.connection.isConfigured) "Retry" else "Configure connection",
                    onAction = if (state.connection.isConfigured) onRetry else onConfigureConnection,
                )
            }
            state.activeItems.isEmpty() -> item {
                RecordsMessage(
                    message = "No record items yet.",
                    actionLabel = "Create record item",
                    onAction = onCreateItem,
                )
            }
            else -> {
                val orderedItems = orderedRecordItems(state.activeItems)
                val focusGoals = goalItems(orderedItems)
                val todayValues = todayRecordValues(state.entries)

                if (focusGoals.isNotEmpty()) {
                    item {
                        RecordsSectionTitle(title = "Focus")
                    }
                    item(key = "focus-goals") {
                        FocusGoalsRow(
                            goals = focusGoals,
                            values = state.goalValues,
                            onRecordItem = onRecordItem,
                        )
                    }
                }

                item {
                    RecordsSectionTitle(title = "Records")
                }
                items(orderedItems, key = RecordItem::id) { item ->
                    QuickRecordCard(
                        item = item,
                        todayValue = todayValues[item.id] ?: 0.0,
                        streak = state.streaks[item.id],
                        onClick = { onRecordItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusGoalsRow(
    goals: List<RecordItem>,
    values: Map<Long, Double>,
    onRecordItem: (RecordItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth * 0.82f).coerceAtMost(300.dp)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(goals, key = RecordItem::id) { goal ->
                GoalCard(
                    item = goal,
                    cumulative = values[goal.id] ?: 0.0,
                    featured = true,
                    onClick = { onRecordItem(goal) },
                    modifier = Modifier
                        .width(cardWidth)
                        .heightIn(min = 136.dp),
                )
            }
        }
    }
}

@Composable
private fun RecordsHeader(
    date: LocalDate,
    completion: FocusGoalCompletion,
    onGoals: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = recordsDateLabel(date),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (completion.total > 0) {
                Text(
                    text = dayStatusLabel(completion),
                    color = if (completion.done >= completion.total) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        FilledTonalIconButton(onClick = onGoals) {
            Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = "Goals",
            )
        }
        Spacer(Modifier.width(4.dp))
        FilledTonalIconButton(onClick = onManage) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_24),
                contentDescription = "Manage records",
            )
        }
    }
}

@Composable
private fun RecordsSectionTitle(
    title: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun QuickRecordCard(
    item: RecordItem,
    todayValue: Double,
    streak: ItemStreak?,
    onClick: () -> Unit,
) {
    val colors = recordItemColors(item.name)
    val hasLoggedToday = todayValue > 0.0
    val container by animateColorAsState(
        targetValue = if (hasLoggedToday) {
            colors.container
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "QuickRecordContainer",
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordItemInitial(name = item.name, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayRecordName(item.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.widthIn(max = 140.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "${formatValue(todayValue)} ${item.unit}",
                    color = colors.accent,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val streakText = streak?.let(::streakLabel)
                if (streakText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = streakText,
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TemplateStyle.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_records_24),
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

internal data class FocusGoalCompletion(
    val done: Int,
    val total: Int,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else done.toFloat() / total.toFloat()
}

internal fun focusGoalCompletion(
    goals: List<RecordItem>,
    values: Map<Long, Double>,
): FocusGoalCompletion {
    val done = goals.count { item ->
        val target = item.goalValue ?: return@count false
        (values[item.id] ?: 0.0) >= target
    }
    return FocusGoalCompletion(done = done, total = goals.size)
}

internal fun dayStatusLabel(completion: FocusGoalCompletion): String = when {
    completion.total <= 0 -> ""
    completion.done <= 0 -> "0 of ${completion.total} goals done"
    completion.done >= completion.total -> "All goals done"
    else -> "${completion.done} of ${completion.total} goals done"
}

internal fun crossedGoalThreshold(
    previous: Double,
    current: Double,
    target: Double?,
): Boolean {
    if (target == null || target <= 0.0) return false
    return previous < target && current >= target
}


internal fun orderedRecordItems(items: List<RecordItem>): List<RecordItem> =
    items.sortedWith(
        compareByDescending<RecordItem> { it.priority }
            .thenBy { it.name.lowercase(Locale.ROOT) },
    )

internal fun goalItems(items: List<RecordItem>, period: GoalPeriod? = null): List<RecordItem> =
    orderedRecordItems(items).filter { item ->
        item.goalValue != null && item.goalPeriod != null && (period == null || item.goalPeriod == period)
    }

internal fun todayRecordValues(entries: List<RecordEntry>): Map<Long, Double> =
    entries.groupBy(RecordEntry::itemId).mapValues { (_, itemEntries) -> itemEntries.sumOf(RecordEntry::value) }

internal fun recordsDateLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))
