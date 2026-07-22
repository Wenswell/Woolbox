package com.wswensw.template.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.BreakIterator
import java.util.Locale

@Immutable
internal data class RecordItemColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

@Composable
internal fun recordItemColors(name: String): RecordItemColors {
    val scheme = MaterialTheme.colorScheme
    val colorIndex = Math.floorMod(firstRecordCharacter(name).hashCode(), 6)
    return when (colorIndex) {
        0 -> RecordItemColors(
            accent = scheme.primary,
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer,
        )
        1 -> RecordItemColors(
            accent = scheme.secondary,
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer,
        )
        2 -> RecordItemColors(
            accent = scheme.tertiary,
            container = scheme.tertiaryContainer,
            onContainer = scheme.onTertiaryContainer,
        )
        3 -> RecordItemColors(
            accent = scheme.primary,
            container = scheme.surfaceContainerHighest,
            onContainer = scheme.primary,
        )
        4 -> RecordItemColors(
            accent = scheme.secondary,
            container = scheme.surfaceContainerHighest,
            onContainer = scheme.secondary,
        )
        else -> RecordItemColors(
            accent = scheme.tertiary,
            container = scheme.surfaceContainerHighest,
            onContainer = scheme.tertiary,
        )
    }
}

@Composable
internal fun RecordItemInitial(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = recordItemColors(name)
    Box(
        modifier = modifier
            .size(size)
            .background(colors.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = firstRecordCharacter(name),
            color = colors.onContainer,
            style = if (size > 40.dp) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

internal fun firstRecordCharacter(value: String): String {
    if (value.isEmpty()) return ""
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(value)
    val end = iterator.next()
    return if (end == BreakIterator.DONE) "" else value.substring(0, end)
}

internal fun dropFirstRecordCharacter(value: String): String {
    if (value.isEmpty()) return value
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(value)
    val end = iterator.next()
    return if (end == BreakIterator.DONE) value else value.substring(end)
}
