package com.wswensw.template.tool.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo

data class ForegroundTarget(
    val packageName: String,
)

data class ForegroundTargetCandidate(
    val packageName: String,
    val isActive: Boolean,
    val isFocused: Boolean,
    val layer: Int,
)

object ForegroundTargetSelector {
    fun select(candidates: List<ForegroundTargetCandidate>): String? = candidates
        .let { allCandidates ->
            if (allCandidates.any(ForegroundTargetCandidate::isActive)) {
                allCandidates.filter(ForegroundTargetCandidate::isActive)
            } else if (allCandidates.any(ForegroundTargetCandidate::isFocused)) {
                allCandidates.filter(ForegroundTargetCandidate::isFocused)
            } else {
                allCandidates
            }
        }
        .sortedWith(
            compareByDescending<ForegroundTargetCandidate> { it.isActive }
                .thenByDescending { it.isFocused }
                .thenByDescending { it.layer },
        )
        .firstOrNull()
        ?.packageName
}

class ForegroundWindowResolver(
    private val service: AccessibilityService,
) {
    fun resolve(): ForegroundTarget? = service.windows
        .asSequence()
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        .mapNotNull { window ->
            window.root?.let { root ->
                root.packageName?.toString()?.let { name -> WindowCandidate(name, window) }
            }
        }
        .filter { candidate -> candidate.packageName.isExternalTo(service.packageName) }
        .map { candidate ->
            ForegroundTargetCandidate(
                packageName = candidate.packageName,
                isActive = candidate.window.isActive,
                isFocused = candidate.window.isFocused,
                layer = candidate.window.layer,
            )
        }
        .toList()
        .let(ForegroundTargetSelector::select)
        ?.let(::ForegroundTarget)

    fun isSystemUiForeground(): Boolean =
        service.rootInActiveWindow?.packageName?.toString() == SYSTEM_UI_PACKAGE ||
            service.windows.any { window ->
                val packageName = window.root?.packageName?.toString()
                packageName == SYSTEM_UI_PACKAGE && (window.isActive || window.isFocused)
            }

    fun isOwnApplicationForeground(): Boolean = service.windows.any { window ->
        window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
            (window.isActive || window.isFocused) &&
            window.root?.packageName?.toString() == service.packageName
    }

    private data class WindowCandidate(
        val packageName: String,
        val window: AccessibilityWindowInfo,
    )

    private fun String.isExternalTo(ownPackageName: String): Boolean =
        this != ownPackageName && this != SYSTEM_UI_PACKAGE

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
