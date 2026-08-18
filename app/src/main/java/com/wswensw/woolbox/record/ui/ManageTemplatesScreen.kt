package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.TaskTemplate
import com.wswensw.woolbox.record.model.TemplateCadence
import com.wswensw.woolbox.ui.AppLazyRouteScaffold
import com.wswensw.woolbox.ui.AppListItem
import com.wswensw.woolbox.ui.AppStyle

@Composable
fun ManageTemplatesScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val templates = state.templates.sortedWith(
        compareBy<TaskTemplate> { it.cadence != TemplateCadence.DAILY }
            .thenBy { it.title.lowercase() },
    )
    AppLazyRouteScaffold(
        title = stringResource(R.string.manage_templates),
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = onCreate) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_template))
            }
        },
    ) {
        when {
            state.isLoading -> item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && templates.isEmpty() -> item {
                RecordsStatusCard(
                    message = state.error,
                    actionLabel = stringResource(R.string.navigation_back),
                    onAction = onBack,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                )
            }
            templates.isEmpty() -> item {
                RecordsStatusCard(
                    message = stringResource(R.string.no_templates),
                    actionLabel = stringResource(R.string.create_template),
                    onAction = onCreate,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                )
            }
            else -> items(templates, key = TaskTemplate::id) { template ->
                AppListItem(
                    title = template.title,
                    subtitle = stringResource(
                        R.string.template_summary,
                        template.steps.size,
                        stringResource(
                            if (template.cadence == TemplateCadence.DAILY) {
                                R.string.template_daily
                            } else {
                                R.string.template_manual
                            },
                        ),
                    ),
                    onClick = { onEdit(template.id) },
                )
            }
        }
    }
}
