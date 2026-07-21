package com.wswensw.template.record.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
            Spacer(Modifier.height(TemplateStyle.spacing.pageTop))
            TemplatePageHeader(
                title = "Records",
                subtitle = "Review recorded values by time.",
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

            else -> item {
                AnimatedContent(
                    targetState = state.entries,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith
                            fadeOut(animationSpec = tween(90))
                    },
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
                                TemplateListItem(
                                    title = formatMinute(entry.localMinute),
                                    subtitle = entry.subtitle(state.findItem(entry.itemId)),
                                )
                                if (index != entries.lastIndex) {
                                    TemplateDivider()
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

private fun com.wswensw.template.record.model.RecordEntry.subtitle(item: RecordItem?): String = listOfNotNull(
    "$itemName · ${formatValue(value)} $itemUnit",
    item?.goalSummary(),
).joinToString("\n")
