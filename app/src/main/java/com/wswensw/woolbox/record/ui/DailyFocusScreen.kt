package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.DailyFocus
import com.wswensw.woolbox.record.model.DailyFocusPauseReason
import com.wswensw.woolbox.record.model.DailyFocusState
import com.wswensw.woolbox.record.model.TodayTask
import com.wswensw.woolbox.record.model.validatedFirstMove
import com.wswensw.woolbox.ui.AppListItem
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle

private enum class FocusSlot { PRIMARY, SECONDARY }

@Composable
fun DailyFocusScreen(
    focus: DailyFocus?,
    tasks: List<TodayTask>,
    loading: Boolean,
    onBack: () -> Unit,
    onSave: (String, Long, Long?, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openTasks = tasks.filterNot(TodayTask::isCompleted)
    val paused = focus?.state == DailyFocusState.PAUSED
    val editableFirstMove = focus == null || focus.state == DailyFocusState.READY
    var firstMove by rememberSaveable(focus?.id) { mutableStateOf(focus?.firstMove.orEmpty()) }
    var primaryTaskId by rememberSaveable(focus?.id, focus?.revision) {
        mutableStateOf(focus?.primaryTaskId?.takeIf { id -> openTasks.any { it.id == id } })
    }
    var secondaryTaskId by rememberSaveable(focus?.id, focus?.revision) {
        mutableStateOf(focus?.secondaryTaskId?.takeIf { id -> openTasks.any { it.id == id } })
    }
    var choosingSlot by rememberSaveable { mutableStateOf<FocusSlot?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val primary = openTasks.firstOrNull { it.id == primaryTaskId }
    val secondary = openTasks.firstOrNull { it.id == secondaryTaskId }
    val primaryRequired = stringResource(R.string.primary_task_required)
    val tasksMustDiffer = stringResource(R.string.focus_tasks_must_differ)

    fun submit() {
        val normalizedMove = runCatching { validatedFirstMove(firstMove) }.getOrElse {
            error = it.message
            return
        }
        val primaryId = primaryTaskId ?: run {
            error = primaryRequired
            return
        }
        if (primaryId == secondaryTaskId) {
            error = tasksMustDiffer
            return
        }
        saving = true
        error = null
        onSave(normalizedMove, primaryId, secondaryTaskId) { result ->
            saving = false
            error = result
        }
    }

    AppRouteScaffold(
        title = stringResource(
            when {
                focus == null -> R.string.plan_focus
                paused && focus.pauseReason == DailyFocusPauseReason.TARGET_REACHED -> R.string.continue_focus
                else -> R.string.edit_focus
            },
        ),
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(
                onClick = ::submit,
                enabled = !loading && !saving && firstMove.isNotBlank() && primaryTaskId != null,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(if (saving) R.string.saving else R.string.action_save),
                )
            }
        },
    ) {
        if (loading) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@AppRouteScaffold
        }
        AppSection(title = stringResource(R.string.first_move)) {
            OutlinedTextField(
                value = firstMove,
                onValueChange = { firstMove = it; error = null },
                enabled = !saving && editableFirstMove,
                label = { Text(stringResource(R.string.first_move_label)) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = AppStyle.spacing.pageHorizontal,
                    vertical = 8.dp,
                ),
            )
        }
        AppSection(title = stringResource(R.string.focus_tasks)) {
            if (openTasks.isEmpty()) {
                Text(
                    stringResource(R.string.no_open_tasks_for_focus),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal, vertical = 12.dp),
                )
            } else {
                AppListItem(
                    title = stringResource(R.string.primary_focus),
                    subtitle = primary?.title ?: stringResource(R.string.choose_task),
                    enabled = !saving,
                    navigate = true,
                    onClick = { choosingSlot = FocusSlot.PRIMARY },
                )
                AppListItem(
                    title = stringResource(R.string.secondary_focus),
                    subtitle = secondary?.title ?: stringResource(R.string.no_secondary_task),
                    enabled = !saving,
                    navigate = true,
                    onClick = { choosingSlot = FocusSlot.SECONDARY },
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = AppStyle.spacing.pageHorizontal),
            )
        }
    }

    choosingSlot?.let { slot ->
        val unavailableId = if (slot == FocusSlot.PRIMARY) secondaryTaskId else primaryTaskId
        TodayTaskChoiceDialog(
            title = stringResource(if (slot == FocusSlot.PRIMARY) R.string.primary_focus else R.string.secondary_focus),
            tasks = openTasks.filter { it.id != unavailableId },
            onDismiss = { choosingSlot = null },
            onSelect = { task ->
                if (slot == FocusSlot.PRIMARY) primaryTaskId = task.id else secondaryTaskId = task.id
                choosingSlot = null
                error = null
            },
            onClear = if (slot == FocusSlot.SECONDARY) {
                {
                    secondaryTaskId = null
                    choosingSlot = null
                    error = null
                }
            } else {
                null
            },
        )
    }
}

@Composable
internal fun TodayTaskChoiceDialog(
    title: String,
    tasks: List<TodayTask>,
    onDismiss: () -> Unit,
    onSelect: (TodayTask) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(tasks, key = TodayTask::id) { task ->
                    TextButton(onClick = { onSelect(task) }, modifier = Modifier.fillMaxWidth()) {
                        Text(task.title, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            onClear?.let { clear ->
                TextButton(onClick = clear) { Text(stringResource(R.string.no_secondary_task)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
