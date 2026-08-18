package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.DailyPlan
import com.wswensw.woolbox.record.model.DailyPlanPauseReason
import com.wswensw.woolbox.record.model.DailyPlanState
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.model.TaskTemplate
import com.wswensw.woolbox.record.model.TodayCommitment
import com.wswensw.woolbox.record.model.TodayCommitmentStep
import com.wswensw.woolbox.ui.AppStyle
import java.util.Locale

@Composable
fun RecordsScreen(
    state: RecordsUiState,
    clock: State<RecordsClock>,
    onPlanToday: () -> Unit,
    onManageTemplates: () -> Unit,
    onManageAccumulates: () -> Unit,
    onManageMeasurements: () -> Unit,
    onConfigureConnection: () -> Unit,
    onRetry: () -> Unit,
    onRecordItem: (RecordItem) -> Unit,
    onCompleteStep: (DailyPlan, TodayCommitment, TodayCommitmentStep, (String?) -> Unit) -> Unit,
    onOpenCommitment: (TodayCommitment) -> Unit,
    onOpenMeasurement: (RecordItem) -> Unit,
    notificationsEnabled: Boolean,
    onEnableNotifications: () -> Unit,
    onStartPlan: (DailyPlan, (String?) -> Unit) -> Unit,
    onReviewPlan: (DailyPlan, Long, (String?) -> Unit) -> Unit,
    onContinuePlan: (DailyPlan, Long, (String?) -> Unit) -> Unit,
    onAddCommitment: (DailyPlan, Long, (String?) -> Unit) -> Unit,
    onEndPlan: (DailyPlan, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accumulateItems = orderedRecordItems(state.activeItems).filter { it.mode == RecordMode.ACCUMULATE }
    val measurementItems = state.activeItems
        .filter { it.mode == RecordMode.MEASUREMENT }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
    val todayValues = todayRecordValues(state.entries)
    clock.value

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppStyle.spacing.pageHorizontal,
            top = AppStyle.spacing.pageTop,
            end = AppStyle.spacing.pageHorizontal,
            bottom = AppStyle.spacing.pageBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && !state.hasSnapshot -> item {
                RecordsStatusCard(
                    message = state.error,
                    actionLabel = stringResource(
                        if (state.connection.isConfigured) R.string.action_retry else R.string.configure_connection,
                    ),
                    onAction = if (state.connection.isConfigured) onRetry else onConfigureConnection,
                )
            }
            else -> {
                state.error?.let { message ->
                    item {
                        RecordsStatusCard(
                            message = message,
                            actionLabel = stringResource(R.string.action_retry),
                            onAction = onRetry,
                        )
                    }
                }
                item {
                    SectionTitle(stringResource(R.string.today))
                    TodayCard(
                        plan = state.dailyPlan,
                        templates = state.templates,
                        now = System.currentTimeMillis(),
                        onPlan = onPlanToday,
                        onCompleteStep = onCompleteStep,
                        onOpenCommitment = onOpenCommitment,
                        notificationsEnabled = notificationsEnabled,
                        onEnableNotifications = onEnableNotifications,
                        onStart = onStartPlan,
                        onReview = onReviewPlan,
                        onContinue = onContinuePlan,
                        onAddCommitment = onAddCommitment,
                        onEnd = onEndPlan,
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLink(
                        title = stringResource(R.string.records_templates),
                        subtitle = stringResource(
                            R.string.templates_count,
                            state.templates.size,
                        ),
                        onClick = onManageTemplates,
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLink(
                        title = stringResource(R.string.records_routines),
                        subtitle = stringResource(R.string.routines_count, accumulateItems.size),
                        onClick = onManageAccumulates,
                    )
                }
                items(accumulateItems, key = { "routine-${it.id}" }) { item ->
                    RecordHomeRow(
                        title = displayRecordName(item.name),
                        value = "${formatValue(todayValues[item.id] ?: 0.0)} ${item.unit}",
                        onClick = { onRecordItem(item) },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionLink(
                        title = stringResource(R.string.records_measurement),
                        subtitle = stringResource(R.string.measurements_count, measurementItems.size),
                        onClick = onManageMeasurements,
                    )
                }
                items(measurementItems, key = { "measurement-${it.id}" }) { item ->
                    val latest = state.latestEntries[item.id]
                    RecordHomeRow(
                        title = displayRecordName(item.name),
                        value = latest?.let { "${formatValue(it.value)} ${item.unit}" } ?: "--",
                        onClick = { onOpenMeasurement(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayCard(
    plan: DailyPlan?,
    templates: List<TaskTemplate>,
    now: Long,
    onPlan: () -> Unit,
    onCompleteStep: (DailyPlan, TodayCommitment, TodayCommitmentStep, (String?) -> Unit) -> Unit,
    onOpenCommitment: (TodayCommitment) -> Unit,
    notificationsEnabled: Boolean,
    onEnableNotifications: () -> Unit,
    onStart: (DailyPlan, (String?) -> Unit) -> Unit,
    onReview: (DailyPlan, Long, (String?) -> Unit) -> Unit,
    onContinue: (DailyPlan, Long, (String?) -> Unit) -> Unit,
    onAddCommitment: (DailyPlan, Long, (String?) -> Unit) -> Unit,
    onEnd: (DailyPlan, (String?) -> Unit) -> Unit,
) {
    var busy by remember(plan?.revision) { mutableStateOf(false) }
    var error by remember(plan?.revision) { mutableStateOf<String?>(null) }
    var choiceMode by remember(plan?.revision) { mutableStateOf<TodayChoiceMode?>(null) }
    val currentCommitment = plan?.currentCommitment
    val openCommitments = plan?.openCommitments.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RecordsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                plan == null -> {
                    Text(stringResource(R.string.no_plan_today), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onPlan, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.plan_today))
                    }
                }
                plan.state == DailyPlanState.READY -> {
                    TodayHeader(stringResource(R.string.first_move), onPlan)
                    Text(plan.firstMove, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = {
                            busy = true
                            onStart(plan) { result -> busy = false; error = result }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.first_move_done))
                    }
                    if (!notificationsEnabled) {
                        OutlinedButton(
                            onClick = onEnableNotifications,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.enable_notifications)) }
                    }
                }
                plan.state == DailyPlanState.ACTIVE && !notificationsEnabled -> {
                    TodayHeader(stringResource(R.string.notifications_required), null)
                    Text(
                        stringResource(R.string.notifications_required_detail),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onEnableNotifications, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.enable_notifications))
                    }
                    TextButton(
                        onClick = {
                            busy = true
                            onEnd(plan) { result -> busy = false; error = result }
                        },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.end_today)) }
                }
                plan.state == DailyPlanState.ACTIVE && plan.isReviewDue(now) -> {
                    TodayHeader(stringResource(R.string.hourly_review), null)
                    Text(stringResource(R.string.still_on_commitment), style = MaterialTheme.typography.titleLarge)
                    currentCommitment?.let { Text(it.title, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Button(
                        onClick = {
                            val commitmentId = currentCommitment?.id ?: return@Button
                            busy = true
                            onReview(plan, commitmentId) { result -> busy = false; error = result }
                        },
                        enabled = !busy && currentCommitment != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.continue_current)) }
                    if (openCommitments.any { it.id != currentCommitment?.id }) {
                        OutlinedButton(
                            onClick = { choiceMode = TodayChoiceMode.REVIEW },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.correct_course)) }
                    }
                    TextButton(
                        onClick = {
                            busy = true
                            onEnd(plan) { result -> busy = false; error = result }
                        },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.end_today)) }
                }
                plan.state == DailyPlanState.ACTIVE -> {
                    TodayHeader(stringResource(R.string.round_progress, plan.completedRounds + 1), null)
                    currentCommitment?.let { commitment ->
                        Text(commitment.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        commitment.nextStep?.let { step ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RecordsCardShape,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = false,
                                        onCheckedChange = {
                                            busy = true
                                            onCompleteStep(plan, commitment, step) { result ->
                                                busy = false
                                                error = result
                                            }
                                        },
                                        enabled = !busy,
                                    )
                                    Text(step.title, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.review_around_hour),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                plan.state == DailyPlanState.PAUSED -> {
                    TodayHeader(
                        stringResource(
                            if (plan.pauseReason == DailyPlanPauseReason.COMMITMENTS_COMPLETED) {
                                R.string.commitments_completed
                            } else {
                                R.string.enough_for_today
                            },
                        ),
                        null,
                    )
                    Text(
                        if (plan.completedRounds == 0) {
                            stringResource(R.string.completed_before_first_review)
                        } else {
                            pluralStringResource(R.plurals.rounds_completed, plan.completedRounds, plan.completedRounds)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (plan.pauseReason == DailyPlanPauseReason.TARGET_REACHED && openCommitments.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (openCommitments.size == 1) {
                                    busy = true
                                    onContinue(plan, openCommitments.single().id) { result ->
                                        busy = false
                                        error = result
                                    }
                                } else {
                                    choiceMode = TodayChoiceMode.CONTINUE
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.continue_one_more_round)) }
                    } else {
                        Button(
                            onClick = { choiceMode = TodayChoiceMode.ADD },
                            enabled = !busy && templates.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.add_commitment)) }
                    }
                    TextButton(
                        onClick = {
                            busy = true
                            onEnd(plan) { result -> busy = false; error = result }
                        },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.end_today)) }
                }
                else -> {
                    TodayHeader(stringResource(R.string.today_ended), null)
                    Text(
                        stringResource(R.string.today_ended_detail),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            plan?.commitments?.takeIf { it.isNotEmpty() }?.let { commitments ->
                Text(
                    stringResource(R.string.today_commitments),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                commitments.forEach { commitment ->
                    CommitmentRow(
                        commitment = commitment,
                        current = commitment.id == plan.currentCommitmentId,
                        onClick = { onOpenCommitment(commitment) },
                    )
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (choiceMode == TodayChoiceMode.REVIEW && plan != null) {
        CommitmentChoiceDialog(
            title = stringResource(R.string.correct_course),
            commitments = openCommitments.filter { it.id != currentCommitment?.id },
            onDismiss = { choiceMode = null },
            onSelect = { commitment ->
                choiceMode = null
                busy = true
                onReview(plan, commitment.id) { result -> busy = false; error = result }
            },
        )
    }
    if (choiceMode == TodayChoiceMode.CONTINUE && plan != null) {
        CommitmentChoiceDialog(
            title = stringResource(R.string.continue_one_more_round),
            commitments = openCommitments,
            onDismiss = { choiceMode = null },
            onSelect = { commitment ->
                choiceMode = null
                busy = true
                onContinue(plan, commitment.id) { result -> busy = false; error = result }
            },
        )
    }
    if (choiceMode == TodayChoiceMode.ADD && plan != null) {
        TemplateChoiceDialog(
            title = stringResource(R.string.add_commitment),
            templates = templates,
            onDismiss = { choiceMode = null },
            onSelect = { template ->
                choiceMode = null
                busy = true
                onAddCommitment(plan, template.id) { result -> busy = false; error = result }
            },
        )
    }
}

private enum class TodayChoiceMode { REVIEW, CONTINUE, ADD }

@Composable
private fun CommitmentRow(
    commitment: TodayCommitment,
    current: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (current) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = RecordsCardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(commitment.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        R.string.commitment_steps_progress,
                        commitment.completedStepCount,
                        commitment.steps.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (commitment.isCompleted) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            } else {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun TodayHeader(title: String, onEdit: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        onEdit?.let {
            IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_today))
            }
        }
    }
}

@Composable
internal fun TemplateChoiceDialog(
    title: String,
    templates: List<TaskTemplate>,
    onDismiss: () -> Unit,
    onSelect: (TaskTemplate) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(templates, key = TaskTemplate::id) { template ->
                    TextButton(onClick = { onSelect(template) }, modifier = Modifier.fillMaxWidth()) {
                        Text(template.title, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            onClear?.let { clear ->
                TextButton(onClick = clear) { Text(stringResource(R.string.no_secondary_commitment)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CommitmentChoiceDialog(
    title: String,
    commitments: List<TodayCommitment>,
    onDismiss: () -> Unit,
    onSelect: (TodayCommitment) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(commitments, key = TodayCommitment::id) { commitment ->
                    TextButton(onClick = { onSelect(commitment) }, modifier = Modifier.fillMaxWidth()) {
                        Text(commitment.title, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SectionLink(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun RecordHomeRow(title: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RecordsCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun RecordsStatusCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_records_24), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

internal fun crossedGoalThreshold(previous: Double, current: Double, target: Double?): Boolean =
    target != null && target > 0.0 && previous < target && current >= target

internal fun nextCommitmentStepTitle(commitment: TodayCommitment): String? = commitment.nextStep?.title

internal fun orderedRecordItems(items: List<RecordItem>): List<RecordItem> =
    items.sortedWith(
        compareByDescending<RecordItem> { it.goalValue != null && it.goalPeriod != null }
            .thenByDescending { it.priority }
            .thenBy { it.name.lowercase(Locale.ROOT) },
    )

internal fun goalItems(items: List<RecordItem>, period: GoalPeriod? = null): List<RecordItem> =
    orderedRecordItems(items).filter { item ->
        item.mode == RecordMode.ACCUMULATE && item.goalValue != null && item.goalPeriod != null &&
            (period == null || item.goalPeriod == period)
    }

internal fun todayRecordValues(entries: List<RecordEntry>): Map<Long, Double> =
    entries.groupBy(RecordEntry::itemId).mapValues { (_, values) -> values.sumOf(RecordEntry::value) }

internal fun todayRecordMinutes(entries: List<RecordEntry>): Map<Long, List<Int>> =
    entries.groupBy(RecordEntry::itemId).mapValues { (_, values) -> values.map(RecordEntry::localMinute) }
