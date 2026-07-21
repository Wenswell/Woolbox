package com.wswensw.template.record.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordValueType
import com.wswensw.template.ui.TemplateDivider
import com.wswensw.template.ui.TemplateListItem
import com.wswensw.template.ui.TemplateRouteScaffold
import com.wswensw.template.ui.TemplateSection

@Composable
fun ManageRecordsScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onNewItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    TemplateRouteScaffold(
        title = "Manage records",
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(onClick = onNewItem) {
                Text("New")
            }
        },
    ) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.activeItems.isEmpty() -> {
                TemplateSection(title = "Active") {
                    TemplateListItem(
                        title = "No active items",
                        subtitle = "Choose New to create the first record item.",
                    )
                }
            }

            else -> {
                TemplateSection(title = "Active") {
                    state.activeItems.forEachIndexed { index, item ->
                        RecordItemRow(item = item, onClick = { onEditItem(item.id) })
                        if (index != state.activeItems.lastIndex) {
                            TemplateDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordItemRow(
    item: RecordItem,
    onClick: () -> Unit,
) {
    TemplateListItem(
        title = item.name,
        subtitle = item.summary(),
        leadingContent = { RecordItemIcon(item.icon) },
        onClick = onClick,
    )
}

private fun RecordItem.summary(): String {
    val type = if (valueType == RecordValueType.NUMBER) "Number" else "Duration"
    val default = defaultValue?.let(::formatValue) ?: "No default"
    return listOf(
        "$type · $default $unit · step ${formatValue(stepValue)}",
        goalSummary(),
    ).filterNotNull().joinToString("\n")
}

internal fun RecordItem.goalSummary(): String? = goalValue?.let { target ->
    "Target ${formatValue(target)} $unit / ${if (goalPeriod == GoalPeriod.DAY) "day" else "week"}"
}

internal fun formatValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
