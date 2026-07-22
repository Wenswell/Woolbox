package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordMode
import com.wswensw.template.record.model.RecordValueType
import com.wswensw.template.ui.TemplateLazyRouteScaffold
import com.wswensw.template.ui.TemplateStyle

@Composable
fun ManageRecordsScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onNewItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    TemplateLazyRouteScaffold(
        title = "Manage records",
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = onNewItem) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24),
                    contentDescription = "New record item",
                )
            }
        },
    ) {
        when {
            state.isLoading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.activeItems.isEmpty() -> item {
                RecordsStatusCard(
                    message = "No active items yet.",
                    actionLabel = "Create record item",
                    onAction = onNewItem,
                    modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal),
                )
            }
            else -> {
                item {
                    Text(
                        text = "Active",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            horizontal = TemplateStyle.spacing.pageHorizontal,
                            vertical = 4.dp,
                        ),
                    )
                }
                items(orderedRecordItems(state.activeItems), key = RecordItem::id) { item ->
                    ManageRecordCard(
                        item = item,
                        onClick = { onEditItem(item.id) },
                        modifier = Modifier
                            .padding(horizontal = TemplateStyle.spacing.pageHorizontal)
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ManageRecordCard(
    item: RecordItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordItemColors(item.name)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
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
                Text(
                    text = item.measurementSummary(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.goalSummary()?.let { goal ->
                    Text(
                        text = goal,
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun RecordItem.measurementSummary(): String {
    if (mode == RecordMode.MEASUREMENT) {
        val default = defaultValue?.let(::formatValue) ?: "No default"
        return "Measurement · default $default $unit · step ${formatValue(stepValue)}"
    }
    val type = if (valueType == RecordValueType.NUMBER) "Number" else "Duration"
    val default = defaultValue?.let(::formatValue) ?: "No default"
    return "$type · default $default $unit · step ${formatValue(stepValue)}"
}

internal fun RecordItem.goalSummary(): String? = goalValue?.let { target ->
    "Target ${formatValue(target)} $unit / ${if (goalPeriod == GoalPeriod.DAY) "day" else "week"}"
}

internal fun formatValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
