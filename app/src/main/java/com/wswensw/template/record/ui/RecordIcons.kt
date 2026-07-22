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
import androidx.compose.ui.graphics.lerp
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

/**
 * Stable chromatic colors from the item name. Never uses surfaceContainer* grays
 * (e.g. #E1E0E5); blends the role accent onto surface so cards stay tinted.
 */
@Composable
internal fun recordItemColors(name: String): RecordItemColors {
    val scheme = MaterialTheme.colorScheme
    val colorIndex = Math.floorMod(firstRecordCharacter(name).hashCode(), 3)
    val (accent, onContainer) = when (colorIndex) {
        0 -> scheme.primary to scheme.onPrimaryContainer
        1 -> scheme.secondary to scheme.onSecondaryContainer
        else -> scheme.tertiary to scheme.onTertiaryContainer
    }
    val materialContainer = when (colorIndex) {
        0 -> scheme.primaryContainer
        1 -> scheme.secondaryContainer
        else -> scheme.tertiaryContainer
    }
    // Prefer Material container; if the theme flattens it toward surface gray, force a visible tint.
    val container = if (isNearNeutralSurface(materialContainer, scheme.surface)) {
        lerp(scheme.surface, accent, 0.18f)
    } else {
        materialContainer
    }
    return RecordItemColors(
        accent = accent,
        container = container,
        onContainer = onContainer,
    )
}

private fun isNearNeutralSurface(color: Color, surface: Color): Boolean {
    fun channelDelta(a: Float, b: Float) = kotlin.math.abs(a - b)
    val chroma =
        maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
    val nearSurface =
        channelDelta(color.red, surface.red) < 0.04f &&
            channelDelta(color.green, surface.green) < 0.04f &&
            channelDelta(color.blue, surface.blue) < 0.04f
    return chroma < 0.04f || nearSurface
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
