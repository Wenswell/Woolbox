package com.wswensw.woolbox.record.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.stepRecordValue
import com.wswensw.woolbox.ui.AppStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    item: RecordItem,
    entries: List<RecordEntry>,
    cumulative: Double,
    onDismiss: () -> Unit,
    onRecord: (RecordItem, Double, (String?) -> Unit) -> Unit,
) {
    var valueText by rememberSaveable(item.id) {
        mutableStateOf(item.defaultValue?.let(::formatValue).orEmpty())
    }
    var saving by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val positiveValueMessage = stringResource(R.string.error_positive_value)
    val parsedValue = valueText.trim().toDoubleOrNull()
    val canConfirm = !saving &&
        parsedValue != null &&
        parsedValue.isFinite() &&
        parsedValue > 0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppStyle.spacing.pageHorizontal)
                .padding(bottom = 28.dp),
        ) {
            RecordSheetIdentity(
                item = item,
                cumulative = cumulative,
            )
            Spacer(Modifier.height(20.dp))
            RecordValueInput(
                unit = item.unit,
                stepValue = item.stepValue,
                valueText = valueText,
                saving = saving,
                canConfirm = canConfirm,
                errorMessage = errorMessage,
                onValueChange = {
                    valueText = it
                    errorMessage = null
                },
                onConfirm = {
                    val value = valueText.trim().toDoubleOrNull()
                    if (value == null || !value.isFinite() || value <= 0) {
                        errorMessage = positiveValueMessage
                    } else {
                        saving = true
                        errorMessage = null
                        onRecord(item, value) { error ->
                            saving = false
                            if (error == null) {
                                valueText = item.defaultValue?.let(::formatValue).orEmpty()
                            } else {
                                errorMessage = error
                            }
                        }
                    }
                },
            )
            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.today),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppStyle.cardShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 168.dp)) {
                        items(
                            count = entries.size,
                            key = { index -> entries[index].id },
                        ) { index ->
                            EntryRow(entry = entries[index])
                            if (index < entries.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: RecordEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMinute(entry.localMinute),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${formatValue(entry.value)} ${entry.itemUnit}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}


@Composable
private fun RecordSheetIdentity(
    item: RecordItem,
    cumulative: Double,
) {
    val target = item.goalValue
    val period = item.goalPeriod
    val hasGoal = target != null && target > 0 && period != null
    val progress = if (hasGoal) {
        goalProgressRatio(cumulative, checkNotNull(target)).toFloat()
    } else {
        0f
    }
    val reached = hasGoal && cumulative >= checkNotNull(target)
    val colors = recordProgressColors(progress = progress, completed = reached)
    val progressColor = colors.accent

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hasGoal && period == GoalPeriod.DAY) {
            DailyGoalMedallion(
                item = item,
                progress = progress,
                reached = reached,
                progressColor = progressColor,
                size = 56.dp,
                colors = colors,
            )
        } else {
            RecordItemInitial(name = item.name, size = 52.dp, colors = colors)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayRecordName(item.name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasGoal) {
                GoalProgressPanel(
                    item = item,
                    cumulative = cumulative,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun RecordValueInput(
    unit: String,
    stepValue: Double,
    valueText: String,
    enabled: Boolean = true,
    saving: Boolean,
    canConfirm: Boolean,
    errorMessage: String?,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val editable = enabled && !saving
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(
            enabled = editable,
            onClick = { onValueChange(stepRecordValue(valueText, stepValue, -1)) },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_remove_24),
                contentDescription = stringResource(R.string.decrease_by, formatValue(stepValue)),
            )
        }
        OutlinedTextField(
            value = valueText,
            onValueChange = onValueChange,
            enabled = editable,
            isError = errorMessage != null,
            label = { Text(stringResource(R.string.field_value)) },
            suffix = { Text(unit) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (canConfirm) onConfirm() },
            ),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            enabled = editable,
            onClick = { onValueChange(stepRecordValue(valueText, stepValue, 1)) },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_24),
                contentDescription = stringResource(R.string.increase_by, formatValue(stepValue)),
            )
        }
    }
    Text(
        text = stringResource(R.string.step_value_unit, formatValue(stepValue), unit),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    )
    errorMessage?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onConfirm,
        enabled = canConfirm,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add_24),
            contentDescription = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (saving) R.string.saving else R.string.action_record))
    }
}
