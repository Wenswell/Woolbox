package com.wswensw.template.record.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.wswensw.template.R
import com.wswensw.template.record.model.MeasurementRange
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.ui.TemplateRouteScaffold
import com.wswensw.template.ui.TemplateSection
import com.wswensw.template.ui.TemplateStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MeasurementDetailScreen(
    item: RecordItem,
    state: MeasurementDetailUiState,
    onBack: () -> Unit,
    onEditItem: () -> Unit,
    onAddEntry: () -> Unit,
    onRangeChange: (MeasurementRange) -> Unit,
    onEditEntry: (Long) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    TemplateRouteScaffold(
        title = displayRecordName(item.name),
        onBack = onBack,
        modifier = modifier,
        actions = {
            FilledTonalIconButton(onClick = onEditItem) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit tracker",
                )
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(onClick = onAddEntry) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add measurement",
                )
            }
        },
    ) {
        val latest = state.entries.firstOrNull()
        TemplateSection(title = "Latest") {
            if (latest == null) {
                Text(
                    text = "No measurements in this range.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.measurementContent(),
                )
            } else {
                Text(
                    text = "${formatValue(latest.value)} ${item.unit}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.measurementContent(),
                )
                Text(
                    text = measurementDateTimeLabel(latest.occurredAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.measurementContent(top = 2.dp),
                )
            }
        }

        TemplateSection(title = "Range") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.measurementContent()) {
                MeasurementRange.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = state.range == range,
                        onClick = { onRangeChange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index, MeasurementRange.entries.size),
                        label = { Text(range.label) },
                    )
                }
            }
        }

        TemplateSection(title = "History") {
            when {
                state.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.error != null -> Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.measurementContent(),
                )
                state.entries.isEmpty() -> Text(
                    text = "No measurements in this range.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.measurementContent(),
                )
                else -> MeasurementChart(
                    entries = state.entries,
                    range = state.range,
                    unit = item.unit,
                )
            }
        }

        TemplateSection(title = "Entries") {
            if (state.entries.isEmpty() && !state.isLoading) {
                Text(
                    text = "No measurements in this range.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.measurementContent(),
                )
            }
            state.entries.forEachIndexed { index, entry ->
                MeasurementEntryRow(
                    entry = entry,
                    unit = item.unit,
                    onEdit = { onEditEntry(entry.id) },
                    onDelete = { onDeleteEntry(entry.id) },
                )
                if (index < state.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = TemplateStyle.spacing.pageHorizontal),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MeasurementChart(
    entries: List<RecordEntry>,
    range: MeasurementRange,
    unit: String,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val axisFormatter = remember(range) {
        CartesianValueFormatter { _, value, _ -> measurementAxisLabel(value.toLong(), range) }
    }
    val markerLabel = rememberTextComponent(
        style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        padding = Insets(horizontal = 8.dp, vertical = 4.dp),
        background = rememberShapeComponent(
            fill = Fill(MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(6.dp),
        ),
    )
    val markerValueFormatter = remember(entries, range, unit) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val timestamp = targets.firstOrNull()?.x?.toLong()
            val entry = timestamp?.let { selected ->
                entries.minByOrNull { kotlin.math.abs(it.occurredAt - selected) }
            }
            entry?.let {
                "${measurementDateTimeLabel(it.occurredAt)}\n${formatValue(it.value)} $unit"
            }.orEmpty()
        }
    }
    val marker = rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = markerValueFormatter,
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
    )

    LaunchedEffect(entries) {
        modelProducer.runTransaction {
            lineModel {
                series(
                    x = entries.map(RecordEntry::occurredAt),
                    y = entries.map(RecordEntry::value),
                )
            }
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = CartesianValueFormatter.decimal(suffix = " $unit"),
                ),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = axisFormatter),
                marker = marker,
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = TemplateStyle.spacing.pageHorizontal),
        )
    }
}

@Composable
private fun MeasurementEntryRow(
    entry: RecordEntry,
    unit: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = TemplateStyle.spacing.pageHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatValue(entry.value)} $unit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = measurementDateTimeLabel(entry.occurredAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit measurement")
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete measurement")
        }
    }
}

private fun Modifier.measurementContent(top: androidx.compose.ui.unit.Dp = 8.dp): Modifier =
    fillMaxWidth().padding(
        start = TemplateStyle.spacing.pageHorizontal,
        top = top,
        end = TemplateStyle.spacing.pageHorizontal,
    )

internal fun measurementDateTimeLabel(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault()))

private fun measurementAxisLabel(epochMillis: Long, range: MeasurementRange): String {
    val pattern = if (range == MeasurementRange.LAST_24_HOURS) "HH:mm" else "MMM d"
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
