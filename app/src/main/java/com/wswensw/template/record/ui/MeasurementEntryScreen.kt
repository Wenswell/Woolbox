package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.stepMeasurementValue
import com.wswensw.template.ui.TemplateRouteScaffold
import com.wswensw.template.ui.TemplateSection
import com.wswensw.template.ui.TemplateStyle

@Composable
fun MeasurementEntryScreen(
    item: RecordItem,
    onBack: () -> Unit,
    onSave: (value: Double, onResult: (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by rememberSaveable(item.id) {
        mutableStateOf(item.defaultValue?.let(::formatValue).orEmpty())
    }
    var valueError by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        val parsed = value.trim().toDoubleOrNull()
        if (parsed == null || !parsed.isFinite()) {
            valueError = "Value must be a finite number."
            return
        }
        saving = true
        errorMessage = null
        onSave(parsed) { error ->
            saving = false
            errorMessage = error
        }
    }

    TemplateRouteScaffold(
        title = "New measurement",
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = ::submit, enabled = !saving) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = if (saving) "Saving" else "Save",
                )
            }
        },
    ) {
        TemplateSection(title = displayRecordName(item.name)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { value = stepMeasurementValue(value, item.stepValue, -1) },
                    enabled = !saving,
                ) {
                    Icon(imageVector = Icons.Rounded.Remove, contentDescription = "Decrease value")
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        valueError = null
                    },
                    enabled = !saving,
                    label = { Text(item.unit) },
                    isError = valueError != null,
                    supportingText = valueError?.let { message -> { Text(message) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { value = stepMeasurementValue(value, item.stepValue, 1) },
                    enabled = !saving,
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = "Increase value")
                }
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
