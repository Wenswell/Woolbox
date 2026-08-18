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
import com.wswensw.woolbox.record.model.CommitmentRole
import com.wswensw.woolbox.record.model.DailyPlan
import com.wswensw.woolbox.record.model.DailyPlanState
import com.wswensw.woolbox.record.model.TaskTemplate
import com.wswensw.woolbox.record.model.validatedFirstMove
import com.wswensw.woolbox.ui.AppListItem
import com.wswensw.woolbox.ui.AppRouteScaffold
import com.wswensw.woolbox.ui.AppSection
import com.wswensw.woolbox.ui.AppStyle

private enum class PlanSlot { PRIMARY, SECONDARY }

@Composable
fun DailyPlanScreen(
    plan: DailyPlan?,
    templates: List<TaskTemplate>,
    onBack: () -> Unit,
    onSave: (String, Long, Long?, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val existingPrimaryId = plan?.commitments
        ?.firstOrNull { it.role == CommitmentRole.PRIMARY }
        ?.templateId
    val existingSecondaryId = plan?.commitments
        ?.firstOrNull { it.role == CommitmentRole.SECONDARY }
        ?.templateId
    val locked = plan != null && plan.state != DailyPlanState.READY

    var firstMove by rememberSaveable(plan?.id) { mutableStateOf(plan?.firstMove.orEmpty()) }
    var primaryTemplateId by rememberSaveable(plan?.id) { mutableStateOf(existingPrimaryId) }
    var secondaryTemplateId by rememberSaveable(plan?.id) { mutableStateOf(existingSecondaryId) }
    var choosingSlot by rememberSaveable { mutableStateOf<PlanSlot?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val primary = templates.firstOrNull { it.id == primaryTemplateId }
    val secondary = templates.firstOrNull { it.id == secondaryTemplateId }
    val primaryRequired = stringResource(R.string.primary_template_required)
    val commitmentsMustDiffer = stringResource(R.string.templates_must_differ)

    fun submit() {
        val normalizedMove = runCatching { validatedFirstMove(firstMove) }.getOrElse {
            error = it.message
            return
        }
        val primaryId = primaryTemplateId ?: run {
            error = primaryRequired
            return
        }
        if (primaryId == secondaryTemplateId) {
            error = commitmentsMustDiffer
            return
        }
        saving = true
        error = null
        onSave(normalizedMove, primaryId, secondaryTemplateId) { result ->
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
                enabled = !locked && !saving && firstMove.isNotBlank() && primaryTemplateId != null,
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
                enabled = !saving && !locked,
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
                title = stringResource(R.string.primary_commitment),
                subtitle = primary?.title ?: stringResource(R.string.choose_template),
                enabled = !saving && !locked,
                navigate = !locked,
                onClick = if (locked) null else { { choosingSlot = PlanSlot.PRIMARY } },
            )
            AppListItem(
                title = stringResource(R.string.secondary_commitment),
                subtitle = secondary?.title ?: stringResource(R.string.no_secondary_commitment),
                enabled = !saving && !locked,
                navigate = !locked,
                onClick = if (locked) null else { { choosingSlot = PlanSlot.SECONDARY } },
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
        val unavailableId = if (slot == PlanSlot.PRIMARY) secondaryTemplateId else primaryTemplateId
        TemplateChoiceDialog(
            title = stringResource(
                if (slot == PlanSlot.PRIMARY) R.string.primary_commitment else R.string.secondary_commitment,
            ),
            templates = templates.filter { it.id != unavailableId },
            onDismiss = { choosingSlot = null },
            onSelect = { template ->
                if (slot == PlanSlot.PRIMARY) {
                    primaryTemplateId = template.id
                } else {
                    secondaryTemplateId = template.id
                }
                choosingSlot = null
                error = null
            },
            onClear = if (slot == PlanSlot.SECONDARY) {
                {
                    secondaryTemplateId = null
                    choosingSlot = null
                    error = null
                }
            } else {
                null
            },
        )
    }
}
