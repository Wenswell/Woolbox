package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
            IconButton(onClick = onManage) {
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
                GoalPageMessage(
                    message = state.error,
                    actionLabel = if (state.connection.isConfigured) "Retry" else "Configure connection",
                    onAction = if (state.connection.isConfigured) onRetry else onConfigureConnection,
                )
            }
            dailyGoals.isEmpty() && weeklyGoals.isEmpty() -> item {
                GoalPageMessage(
                    message = "No goals yet.",
                    actionLabel = "Manage records",
                    onAction = onManage,
                )
            }
            else -> {
                if (dailyGoals.isNotEmpty()) {
                    item { GoalSectionHeader("Daily") }
                    items(dailyGoals, key = { "daily-${it.id}" }) { item ->
                        GoalCard(
                            item = item,
                            cumulative = state.goalValues[item.id] ?: 0.0,
                            streak = state.streaks[item.id],
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
                            streak = state.streaks[item.id],
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
        modifier = Modifier.padding(
            start = TemplateStyle.spacing.pageHorizontal,
            top = 8.dp,
            end = TemplateStyle.spacing.pageHorizontal,
            bottom = 8.dp,
        ),
    )
}

@Composable
private fun GoalPageMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TemplateStyle.spacing.pageHorizontal),
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }
        }
    }
}
