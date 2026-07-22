package com.wswensw.template.record.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.ui.TemplateDivider
import com.wswensw.template.ui.TemplatePrimaryAction
import com.wswensw.template.ui.TemplateStyle
import java.util.Locale

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    onManage: () -> Unit,
    onCreateItem: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    onRecordItem: (RecordItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        end = TemplateStyle.spacing.pageHorizontal,
                        top = TemplateStyle.spacing.pageTop,
                    ),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onManage) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_24),
                        contentDescription = "Manage records",
                    )
                }
            }
        }

        when {
            state.isLoading -> item {
                CircularProgressIndicator(
                    modifier = Modifier.padding(48.dp),
                )
            }
            state.error != null -> item {
                Column(modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal)) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    TemplatePrimaryAction(
                        label = if (state.connection.isConfigured) "Retry" else "Configure connection",
                        iconRes = R.drawable.ic_records_24,
                        enabled = true,
                        onClick = if (state.connection.isConfigured) onRetry else onConfigureConnection,
                    )
                }
            }
            state.activeItems.isEmpty() -> item {
                Column(modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal)) {
                    Text(
                        text = "Create a record item before recording.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
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
                val sorted = state.activeItems.sortedWith(
                    compareByDescending<RecordItem> { it.priority }
                        .thenBy { it.name.lowercase(Locale.ROOT) },
                )
                itemsIndexed(sorted) { index, item ->
                    RecordItemRow(
                        item = item,
                        cumulative = state.goalValues[item.id] ?: 0.0,
                        onClick = { onRecordItem(item) },
                    )
                    if (index != sorted.lastIndex) {
                        TemplateDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordItemRow(
    item: RecordItem,
    cumulative: Double,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordItemInitial(item.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${formatValue(cumulative)} ${item.unit}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val goal = item.goalValue
            if (goal != null && goal > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (cumulative / goal).coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = goalProgressLabel(item, cumulative, goal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun goalProgressLabel(item: RecordItem, value: Double, goal: Double): String =
    "${formatValue(value)} / ${formatValue(goal)} ${item.unit}" +
        " · ${if (item.goalPeriod == GoalPeriod.DAY) "today" else "this week"}"
