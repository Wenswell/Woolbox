package com.wswensw.woolbox.tool.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.tool.model.OledShieldConfig
import com.wswensw.woolbox.tool.storage.OledShieldConfigRepository
import com.wswensw.woolbox.ui.AppTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private data class OledOverlayUiState(
    val config: OledShieldConfig = OledShieldConfig.Default,
)

class OledShieldController(
    private val service: AccessibilityService,
    private val repository: OledShieldConfigRepository,
    private val onStateChanged: (
        active: Boolean,
        packageName: String?,
        config: OledShieldConfig?,
    ) -> Unit,
) {
    private val mutableUiState = MutableStateFlow(OledOverlayUiState())
    private val uiState = mutableUiState.asStateFlow()
    private var shieldWindow: ComposeOverlayWindow? = null
    private var panelWindow: ComposeOverlayWindow? = null
    private var shieldLayoutParams: WindowManager.LayoutParams? = null
    private var panelLayoutParams: WindowManager.LayoutParams? = null
    private var targetPackageName: String? = null

    fun isActive(): Boolean = shieldWindow?.isShowing == true

    fun onConfigurationChanged() {
        val config = mutableUiState.value.config
        shieldWindow?.let { host ->
            val params = shieldLayoutParams(host, config)
            shieldLayoutParams = params
            host.updateLayout(params)
        }
        panelWindow?.let { host ->
            val params = panelLayoutParams(host, config)
            panelLayoutParams = params
            host.updateLayout(params)
        }
    }

    fun show(packageName: String) {
        show(packageName, repository.loadForPackage(packageName))
    }

    fun show(
        packageName: String,
        config: OledShieldConfig,
    ) {
        dismissOverlays(notify = false)
        val normalizedConfig = config.normalized()
        targetPackageName = packageName
        mutableUiState.value = OledOverlayUiState(config = normalizedConfig)

        val host = ComposeOverlayWindow(service)
        val params = shieldLayoutParams(host, mutableUiState.value.config)
        shieldWindow = host
        shieldLayoutParams = params
        host.show(params) {
            AppTheme(useDynamicColor = false) {
                val state by uiState.collectAsState()
                OledShieldSurface(
                    config = state.config,
                    onLongPress = ::showPanel,
                )
            }
        }
        val profile = repository.recordUse(packageName, normalizedConfig)
        targetPackageName = profile.packageName
        onStateChanged(true, profile.packageName, profile.config)
    }

    fun hide(notify: Boolean = true) {
        dismissOverlays(notify = notify)
    }

    private fun dismissOverlays(notify: Boolean) {
        val packageName = targetPackageName
        panelWindow?.dismiss()
        panelWindow = null
        panelLayoutParams = null
        shieldWindow?.dismiss()
        shieldWindow = null
        shieldLayoutParams = null
        targetPackageName = null
        mutableUiState.value = OledOverlayUiState()
        if (notify && packageName != null) {
            onStateChanged(false, packageName, null)
        }
    }

    private fun showPanel() {
        if (!isActive() || panelWindow?.isShowing == true) return

        val host = ComposeOverlayWindow(service)
        val params = panelLayoutParams(host, mutableUiState.value.config)
        panelWindow = host
        panelLayoutParams = params
        host.show(params) {
            AppTheme(useDynamicColor = false) {
                val state by uiState.collectAsState()
                OledControlPanel(
                    config = state.config,
                    onOpacityChange = { updateConfig { config -> config.copy(opacityPercent = it) } },
                    onWidthChange = { updateConfig { config -> config.copy(widthPercent = it) } },
                    onHeightChange = { updateConfig { config -> config.copy(heightPercent = it) } },
                    onMove = ::movePanel,
                    onSave = ::save,
                    onClosePanel = ::hidePanel,
                    onHideShield = { hide() },
                )
            }
        }
    }

    private fun hidePanel() {
        panelWindow?.dismiss()
        panelWindow = null
        panelLayoutParams = null
    }

    private fun updateConfig(transform: (OledShieldConfig) -> OledShieldConfig) {
        val updated = transform(mutableUiState.value.config).normalized()
        mutableUiState.update { it.copy(config = updated) }
        shieldWindow?.let { host ->
            shieldLayoutParams = shieldLayoutParams(host, updated)
            host.updateLayout(requireNotNull(shieldLayoutParams))
        }
        onStateChanged(true, targetPackageName, updated)
    }

    private fun movePanel(
        deltaX: Float,
        deltaY: Float,
    ) {
        val host = panelWindow ?: return
        val current = panelLayoutParams ?: return
        val display = host.displaySize()
        val panelWidth = current.width.coerceAtLeast(1)
        val panelHeight = estimatedPanelHeight(host).coerceAtLeast(1)
        current.x = (current.x + deltaX.roundToInt())
            .coerceIn(0, (display.width - panelWidth).coerceAtLeast(0))
        current.y = (current.y + deltaY.roundToInt())
            .coerceIn(0, (display.height - panelHeight).coerceAtLeast(0))
        host.updateLayout(current)

        val maxX = (display.width - panelWidth).coerceAtLeast(1)
        val maxY = (display.height - panelHeight).coerceAtLeast(1)
        val updated = mutableUiState.value.config.copy(
            panelXPercent = current.x.toFloat() / maxX,
            panelYPercent = current.y.toFloat() / maxY,
        ).normalized()
        mutableUiState.update { it.copy(config = updated) }
        onStateChanged(true, targetPackageName, updated)
    }

    private fun save() {
        targetPackageName?.let { packageName ->
            repository.saveForPackage(packageName, mutableUiState.value.config)
        }
    }

    private fun shieldLayoutParams(
        host: ComposeOverlayWindow,
        config: OledShieldConfig,
    ): WindowManager.LayoutParams {
        val display = host.displaySize()
        return WindowManager.LayoutParams(
            (display.width * config.widthPercent / 100f).roundToInt().coerceAtLeast(1),
            (display.height * config.heightPercent / 100f).roundToInt().coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun panelLayoutParams(
        host: ComposeOverlayWindow,
        config: OledShieldConfig,
    ): WindowManager.LayoutParams {
        val display = host.displaySize()
        val width = minOf(panelWidth(host), display.width)
        val height = estimatedPanelHeight(host)
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (config.panelXPercent * (display.width - width).coerceAtLeast(0)).roundToInt()
            y = (config.panelYPercent * (display.height - height).coerceAtLeast(0)).roundToInt()
        }
    }

    private fun panelWidth(host: ComposeOverlayWindow): Int = (360 * host.density()).roundToInt()

    private fun estimatedPanelHeight(host: ComposeOverlayWindow): Int = (420 * host.density()).roundToInt()
}

@Composable
internal fun OledShieldSurface(
    config: OledShieldConfig,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = config.opacityPercent / 100f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {},
                    onLongPress = { onLongPress() },
                )
            },
    ) {}
}

@Composable
internal fun OledControlPanel(
    config: OledShieldConfig,
    onOpacityChange: (Int) -> Unit,
    onWidthChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onMove: (Float, Float) -> Unit,
    onSave: () -> Unit,
    onClosePanel: () -> Unit,
    onHideShield: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMove(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.oled_shield_tile_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onClosePanel) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_24),
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
            OledSliderRow(
                label = stringResource(R.string.oled_opacity),
                value = config.opacityPercent,
                onValueChange = onOpacityChange,
            )
            OledSliderRow(
                label = stringResource(R.string.oled_width),
                value = config.widthPercent,
                onValueChange = onWidthChange,
            )
            OledSliderRow(
                label = stringResource(R.string.oled_height),
                value = config.heightPercent,
                onValueChange = onHeightChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onHideShield) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = stringResource(R.string.oled_hide_shield),
                    )
                }
                FilledTonalIconButton(onClick = onSave) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.action_save),
                    )
                }
            }
        }
    }
}

@Composable
private fun OledSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value%", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = OledShieldConfig.MIN_PERCENT.toFloat()..OledShieldConfig.MAX_PERCENT.toFloat(),
        )
    }
}
