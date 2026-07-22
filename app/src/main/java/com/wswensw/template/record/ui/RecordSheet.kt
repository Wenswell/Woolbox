package com.wswensw.template.record.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.stepRecordValue
import com.wswensw.template.ui.TemplateStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    item: RecordItem,
    entries: List<RecordEntry>,
    onDismiss: () -> Unit,
    onRecord: (RecordItem, Double, (String?) -> Unit) -> Unit,
) {
    var valueText by rememberSaveable(item.id) {
        mutableStateOf(item.defaultValue?.let(::formatValue).orEmpty())
    }
    var saving by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TemplateStyle.spacing.pageHorizontal)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordItemInitial(item.name)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item.goalValue?.let { goal ->
                if (goal > 0) {
                    Spacer(Modifier.height(8.dp))
                    GoalProgress(item = item, cumulative = entries.sumOf { it.value }, goal = goal)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (entries.isNotEmpty()) {
                Text(
                    text = "Today",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        EntryRow(entry = entry)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            ValueInput(
                item = item,
                valueText = valueText,
                saving = saving,
                errorMessage = errorMessage,
                onValueChange = { valueText = it },
                onConfirm = {
                    val value = valueText.trim().toDoubleOrNull()
                    if (value == null || !value.isFinite() || value <= 0) {
                        errorMessage = "Value must be a positive number."
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
        }
    }
}

@Composable
private fun GoalProgress(item: RecordItem, cumulative: Double, goal: Double) {
    LinearProgressIndicator(
        progress = { (cumulative / goal).coerceIn(0.0, 1.0).toFloat() },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = "${formatValue(cumulative)} / ${formatValue(goal)} ${item.unit}" +
            " · ${if (item.goalPeriod == GoalPeriod.DAY) "today" else "this week"}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun EntryRow(entry: RecordEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMinute(entry.localMinute),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${formatValue(entry.value)} ${entry.itemUnit}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ValueInput(
    item: RecordItem,
    valueText: String,
    saving: Boolean,
    errorMessage: String?,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = { onValueChange(stepRecordValue(valueText, item.stepValue, -1)) },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_remove_24),
                contentDescription = "Decrease by ${formatValue(item.stepValue)}",
            )
        }
        OutlinedTextField(
            value = valueText,
            onValueChange = onValueChange,
            label = { Text("Value") },
            suffix = { Text(item.unit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onValueChange(stepRecordValue(valueText, item.stepValue, 1)) },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_24),
                contentDescription = "Increase by ${formatValue(item.stepValue)}",
            )
        }
    }
    Text(
        text = "Step ${formatValue(item.stepValue)} ${item.unit}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 56.dp, top = 4.dp),
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
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (saving) "Saving" else "Record")
    }
}

private fun formatMinute(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minute / 60,
    minute % 60,
)
