package com.wswensw.woolbox.tool.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.wswensw.woolbox.tool.model.OledShieldConfig
import kotlin.math.roundToInt

@Composable
fun InAppOledShield(
    config: OledShieldConfig,
    onConfigChange: (OledShieldConfig) -> Unit,
    onSave: () -> Unit,
    onHide: () -> Unit,
) {
    var isPanelVisible by rememberSaveable { mutableStateOf(false) }
    var displaySize by remember { mutableStateOf(IntSize.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var panelOffset by remember { mutableStateOf(IntOffset.Zero) }
    val latestConfig by rememberUpdatedState(config)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { displaySize = it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(config.widthPercent / 100f)
                .fillMaxHeight(config.heightPercent / 100f),
        ) {
            OledShieldSurface(
                config = config,
                onLongPress = { isPanelVisible = true },
            )
        }

        if (isPanelVisible) {
            val maxX = (displaySize.width - panelSize.width).coerceAtLeast(0)
            val maxY = (displaySize.height - panelSize.height).coerceAtLeast(0)
            LaunchedEffect(maxX, maxY, config.panelXPercent, config.panelYPercent) {
                panelOffset = IntOffset(
                    x = (config.panelXPercent * maxX).roundToInt(),
                    y = (config.panelYPercent * maxY).roundToInt(),
                )
            }
            OledControlPanel(
                config = config,
                onOpacityChange = { onConfigChange(config.copy(opacityPercent = it).normalized()) },
                onWidthChange = { onConfigChange(config.copy(widthPercent = it).normalized()) },
                onHeightChange = { onConfigChange(config.copy(heightPercent = it).normalized()) },
                onMove = { deltaX, deltaY ->
                    panelOffset = IntOffset(
                        x = (panelOffset.x + deltaX.roundToInt()).coerceIn(0, maxX),
                        y = (panelOffset.y + deltaY.roundToInt()).coerceIn(0, maxY),
                    )
                    onConfigChange(
                        latestConfig.copy(
                            panelXPercent = if (maxX == 0) 0f else panelOffset.x.toFloat() / maxX,
                            panelYPercent = if (maxY == 0) 0f else panelOffset.y.toFloat() / maxY,
                        ).normalized(),
                    )
                },
                onSave = onSave,
                onClosePanel = { isPanelVisible = false },
                onHideShield = onHide,
                modifier = Modifier
                    .offset { panelOffset }
                    .onSizeChanged { panelSize = it },
            )
        }
    }
}
