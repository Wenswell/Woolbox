package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.DailyPlan
import com.wswensw.woolbox.record.model.DailyPlanState
import com.wswensw.woolbox.record.model.TodoTask
import com.wswensw.woolbox.record.model.validatedFirstMove
import com.wswensw.woolbox.ui.AppListItem
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle

private enum class PlanSlot { PRIMARY, SECONDARY }

@Composable
fun DailyPlanScreen(
    plan: DailyPlan?,
    tasks: List<TodoTask>,
    onBack: () -> Unit,
    onSave: (String, Long, Long?, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openTasks = tasks.filterNot(TodoTask::isCompleted)
    val existingPrimary = tasks.firstOrNull { it.id == plan?.primaryTaskId }
    val existingSecondary = tasks.firstOrNull { it.id == plan?.secondaryTaskId }
    val primaryLocked = existingPrimary?.isCompleted == true
    val secondaryLocked = existingSecondary?.isCompleted == true
    val firstMoveLocked = plan?.state == DailyPlanState.ACTIVE

    var firstMove by rememberSaveable(plan?.id) { mutableStateOf(plan?.firstMove.orEmpty()) }
    var primaryTaskId by rememberSaveable(plan?.id) { mutableStateOf(plan?.primaryTaskId) }
    var secondaryTaskId by rememberSaveable(plan?.id) { mutableStateOf(plan?.secondaryTaskId) }
    var choosingSlot by rememberSaveable { mutableStateOf<PlanSlot?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val primary = tasks.firstOrNull { it.id == primaryTaskId }
    val secondary = tasks.firstOrNull { it.id == secondaryTaskId }
    val primaryRequired = stringResource(R.string.primary_task_required)
    val commitmentsMustDiffer = stringResource(R.string.commitments_must_differ)

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
            error = commitmentsMustDiffer
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
        title = stringResource(if (plan == null) R.string.plan_today else R.string.edit_today),
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(
                onClick = ::submit,
                enabled = !saving && firstMove.isNotBlank() && primaryTaskId != null,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(if (saving) R.string.saving else R.string.action_save),
                )
            }
        },
    ) {
        AppSection(title = stringResource(R.string.first_move)) {
            OutlinedTextField(
                value = firstMove,
                onValueChange = { firstMove = it; error = null },
                enabled = !saving && !firstMoveLocked,
                label = { Text(stringResource(R.string.first_move_label)) },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = AppStyle.spacing.pageHorizontal,
                    vertical = 8.dp,
                ),
            )
        }
        AppSection(title = stringResource(R.string.today_commitments)) {
            AppListItem(
                title = stringResource(R.string.primary_task),
                subtitle = primary?.title ?: stringResource(R.string.choose_task),
                enabled = !saving && !primaryLocked,
                navigate = !primaryLocked,
                onClick = if (primaryLocked) null else { { choosingSlot = PlanSlot.PRIMARY } },
            )
            AppListItem(
                title = stringResource(R.string.secondary_task),
                subtitle = secondary?.title ?: stringResource(R.string.no_secondary_task),
                enabled = !saving && !secondaryLocked,
                navigate = !secondaryLocked,
                onClick = if (secondaryLocked) null else { { choosingSlot = PlanSlot.SECONDARY } },
            )
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
        val unavailableId = if (slot == PlanSlot.PRIMARY) secondaryTaskId else primaryTaskId
        TaskChoiceDialog(
            title = stringResource(if (slot == PlanSlot.PRIMARY) R.string.primary_task else R.string.secondary_task),
            tasks = openTasks.filter { it.id != unavailableId },
            onDismiss = { choosingSlot = null },
            onSelect = { task ->
                if (slot == PlanSlot.PRIMARY) primaryTaskId = task.id else secondaryTaskId = task.id
                choosingSlot = null
                error = null
            },
            onClear = if (slot == PlanSlot.SECONDARY) {
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
