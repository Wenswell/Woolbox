package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.ui.TemplatePageHeader
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
            Spacer(Modifier.height(TemplateStyle.spacing.pageTop))
            TemplatePageHeader(
                title = "Records",
                subtitle = "Track values and daily progress.",
                titleActions = {
                    TextButton(onClick = onManage) {
                        Text("Manage")
                    }
                },
                modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal),
            )
            Spacer(Modifier.height(32.dp))
        }

        when {
            state.isLoading -> item { CircularProgressIndicator() }
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

            else -> item {
                TemplateSection(title = "Today") {
                    if (state.entries.isEmpty()) {
                        TemplateListItem(
                            title = "No records today",
                            subtitle = "Use Record to add the first value.",
                            iconRes = R.drawable.ic_records_24,
                        )
                    } else {
                        state.entries.forEachIndexed { index, entry ->
                            TemplateListItem(
                                title = entry.itemName,
                                subtitle = "${formatMinute(entry.localMinute)} · ${formatValue(entry.value)} ${entry.itemUnit}",
                                iconRes = R.drawable.ic_records_24,
                            )
                            if (index != state.entries.lastIndex) {
                                TemplateDivider()
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
