package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordSuggestion
import com.wswensw.template.record.model.getRecordSuggestions
import com.wswensw.template.record.model.stepRecordValue
import com.wswensw.template.ui.TemplateListItem
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    items: List<RecordItem>,
    onDismiss: () -> Unit,
    onRecord: (RecordItem, Double, (String?) -> Unit) -> Unit,
) {
    var selectedItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var valueText by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val currentMinute = remember {
        val time = LocalTime.now()
        time.hour * 60 + time.minute
    }
    val selectedItem = items.firstOrNull { it.id == selectedItemId }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (selectedItem == null) {
            RecordItemPicker(
                items = items,
                query = query,
                currentMinute = currentMinute,
                onQueryChange = { query = it },
                onSelect = { item ->
                    selectedItemId = item.id
                    valueText = item.defaultValue?.let(::formatValue).orEmpty()
                    errorMessage = null
                },
            )
        } else {
            RecordValueConfirmation(
                item = selectedItem,
                valueText = valueText,
                saving = saving,
                errorMessage = errorMessage,
                onValueChange = { valueText = it },
                onBack = {
                    selectedItemId = null
                    errorMessage = null
                },
                onConfirm = {
                    val value = valueText.trim().toDoubleOrNull()
                    if (value == null || !value.isFinite() || value <= 0) {
                        errorMessage = "Value must be a positive number."
                    } else {
                        saving = true
                        errorMessage = null
                        onRecord(selectedItem, value) { error ->
                            saving = false
                            errorMessage = error
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun RecordItemPicker(
    items: List<RecordItem>,
    query: String,
    currentMinute: Int,
    onQueryChange: (String) -> Unit,
    onSelect: (RecordItem) -> Unit,
) {
    val suggestions = remember(items, query, currentMinute) {
        getRecordSuggestions(
            items = items,
            query = query,
            currentMinute = currentMinute,
            limit = if (query.isBlank()) 5 else items.size,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Record",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search record items") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (suggestions.isEmpty()) {
            Text(
                text = "No matching record items.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            Text(
                text = if (query.isBlank()) "Suggested now" else "Results",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(suggestions, key = { it.item.id }) { suggestion ->
                    TemplateListItem(
                        title = suggestion.item.name,
                        subtitle = suggestion.subtitle(),
                        onClick = { onSelect(suggestion.item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordValueConfirmation(
    item: RecordItem,
    valueText: String,
    saving: Boolean,
    errorMessage: String?,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
        Text(text = item.name, style = MaterialTheme.typography.headlineSmall)
        if (item.description.isNotEmpty()) {
            Text(
                text = item.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item.goalSummary()?.let { goal ->
            Text(
                text = goal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(20.dp))
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
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onConfirm,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Saving" else "Record")
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun RecordSuggestion.subtitle(): String {
    val time = matchingWindow?.let { window ->
        "${formatMinute(window.startMinute)}-${formatMinute(window.endMinute)}"
    } ?: "Anytime"
    val value = item.defaultValue?.let { " · ${formatValue(it)} ${item.unit}" }.orEmpty()
    val goal = item.goalSummary()?.let { " · $it" }.orEmpty()
    return "$time$value$goal"
}

private fun formatMinute(minute: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minute / 60,
    minute % 60,
)
