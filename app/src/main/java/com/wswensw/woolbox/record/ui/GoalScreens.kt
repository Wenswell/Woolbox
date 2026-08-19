package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.GoalPool
import com.wswensw.woolbox.record.model.GoalPoolDraft
import com.wswensw.woolbox.record.model.GoalSubgoalDraft
import com.wswensw.woolbox.ui.AppLazyRouteScaffold
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Composable
fun ManageGoalsScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit,
    onRetry: (Long) -> Unit,
) {
    AppLazyRouteScaffold(
        title = stringResource(R.string.manage_goals),
        onBack = onBack,
        actions = {
            FilledTonalIconButton(onClick = onNew) {
                Icon(painterResource(R.drawable.ic_add_24), contentDescription = stringResource(R.string.new_goal))
            }
        },
    ) {
        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.goals.isEmpty() -> item {
                RecordsStatusCard(
                    message = stringResource(R.string.no_goals),
                    actionLabel = stringResource(R.string.create_goal),
                    onAction = onNew,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
                )
            }
            else -> items(state.goals, key = GoalPool::id) { goal ->
                ManageGoalCard(
                    goal = goal,
                    onClick = { onEdit(goal.id) },
                    onRetry = { onRetry(goal.id) },
                    modifier = Modifier
                        .padding(horizontal = AppStyle.spacing.pageHorizontal)
                        .padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ManageGoalCard(
    goal: GoalPool,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closed = goal.completed || goal.expired()
    val largestTarget = goal.subgoals.maxOfOrNull { it.targetValue } ?: 0.0
    val progress = if (largestTarget > 0) goalProgressRatio(goal.currentValue, largestTarget).toFloat() else 0f
    val colors = recordProgressColors(progress = progress, completed = goal.completed)
    Card(
        onClick = onClick,
        enabled = !closed,
        modifier = modifier.fillMaxWidth(),
        shape = AppStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = colors.container,
            contentColor = colors.onContainer,
            disabledContainerColor = colors.container,
            disabledContentColor = colors.onContainer,
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
            RecordItemInitial(name = goal.title, size = 40.dp, colors = colors)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayRecordName(goal.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.goal_subgoal_progress,
                        goal.subgoals.count { it.completedAt != null },
                        goal.subgoals.size,
                    ),
                    color = colors.onContainer.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = stringResource(
                        R.string.goal_shared_progress,
                        formatValue(goal.currentValue),
                        goal.unit,
                    ),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (closed) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Replay, contentDescription = stringResource(R.string.retry_goal))
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.onContainer.copy(alpha = 0.54f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalPoolSheet(
    goal: GoalPool,
    onDismiss: () -> Unit,
    onRecord: (Double, (String?) -> Unit) -> Unit,
    onToggleSubgoal: (Long, Boolean, (String?) -> Unit) -> Unit,
) {
    var value by rememberSaveable(goal.id) {
        mutableStateOf(goal.defaultValue?.let(::formatValue).orEmpty())
    }
    var saving by rememberSaveable(goal.id) { mutableStateOf(false) }
    var error by rememberSaveable(goal.id) { mutableStateOf<String?>(null) }
    val positiveValueMessage = stringResource(R.string.error_positive_value)
    val pending = goal.pending()
    val expired = goal.expired()
    val active = !goal.completed && !pending && !expired
    val parsed = value.trim().toDoubleOrNull()
    val canRecord = active && !saving && parsed != null && parsed.isFinite() && parsed > 0

    fun record() {
        val amount = value.trim().toDoubleOrNull()
        if (amount == null || !amount.isFinite() || amount <= 0) {
            error = positiveValueMessage
            return
        }
        saving = true
        error = null
        onRecord(amount) { message ->
            saving = false
            error = message
            if (message == null) value = goal.defaultValue?.let(::formatValue).orEmpty()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppStyle.spacing.pageHorizontal)
                .padding(bottom = 28.dp),
        ) {
            GoalSheetIdentity(goal)
            Spacer(Modifier.height(20.dp))
            RecordValueInput(
                unit = goal.unit,
                stepValue = goal.stepValue,
                valueText = value,
                enabled = active,
                saving = saving,
                canConfirm = canRecord,
                errorMessage = error,
                onValueChange = {
                    value = it
                    error = null
                },
                onConfirm = ::record,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.goal_subgoals),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = AppStyle.cardShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    goal.subgoals.forEachIndexed { index, subgoal ->
                        val eligible = goal.currentValue >= subgoal.targetValue
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = subgoal.completedAt != null,
                                enabled = active && (eligible || subgoal.completedAt != null),
                                onCheckedChange = { completed ->
                                    onToggleSubgoal(subgoal.id, completed) { message -> error = message }
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(subgoal.title, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (eligible) {
                                        stringResource(R.string.goal_ready_to_complete)
                                    } else {
                                        stringResource(
                                            R.string.goal_remaining,
                                            formatValue(subgoal.targetValue - goal.currentValue),
                                            goal.unit,
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${formatValue(subgoal.targetValue)} ${goal.unit}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        if (index < goal.subgoals.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalSheetIdentity(goal: GoalPool) {
    val largestTarget = goal.subgoals.maxOfOrNull { it.targetValue } ?: 0.0
    val progress = if (largestTarget > 0) goalProgressRatio(goal.currentValue, largestTarget).toFloat() else 0f
    val colors = recordProgressColors(progress = progress, completed = goal.completed)
    val status = when {
        goal.pending() -> stringResource(R.string.goal_pending, goal.startDate.toString())
        goal.expired() -> stringResource(R.string.goal_expired)
        goal.completed -> stringResource(R.string.goal_completed)
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RecordItemInitial(name = goal.title, size = 52.dp, colors = colors)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayRecordName(goal.title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (goal.description.isNotEmpty()) {
                Text(
                    text = goal.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    R.string.goal_shared_progress,
                    formatValue(goal.currentValue),
                    goal.unit,
                ),
                color = colors.accent,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            status?.let {
                Text(
                    text = it,
                    color = if (goal.expired()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private data class EditableSubgoal(val id: Long?, val title: String, val target: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorScreen(
    goal: GoalPool?,
    source: GoalPool? = null,
    editing: Boolean,
    loading: Boolean,
    onBack: () -> Unit,
    onSave: (GoalPoolDraft, (String?) -> Unit) -> Unit,
) {
    if (editing && goal == null) {
        AppRouteScaffold(title = stringResource(R.string.edit_goal), onBack = onBack) {
            if (loading) {
                CircularProgressIndicator(Modifier.padding(AppStyle.spacing.pageHorizontal))
            } else {
                Text(
                    text = stringResource(R.string.goal_not_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AppStyle.spacing.pageHorizontal),
                )
            }
        }
        return
    }

    val definition = goal ?: source
    val retryDuration = source?.let { it.endDate.toEpochDay() - it.startDate.toEpochDay() }
    val initialStartDate = goal?.startDate ?: LocalDate.now()
    val initialEndDate = goal?.endDate
        ?: retryDuration?.let { LocalDate.now().plusDays(it) }
        ?: LocalDate.now().plusYears(1)
    var title by rememberSaveable(goal?.id, source?.id) { mutableStateOf(definition?.title.orEmpty()) }
    var description by rememberSaveable(goal?.id, source?.id) { mutableStateOf(definition?.description.orEmpty()) }
    var unit by rememberSaveable(goal?.id, source?.id) { mutableStateOf(definition?.unit ?: "次") }
    var defaultValue by rememberSaveable(goal?.id, source?.id) {
        mutableStateOf(definition?.defaultValue?.let(::formatValue).orEmpty())
    }
    var stepValue by rememberSaveable(goal?.id, source?.id) {
        mutableStateOf(definition?.stepValue?.let(::formatValue) ?: "1")
    }
    var startDateText by rememberSaveable(goal?.id, source?.id) { mutableStateOf(initialStartDate.toString()) }
    var endDateText by rememberSaveable(goal?.id, source?.id) { mutableStateOf(initialEndDate.toString()) }
    var subgoals by remember(goal?.id, source?.id) {
        mutableStateOf(
            definition?.subgoals?.map {
                EditableSubgoal(it.id.takeIf { goal != null }, it.title, formatValue(it.targetValue))
            } ?: listOf(EditableSubgoal(null, "", "")),
        )
    }
    var saving by rememberSaveable(goal?.id) { mutableStateOf(false) }
    var showErrors by rememberSaveable(goal?.id) { mutableStateOf(false) }
    var errorMessage by rememberSaveable(goal?.id) { mutableStateOf<String?>(null) }
    val locked = goal?.currentValue?.let { it > 0 } == true
    val startDate = LocalDate.parse(startDateText)
    val endDate = LocalDate.parse(endDateText)
    val requiredFieldTemplate = stringResource(R.string.error_required_field)
    val positiveFieldTemplate = stringResource(R.string.error_positive_field)
    val checkFieldsMessage = stringResource(R.string.error_check_fields)
    val titleLabel = stringResource(R.string.field_title)
    val unitLabel = stringResource(R.string.field_unit)
    val defaultLabel = stringResource(R.string.field_default)
    val stepLabel = stringResource(R.string.field_step)
    val subgoalLabel = stringResource(R.string.goal_subgoal_name)
    val targetLabel = stringResource(R.string.field_goal_value)
    val dateOrderMessage = stringResource(R.string.goal_date_order_error)

    fun requiredMessage(label: String) = String.format(Locale.getDefault(), requiredFieldTemplate, label)
    fun positiveMessage(label: String) = String.format(Locale.getDefault(), positiveFieldTemplate, label)

    val titleError = requiredMessage(titleLabel).takeIf { showErrors && title.isBlank() }
    val unitError = requiredMessage(unitLabel).takeIf { showErrors && unit.isBlank() }
    val defaultError = positiveMessage(defaultLabel).takeIf {
        showErrors && runCatching { parseOptionalPositive(defaultValue, "Default value") }.isFailure
    }
    val stepError = positiveMessage(stepLabel).takeIf {
        showErrors && runCatching { parsePositive(stepValue, "Step") }.isFailure
    }
    val dateError = dateOrderMessage.takeIf { showErrors && startDate > endDate }

    fun validSubgoal(subgoal: EditableSubgoal): Boolean =
        subgoal.title.isNotBlank() && runCatching { parsePositive(subgoal.target, "Target") }.isSuccess

    val formValid = title.isNotBlank() &&
        unit.isNotBlank() &&
        runCatching { parseOptionalPositive(defaultValue, "Default value") }.isSuccess &&
        runCatching { parsePositive(stepValue, "Step") }.isSuccess &&
        startDate <= endDate &&
        subgoals.isNotEmpty() &&
        subgoals.all(::validSubgoal)

    fun submit() {
        showErrors = true
        errorMessage = null
        if (!formValid) {
            errorMessage = checkFieldsMessage
            return
        }
        val draft = GoalPoolDraft(
            title = title,
            description = description,
            unit = unit,
            defaultValue = parseOptionalPositive(defaultValue, "Default value"),
            stepValue = parsePositive(stepValue, "Step"),
            startDate = startDate,
            endDate = endDate,
            subgoals = subgoals.map {
                GoalSubgoalDraft(it.id, it.title, parsePositive(it.target, "Target"))
            },
        )
        saving = true
        onSave(draft) { message ->
            saving = false
            errorMessage = message
        }
    }

    AppRouteScaffold(
        title = stringResource(if (editing) R.string.edit_goal else R.string.new_goal),
        onBack = onBack,
        actions = {
            FilledTonalIconButton(
                enabled = !saving,
                onClick = ::submit,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(if (saving) R.string.saving else R.string.action_save),
                )
            }
        },
    ) {
        if (title.isNotBlank()) {
            val colors = recordProgressColors(progress = 0f)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppStyle.spacing.pageHorizontal)
                    .padding(bottom = 8.dp),
                shape = AppStyle.cardShape,
                color = colors.container,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecordItemInitial(name = title, size = 48.dp, colors = colors)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayRecordName(title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(if (editing) R.string.edit_goal else R.string.new_goal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        AppSection(title = stringResource(R.string.record_details)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !saving,
                label = { Text(titleLabel) },
                isError = titleError != null,
                supportingText = titleError?.let { message -> { Text(message) } },
                singleLine = true,
                modifier = Modifier.formField(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                enabled = !saving,
                label = { Text(stringResource(R.string.field_description)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.formField(),
            )
        }

        AppSection(title = stringResource(R.string.record_measurement)) {
            Row(
                modifier = Modifier.formField(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    enabled = !saving,
                    label = { Text(defaultLabel) },
                    isError = defaultError != null,
                    supportingText = defaultError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = stepValue,
                    onValueChange = { stepValue = it },
                    enabled = !locked && !saving,
                    label = { Text(stepLabel) },
                    isError = stepError != null,
                    supportingText = stepError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                enabled = !locked && !saving,
                label = { Text(unitLabel) },
                isError = unitError != null,
                supportingText = unitError?.let { message -> { Text(message) } },
                singleLine = true,
                modifier = Modifier.formField(),
            )
        }

        AppSection(title = stringResource(R.string.goal_date_range)) {
            DateButton(
                label = stringResource(R.string.goal_start_date),
                date = startDate,
                enabled = !locked && !saving,
                onDate = { startDateText = it.toString() },
                modifier = Modifier.formField(),
            )
            DateButton(
                label = stringResource(R.string.goal_end_date),
                date = endDate,
                enabled = !locked && !saving,
                errorMessage = dateError,
                onDate = { endDateText = it.toString() },
                modifier = Modifier.formField(),
            )
        }

        AppSection(title = stringResource(R.string.goal_subgoals)) {
            subgoals.forEachIndexed { index, subgoal ->
                val subgoalTitleError = requiredMessage(subgoalLabel).takeIf {
                    showErrors && subgoal.title.isBlank()
                }
                val subgoalTargetError = positiveMessage(targetLabel).takeIf {
                    showErrors && runCatching { parsePositive(subgoal.target, "Target") }.isFailure
                }
                Row(
                    modifier = Modifier.formField(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = subgoal.title,
                        onValueChange = { value ->
                            subgoals = subgoals.toMutableList().also { it[index] = subgoal.copy(title = value) }
                        },
                        enabled = !locked && !saving,
                        label = { Text(subgoalLabel) },
                        isError = subgoalTitleError != null,
                        supportingText = subgoalTitleError?.let { message -> { Text(message) } },
                        singleLine = true,
                        modifier = Modifier.weight(1.35f),
                    )
                    OutlinedTextField(
                        value = subgoal.target,
                        onValueChange = { value ->
                            subgoals = subgoals.toMutableList().also { it[index] = subgoal.copy(target = value) }
                        },
                        enabled = !locked && !saving,
                        label = { Text(targetLabel) },
                        isError = subgoalTargetError != null,
                        supportingText = subgoalTargetError?.let { message -> { Text(message) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (!locked && subgoals.size > 1) {
                        IconButton(
                            enabled = !saving,
                            onClick = {
                                subgoals = subgoals.filterIndexed { itemIndex, _ -> itemIndex != index }
                            },
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.remove_goal_subgoal))
                        }
                    }
                }
            }
            if (!locked) {
                OutlinedButton(
                    onClick = { subgoals = subgoals + EditableSubgoal(null, "", "") },
                    enabled = !saving,
                    modifier = Modifier.formField(),
                ) {
                    Icon(painterResource(R.drawable.ic_add_24), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_goal_subgoal))
                }
            }
            if (locked) {
                Text(
                    text = stringResource(R.string.goal_definition_locked),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.formField(),
                )
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.formField(),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(
    label: String,
    date: LocalDate,
    enabled: Boolean,
    onDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier) {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            border = BorderStroke(
                width = 1.dp,
                color = if (errorMessage == null) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.error
                },
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Text(date.toString())
        }
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        open = false
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
