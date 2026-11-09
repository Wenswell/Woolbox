package com.wswensw.woolbox.tool.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

data class OverlayDisplaySize(
    val width: Int,
    val height: Int,
)

class ComposeOverlayWindow(
    service: AccessibilityService,
) {
    private val windowContext: Context = service
    private val windowManager: WindowManager = requireNotNull(
        service.getSystemService(WindowManager::class.java),
    )
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isShowing: Boolean
        get() = composeView != null

    fun show(
        layoutParams: WindowManager.LayoutParams,
        content: @Composable () -> Unit,
    ) {
        check(composeView == null) { "Overlay is already visible." }
        layoutParams.flags = layoutParams.flags or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val owner = OverlayLifecycleOwner()
        val view = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent(content)
        }

        runCatching {
            windowManager.addView(view, layoutParams)
        }.onFailure {
            owner.destroy()
        }.getOrThrow()
        lifecycleOwner = owner
        composeView = view
        owner.resume()
    }

    fun updateLayout(layoutParams: WindowManager.LayoutParams) {
        composeView?.let { view -> windowManager.updateViewLayout(view, layoutParams) }
    }

    fun dismiss() {
        val view = composeView ?: return
        composeView = null
        windowManager.removeViewImmediate(view)
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    fun displaySize(): OverlayDisplaySize {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds: Rect = windowManager.currentWindowMetrics.bounds
            return OverlayDisplaySize(bounds.width(), bounds.height())
        }
        @Suppress("DEPRECATION")
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return OverlayDisplaySize(metrics.widthPixels, metrics.heightPixels)
    }

    fun density(): Float = windowContext.resources.displayMetrics.density

    private class OverlayLifecycleOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = registry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
