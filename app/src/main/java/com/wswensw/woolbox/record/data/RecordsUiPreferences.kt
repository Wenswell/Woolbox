package com.wswensw.woolbox.record.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Root open-task preview limit. null = show all. */
enum class RootTaskPreviewLimit(val count: Int?, val label: String) {
    THREE(3, "3"),
    FIVE(5, "5"),
    TEN(10, "10"),
    ALL(null, "All"),
    ;

    companion object {
        val DEFAULT = FIVE

        fun fromStored(value: String?): RootTaskPreviewLimit =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

class RecordsUiPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _rootTaskPreviewLimit = MutableStateFlow(readLimit())
    val rootTaskPreviewLimit: StateFlow<RootTaskPreviewLimit> = _rootTaskPreviewLimit.asStateFlow()

    fun setRootTaskPreviewLimit(limit: RootTaskPreviewLimit) {
        prefs.edit().putString(KEY_ROOT_TASK_PREVIEW, limit.name).apply()
        _rootTaskPreviewLimit.value = limit
    }

    private fun readLimit(): RootTaskPreviewLimit =
        RootTaskPreviewLimit.fromStored(prefs.getString(KEY_ROOT_TASK_PREVIEW, null))

    companion object {
        private const val PREFS = "records_ui"
        private const val KEY_ROOT_TASK_PREVIEW = "root_task_preview_limit"
    }
}
