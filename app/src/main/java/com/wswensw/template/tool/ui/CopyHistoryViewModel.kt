package com.wswensw.template.tool.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wswensw.template.tool.model.CopyHistoryEntry
import com.wswensw.template.tool.model.CopyHistorySummary
import com.wswensw.template.tool.model.CopySessionScreen
import com.wswensw.template.tool.model.CopySessionUiState
import com.wswensw.template.tool.storage.CopyHistoryDatabase
import com.wswensw.template.tool.storage.CopyHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CopyHistorySummaryUi(
    val id: Long,
    val appName: String,
    val appIcon: Bitmap?,
    val capturedAt: Long,
    val textPreview: String,
    val blockCount: Int,
)

class CopyHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CopyHistoryRepository(CopyHistoryDatabase.getInstance(application))
    private val mutableSelectedHistory = MutableStateFlow<CopyHistoryEntry?>(null)

    val history: StateFlow<List<CopyHistorySummaryUi>> = repository.history
        .map { summaries -> summaries.map(application::resolveSummary) }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    val selectedHistory = mutableSelectedHistory.asStateFlow()

    fun open(historyId: Long, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.get(historyId) }
                .onSuccess { entry ->
                    if (entry == null) {
                        onResult("Copy history entry no longer exists.")
                    } else {
                        mutableSelectedHistory.value = entry
                        onResult(null)
                    }
                }
                .onFailure { error ->
                    onResult(error.message ?: "Unable to open copy history.")
                }
        }
    }

    fun closeEditor() {
        mutableSelectedHistory.value = null
    }

    fun save(
        historyId: Long,
        state: CopySessionUiState,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.updateSession(historyId, state) }
                .onSuccess {
                    mutableSelectedHistory.value = mutableSelectedHistory.value
                        ?.takeIf { it.id == historyId }
                        ?.copy(session = state.copy(screen = CopySessionScreen.EDITOR))
                    onResult(null)
                }
                .onFailure { error -> onResult(error.message ?: "Unable to save copy history.") }
        }
    }

    fun delete(historyId: Long, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.delete(historyId) }
                .onSuccess {
                    if (mutableSelectedHistory.value?.id == historyId) {
                        mutableSelectedHistory.value = null
                    }
                    onResult(null)
                }
                .onFailure { error -> onResult(error.message ?: "Unable to delete copy history.") }
        }
    }
}

private fun Application.resolveSummary(summary: CopyHistorySummary): CopyHistorySummaryUi {
    val appInfo = resolveAppDisplayInfo(summary.packageName)
    return CopyHistorySummaryUi(
        id = summary.id,
        appName = appInfo?.name?.takeIf(String::isNotBlank) ?: "Unavailable app",
        appIcon = appInfo?.icon,
        capturedAt = summary.capturedAt,
        textPreview = summary.textPreview,
        blockCount = summary.blockCount,
    )
}
