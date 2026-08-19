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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
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
                val closed = goal.completed || goal.expired()
                Surface(
                    onClick = { onEdit(goal.id) },
                    enabled = !closed,
                    shape = AppStyle.cardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppStyle.spacing.pageHorizontal)
                        .padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                goal.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${formatValue(goal.currentValue)} ${goal.unit}", color = MaterialTheme.colorScheme.primary)
                            if (closed) {
                                IconButton(onClick = { onRetry(goal.id) }) {
                                    Icon(Icons.Rounded.Replay, contentDescription = stringResource(R.string.retry_goal))
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.goal_subgoal_progress, goal.subgoals.count { it.completedAt != null }, goal.subgoals.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
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
    var value by remember(goal.id) { mutableStateOf(goal.defaultValue?.let(::formatValue).orEmpty()) }
    var saving by remember(goal.id) { mutableStateOf(false) }
    var error by remember(goal.id) { mutableStateOf<String?>(null) }
    val pending = goal.pending()
    val expired = goal.expired()
    val parsed = value.trim().toDoubleOrNull()
    val canRecord = !goal.completed && !pending && !expired && !saving && parsed != null && parsed.isFinite() && parsed > 0
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = AppStyle.spacing.pageHorizontal).padding(bottom = 28.dp),
        ) {
            Text(goal.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (goal.description.isNotEmpty()) {
                Text(goal.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                stringResource(R.string.goal_shared_progress, formatValue(goal.currentValue), goal.unit),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (pending) {
                Text(stringResource(R.string.goal_pending, goal.startDate.toString()), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            } else if (expired) {
                Text(stringResource(R.string.goal_expired), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            } else if (goal.completed) {
                Text(stringResource(R.string.goal_completed), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = null },
                label = { Text(stringResource(R.string.field_value)) },
                suffix = { Text(goal.unit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            Button(
                enabled = canRecord,
                onClick = {
                    val amount = parsed ?: return@Button
                    saving = true
                    onRecord(amount) { message ->
                        saving = false
                        error = message
                        if (message == null) value = goal.defaultValue?.let(::formatValue).orEmpty()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add_24), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_record))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.goal_subgoals), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Surface(
                shape = AppStyle.cardShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column {
                    goal.subgoals.forEachIndexed { index, subgoal ->
                        val eligible = goal.currentValue >= subgoal.targetValue
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = subgoal.completedAt != null,
                                enabled = !goal.completed && !pending && !expired && (eligible || subgoal.completedAt != null),
                                onCheckedChange = { completed ->
                                    onToggleSubgoal(subgoal.id, completed) { message -> error = message }
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(subgoal.title, fontWeight = FontWeight.Medium)
                                Text(
                                    if (eligible) stringResource(R.string.goal_ready_to_complete) else stringResource(
                                        R.string.goal_remaining,
                                        formatValue(subgoal.targetValue - goal.currentValue),
                                        goal.unit,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("${formatValue(subgoal.targetValue)} ${goal.unit}", style = MaterialTheme.typography.labelLarge)
                        }
                        if (index < goal.subgoals.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    }
                }
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
            if (loading) CircularProgressIndicator(Modifier.padding(AppStyle.spacing.pageHorizontal))
            else Text(stringResource(R.string.goal_not_found), Modifier.padding(AppStyle.spacing.pageHorizontal))
        }
        return
    }
    val definition = goal ?: source
    val retryDuration = source?.let { it.endDate.toEpochDay() - it.startDate.toEpochDay() }
    var title by remember(goal?.id, source?.id) { mutableStateOf(definition?.title.orEmpty()) }
    var description by remember(goal?.id, source?.id) { mutableStateOf(definition?.description.orEmpty()) }
    var unit by remember(goal?.id, source?.id) { mutableStateOf(definition?.unit ?: "次") }
    var defaultValue by remember(goal?.id, source?.id) { mutableStateOf(definition?.defaultValue?.let(::formatValue).orEmpty()) }
    var stepValue by remember(goal?.id, source?.id) { mutableStateOf(definition?.stepValue?.let(::formatValue) ?: "1") }
    var startDate by remember(goal?.id, source?.id) { mutableStateOf(goal?.startDate ?: LocalDate.now()) }
    var endDate by remember(goal?.id, source?.id) {
        mutableStateOf(goal?.endDate ?: retryDuration?.let { LocalDate.now().plusDays(it) } ?: LocalDate.now().plusYears(1))
    }
    var subgoals by remember(goal?.id, source?.id) {
        mutableStateOf(definition?.subgoals?.map { EditableSubgoal(it.id.takeIf { goal != null }, it.title, formatValue(it.targetValue)) }
            ?: listOf(EditableSubgoal(null, "", "")))
    }
    var saving by remember(goal?.id) { mutableStateOf(false) }
    var error by remember(goal?.id) { mutableStateOf<String?>(null) }
    val locked = goal?.currentValue?.let { it > 0 } == true
    AppRouteScaffold(
        title = stringResource(if (editing) R.string.edit_goal else R.string.new_goal),
        onBack = onBack,
        actions = {
            IconButton(
                enabled = !saving,
                onClick = {
                    val draft = runCatching {
                        GoalPoolDraft(
                            title = title,
                            description = description,
                            unit = unit,
                            defaultValue = defaultValue.trim().takeIf(String::isNotEmpty)?.toDouble(),
                            stepValue = stepValue.trim().toDouble(),
                            startDate = startDate,
                            endDate = endDate,
                            subgoals = subgoals.map { GoalSubgoalDraft(it.id, it.title, it.target.trim().toDouble()) },
                        )
                    }.getOrElse { failure -> error = failure.message; return@IconButton }
                    saving = true
                    onSave(draft) { message -> saving = false; error = message }
                },
            ) { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_save)) }
        },
    ) {
        AppSection(title = stringResource(R.string.record_details)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.field_description)) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(unit, { unit = it }, enabled = !locked, label = { Text(stringResource(R.string.field_unit)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(stepValue, { stepValue = it }, enabled = !locked, label = { Text(stringResource(R.string.field_step)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(defaultValue, { defaultValue = it }, label = { Text(stringResource(R.string.field_default)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        AppSection(title = stringResource(R.string.goal_date_range)) {
            DateButton(stringResource(R.string.goal_start_date), startDate, !locked) { startDate = it }
            DateButton(stringResource(R.string.goal_end_date), endDate, !locked) { endDate = it }
        }
        AppSection(title = stringResource(R.string.goal_subgoals)) {
            subgoals.forEachIndexed { index, subgoal ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        subgoal.title,
                        { value -> subgoals = subgoals.toMutableList().also { it[index] = subgoal.copy(title = value) } },
                        enabled = !locked,
                        label = { Text(stringResource(R.string.goal_subgoal_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                    )
                    OutlinedTextField(
                        subgoal.target,
                        { value -> subgoals = subgoals.toMutableList().also { it[index] = subgoal.copy(target = value) } },
                        enabled = !locked,
                        label = { Text(stringResource(R.string.field_goal_value)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (!locked && subgoals.size > 1) {
                        IconButton(onClick = { subgoals = subgoals.filterIndexed { itemIndex, _ -> itemIndex != index } }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.remove_goal_subgoal))
                        }
                    }
                }
            }
            if (!locked) {
                OutlinedButton(
                    onClick = { subgoals = subgoals + EditableSubgoal(null, "", "") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.add_goal_subgoal)) }
            }
            if (locked) Text(stringResource(R.string.goal_definition_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal)) }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(label: String, date: LocalDate, enabled: Boolean, onDate: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(date.toString())
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}
