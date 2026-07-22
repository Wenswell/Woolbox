package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.ui.TemplateLazyRouteScaffold
import com.wswensw.template.ui.TemplateStyle

@Composable
fun GoalsScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onManage: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    onRecordItem: (RecordItem) -> Unit,
    snackbarHost: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dailyGoals = goalItems(state.activeItems, GoalPeriod.DAY)
    val weeklyGoals = goalItems(state.activeItems, GoalPeriod.WEEK)

    TemplateLazyRouteScaffold(
        title = "Goals",
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = onManage) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24),
                    contentDescription = "Manage records",
                )
            }
        },
        snackbarHost = snackbarHost,
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
            state.error != null -> item {
                RecordsStatusCard(
                    message = state.error,
                    actionLabel = if (state.connection.isConfigured) "Retry" else "Configure connection",
                    onAction = if (state.connection.isConfigured) onRetry else onConfigureConnection,
                    modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal),
                )
            }
            dailyGoals.isEmpty() && weeklyGoals.isEmpty() -> item {
                RecordsStatusCard(
                    message = "No goals yet.",
                    actionLabel = "Manage records",
                    onAction = onManage,
                    modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal),
                )
            }
            else -> {
                if (dailyGoals.isNotEmpty()) {
                    item { GoalSectionHeader("Daily") }
                    items(dailyGoals, key = { "daily-${it.id}" }) { item ->
                        GoalCard(
                            item = item,
                            cumulative = state.goalValues[item.id] ?: 0.0,
                            featured = false,
                            onClick = { onRecordItem(item) },
                            modifier = Modifier
                                .padding(horizontal = TemplateStyle.spacing.pageHorizontal)
                                .padding(bottom = 10.dp),
                        )
                    }
                }
                if (weeklyGoals.isNotEmpty()) {
                    item { GoalSectionHeader("Weekly") }
                    items(weeklyGoals, key = { "weekly-${it.id}" }) { item ->
                        GoalCard(
                            item = item,
                            cumulative = state.goalValues[item.id] ?: 0.0,
                            featured = false,
                            onClick = { onRecordItem(item) },
                            modifier = Modifier
                                .padding(horizontal = TemplateStyle.spacing.pageHorizontal)
                                .padding(bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalSectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            start = TemplateStyle.spacing.pageHorizontal,
            top = 8.dp,
            end = TemplateStyle.spacing.pageHorizontal,
            bottom = 8.dp,
        ),
    )
}

