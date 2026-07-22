package com.wswensw.template.record.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.ui.TemplateStyle
import kotlin.math.roundToInt

@Composable
internal fun GoalCard(
    item: RecordItem,
    cumulative: Double,
    featured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    checkNotNull(item.goalValue)
    when (checkNotNull(item.goalPeriod)) {
        GoalPeriod.DAY -> DailyGoalCard(
            item = item,
            cumulative = cumulative,
            featured = featured,
            onClick = onClick,
            modifier = modifier,
        )
        GoalPeriod.WEEK -> WeeklyGoalCard(
            item = item,
            cumulative = cumulative,
            featured = featured,
            onClick = onClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun DailyGoalCard(
    item: RecordItem,
    cumulative: Double,
    featured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val target = checkNotNull(item.goalValue)
    val progress by animateFloatAsState(
        targetValue = goalProgressFraction(cumulative, target),
        animationSpec = spring(),
        label = "DailyGoalProgress",
    )
    val reached = cumulative >= target
    val containerColor by animateColorAsState(
        targetValue = if (reached) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        label = "DailyGoalContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (reached) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        label = "DailyGoalContent",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (featured) 132.dp else 96.dp),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (featured) 20.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DailyGoalMedallion(
                item = item,
                progress = progress,
                reached = reached,
                color = contentColor,
                size = if (featured) 84.dp else 68.dp,
            )
            Spacer(Modifier.width(if (featured) 18.dp else 14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily goal",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor.copy(alpha = 0.78f),
                )
                Text(
                    text = displayRecordName(item.name),
                    style = if (featured) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = goalValueLabel(cumulative, target, item.unit),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                GoalStatus(
                    progress = progress,
                    reached = reached,
                    periodLabel = "today",
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun WeeklyGoalCard(
    item: RecordItem,
    cumulative: Double,
    featured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val target = checkNotNull(item.goalValue)
    val progress by animateFloatAsState(
        targetValue = goalProgressFraction(cumulative, target),
        animationSpec = spring(),
        label = "WeeklyGoalProgress",
    )
    val reached = cumulative >= target
    val containerColor by animateColorAsState(
        targetValue = if (reached) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        label = "WeeklyGoalContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (reached) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        label = "WeeklyGoalContent",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (featured) 140.dp else 108.dp),
        shape = TemplateStyle.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (featured) 16.dp else 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordItemInitial(
                    name = item.name,
                    size = if (featured) 52.dp else 44.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weekly goal",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                    Text(
                        text = displayRecordName(item.name),
                        style = if (featured) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = goalValueLabel(cumulative, target, item.unit),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(if (featured) 10.dp else 12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (featured) 8.dp else 7.dp)
                    .clip(CircleShape),
                color = contentColor,
                trackColor = contentColor.copy(alpha = 0.16f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(if (featured) 5.dp else 7.dp))
            GoalStatus(
                progress = progress,
                reached = reached,
                periodLabel = "this week",
                color = contentColor,
            )
        }
    }
}

@Composable
private fun DailyGoalMedallion(
    item: RecordItem,
    progress: Float,
    reached: Boolean,
    color: Color,
    size: Dp,
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = if (size > 72.dp) 8.dp else 7.dp,
            trackColor = color.copy(alpha = 0.16f),
            gapSize = 0.dp,
        )
        RecordItemInitial(
            name = item.name,
            size = size - if (size > 72.dp) 24.dp else 20.dp,
        )
        if (reached) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(if (size > 72.dp) 26.dp else 22.dp),
            )
        }
    }
}

@Composable
private fun GoalStatus(
    progress: Float,
    reached: Boolean,
    periodLabel: String,
    color: Color,
) {
    Row(
        modifier = Modifier.padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (reached) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = if (reached) {
                "Goal reached"
            } else {
                "${(progress * 100).roundToInt()}% $periodLabel"
            },
            style = MaterialTheme.typography.labelMedium,
            color = color.copy(alpha = if (reached) 1f else 0.78f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun GoalProgressPanel(
    item: RecordItem,
    cumulative: Double,
    modifier: Modifier = Modifier,
) {
    val target = checkNotNull(item.goalValue)
    val progress by animateFloatAsState(
        targetValue = goalProgressFraction(cumulative, target),
        animationSpec = spring(),
        label = "SheetGoalProgress",
    )
    val reached = cumulative >= target
    val colors = recordItemColors(item.name)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = TemplateStyle.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        when (checkNotNull(item.goalPeriod)) {
            GoalPeriod.DAY -> Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = colors.accent,
                        strokeWidth = 7.dp,
                        trackColor = colors.container,
                        gapSize = 0.dp,
                    )
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily goal", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = goalValueLabel(cumulative, target, item.unit),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (reached) {
                        Text(
                            text = "Goal reached",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            GoalPeriod.WEEK -> Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Weekly goal",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = goalValueLabel(cumulative, target, item.unit),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = colors.accent,
                    trackColor = colors.container,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    text = if (reached) "Goal reached" else "${(progress * 100).roundToInt()}% this week",
                    color = if (reached) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

internal fun goalProgressFraction(cumulative: Double, target: Double): Float {
    require(target > 0.0)
    return (cumulative / target).coerceIn(0.0, 1.0).toFloat()
}

internal fun goalValueLabel(cumulative: Double, target: Double, unit: String): String =
    "${formatValue(cumulative)} / ${formatValue(target)} $unit"

internal fun displayRecordName(name: String): String =
    dropFirstRecordCharacter(name).trimStart().ifEmpty { firstRecordCharacter(name) }
