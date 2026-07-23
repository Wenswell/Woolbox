package com.wswensw.template.record.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.measurementStepCount
import com.wswensw.template.record.model.snapMeasurementValue
import com.wswensw.template.ui.TemplateStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementPreviewSheet(
    item: RecordItem,
    state: MeasurementPreviewUiState,
    onDismiss: () -> Unit,
    onRecord: (Double, (String?) -> Unit) -> Unit,
) {
    val minValue = requireNotNull(item.minValue)
    val maxValue = requireNotNull(item.maxValue)
    val stepCount = measurementStepCount(minValue, maxValue, item.stepValue)
    var value by rememberSaveable(item.id) { mutableStateOf(minValue) }
    var saving by rememberSaveable(item.id) { mutableStateOf(false) }
    var errorMessage by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    val sortedEntries = state.entries.sortedBy(RecordEntry::occurredAt)
    val latest = sortedEntries.lastOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TemplateStyle.spacing.pageHorizontal)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = displayRecordName(item.name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            latest?.let { entry ->
                Text(
                    text = "${formatValue(entry.value)} ${item.unit} · ${measurementDateTimeLabel(entry.occurredAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } ?: Text(
                text = "No measurements in the last 24 hours.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Record",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            MeasurementValueSlider(
                value = value,
                minValue = minValue,
                maxValue = maxValue,
                stepCount = stepCount,
                unit = item.unit,
                enabled = !saving,
                onValueChange = { value = snapMeasurementValue(it, minValue, maxValue, item.stepValue) },
            )
            Button(
                onClick = {
                    saving = true
                    errorMessage = null
                    onRecord(value) { error ->
                        saving = false
                        errorMessage = error
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Recording" else "Record")
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = "Last 24 hours",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.error != null -> Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                sortedEntries.isNotEmpty() -> MeasurementPreviewChart(entries = sortedEntries, unit = item.unit)
            }

            if (sortedEntries.isNotEmpty()) {
                Text(
                    text = "Entries",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 248.dp)) {
                    items(sortedEntries.reversed(), key = RecordEntry::id) { entry ->
                        MeasurementPreviewEntryRow(entry = entry, unit = item.unit)
                        if (entry.id != sortedEntries.first().id) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementValueSlider(
    value: Double,
    minValue: Double,
    maxValue: Double,
    stepCount: Int,
    unit: String,
    enabled: Boolean,
    onValueChange: (Double) -> Unit,
) {
    Column {
        Text(
            text = "${formatValue(value)} $unit",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = minValue.toFloat()..maxValue.toFloat(),
            steps = stepCount - 1,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatValue(minValue),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatValue(maxValue),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MeasurementPreviewChart(entries: List<RecordEntry>, unit: String) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val axisFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            Instant.ofEpochMilli(value.toLong())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        }
    }
    val markerLabel = rememberTextComponent(
        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onPrimaryContainer),
        padding = Insets(horizontal = 8.dp, vertical = 4.dp),
        background = rememberShapeComponent(
            fill = Fill(MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(6.dp),
        ),
    )
    val marker = rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = remember(entries, unit) {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                val timestamp = targets.firstOrNull()?.x?.toLong()
                val entry = timestamp?.let { selected -> entries.minByOrNull { kotlin.math.abs(it.occurredAt - selected) } }
                entry?.let { "${measurementDateTimeLabel(it.occurredAt)}\n${formatValue(it.value)} $unit" }.orEmpty()
            }
        },
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
    )
    LaunchedEffect(entries) {
        modelProducer.runTransaction {
            lineModel {
                series(x = entries.map(RecordEntry::occurredAt), y = entries.map(RecordEntry::value))
            }
        }
    }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(valueFormatter = CartesianValueFormatter.decimal(suffix = " $unit")),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = axisFormatter),
                marker = marker,
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp),
        )
    }
}

@Composable
private fun MeasurementPreviewEntryRow(entry: RecordEntry, unit: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatValue(entry.value)} $unit",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = measurementDateTimeLabel(entry.occurredAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun measurementDateTimeLabel(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault()))
