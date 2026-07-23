package com.wswensw.woolbox.record.ui

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
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordValueType
import com.wswensw.woolbox.ui.AppLazyRouteScaffold
import com.wswensw.woolbox.ui.AppStyle
import java.util.Locale

@Composable
fun ManageRecordsScreen(
    state: RecordsUiState,
    mode: RecordMode,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEditItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = manageRecordsCopy(mode)
    val items = managedRecordItems(state.activeItems, mode)

    AppLazyRouteScaffold(
        title = copy.title,
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = onNew) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24),
                    contentDescription = copy.newContentDescription,
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
            state.error != null && items.isEmpty() -> item {
                RecordsStatusCard(
                    message = state.error,
                    actionLabel = "Back",
                    onAction = onBack,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                )
            }
            items.isEmpty() -> item {
                RecordsStatusCard(
                    message = copy.emptyMessage,
                    actionLabel = copy.actionLabel,
                    onAction = onNew,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                )
            }
            else -> items(items, key = RecordItem::id) { item ->
                ManageRecordCard(
                    item = item,
                    onClick = { onEditItem(item.id) },
                    modifier = Modifier
                        .padding(horizontal = AppStyle.spacing.pageHorizontal)
                        .padding(bottom = 8.dp),
                )
            }
        }
    }
}

private data class ManageRecordsCopy(
    val title: String,
    val emptyMessage: String,
    val actionLabel: String,
    val newContentDescription: String,
)

private fun manageRecordsCopy(mode: RecordMode): ManageRecordsCopy = when (mode) {
    RecordMode.ACCUMULATE -> ManageRecordsCopy(
        title = "Manage accumulates",
        emptyMessage = "No accumulate items yet.",
        actionLabel = "Create accumulate",
        newContentDescription = "New accumulate",
    )
    RecordMode.MEASUREMENT -> ManageRecordsCopy(
        title = "Manage measurements",
        emptyMessage = "No measurement items yet.",
        actionLabel = "Create measurement",
        newContentDescription = "New measurement",
    )
}

internal fun managedRecordItems(
    items: List<RecordItem>,
    mode: RecordMode,
): List<RecordItem> = when (mode) {
    RecordMode.ACCUMULATE -> orderedRecordItems(items)
        .filter { it.mode == RecordMode.ACCUMULATE }
    RecordMode.MEASUREMENT -> items
        .filter { it.mode == RecordMode.MEASUREMENT }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
}

@Composable
private fun ManageRecordCard(
    item: RecordItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordProgressColors(progress = 0f)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = colors.container,
            contentColor = colors.onContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordItemInitial(name = item.name, size = 40.dp, colors = colors)
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
                    color = colors.onContainer.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                item.goalSummary()?.let { goal ->
                    Text(
                        text = goal,
                        color = colors.accent,
                        style = MaterialTheme.typography.labelMedium,
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
                tint = colors.onContainer.copy(alpha = 0.54f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun RecordItem.measurementSummary(): String {
    if (mode == RecordMode.MEASUREMENT) {
        val min = minValue?.let(::formatValue) ?: "--"
        val max = maxValue?.let(::formatValue) ?: "--"
        return "$min–$max $unit · step ${formatValue(stepValue)}"
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
