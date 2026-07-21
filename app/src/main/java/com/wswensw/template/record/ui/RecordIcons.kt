package com.wswensw.template.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wswensw.template.record.model.RecordIcon

internal val recordIcons = RecordIcon.entries

@Composable
internal fun RecordItemIcon(icon: RecordIcon, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon.imageVector(),
            contentDescription = icon.label(),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
internal fun RecordIconButton(icon: RecordIcon, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon.imageVector(),
                contentDescription = icon.label(),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun RecordIcon.imageVector(): ImageVector = when (this) {
    RecordIcon.CHECK_CIRCLE -> Icons.Outlined.CheckCircle
    RecordIcon.WATER_DROP -> Icons.Outlined.WaterDrop
    RecordIcon.MENU_BOOK -> Icons.AutoMirrored.Outlined.MenuBook
    RecordIcon.DIRECTIONS_RUN -> Icons.AutoMirrored.Outlined.DirectionsRun
    RecordIcon.SELF_IMPROVEMENT -> Icons.Outlined.SelfImprovement
    RecordIcon.MEDICATION -> Icons.Outlined.Medication
    RecordIcon.RESTAURANT -> Icons.Outlined.Restaurant
    RecordIcon.BEDTIME -> Icons.Outlined.Bedtime
}

internal fun RecordIcon.label(): String = when (this) {
    RecordIcon.CHECK_CIRCLE -> "Check circle"
    RecordIcon.WATER_DROP -> "Water drop"
    RecordIcon.MENU_BOOK -> "Book"
    RecordIcon.DIRECTIONS_RUN -> "Running"
    RecordIcon.SELF_IMPROVEMENT -> "Meditation"
    RecordIcon.MEDICATION -> "Medication"
    RecordIcon.RESTAURANT -> "Meal"
    RecordIcon.BEDTIME -> "Sleep"
}
