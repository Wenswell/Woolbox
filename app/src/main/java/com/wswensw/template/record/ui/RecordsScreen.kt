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
import com.wswensw.template.ui.TemplatePageHeader
import com.wswensw.template.ui.TemplatePrimaryAction
import com.wswensw.template.ui.TemplateStyle

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    onManage: () -> Unit,
    onCreateItem: () -> Unit,
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
                Text(
                    text = "${state.activeItems.size} active record items",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item { Spacer(Modifier.height(TemplateStyle.spacing.pageBottom)) }
    }
}
