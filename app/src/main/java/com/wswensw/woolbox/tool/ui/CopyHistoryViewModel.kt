package com.wswensw.woolbox.tool.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wswensw.woolbox.tool.model.CopyHistoryEntry
import com.wswensw.woolbox.tool.model.CopyHistorySummary
import com.wswensw.woolbox.tool.model.CopySessionScreen
import com.wswensw.woolbox.tool.model.CopySessionUiState
import com.wswensw.woolbox.tool.storage.CopyHistoryDatabase
import com.wswensw.woolbox.tool.storage.CopyHistoryRepository
import com.wswensw.woolbox.R
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
                        onResult(getApplication<Application>().getString(R.string.copy_history_missing))
                    } else {
                        mutableSelectedHistory.value = entry
                        onResult(null)
                    }
                }
                .onFailure { error ->
                    onResult(error.message ?: getApplication<Application>().getString(R.string.copy_history_open_failed))
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
                .onFailure { error ->
                    onResult(error.message ?: getApplication<Application>().getString(R.string.copy_history_save_failed))
                }
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
                .onFailure { error ->
                    onResult(error.message ?: getApplication<Application>().getString(R.string.copy_history_delete_failed))
                }
        }
    }
}

private fun Application.resolveSummary(summary: CopyHistorySummary): CopyHistorySummaryUi {
    val appInfo = resolveAppDisplayInfo(summary.packageName)
    return CopyHistorySummaryUi(
        id = summary.id,
        appName = appInfo?.name?.takeIf(String::isNotBlank)
            ?: getString(R.string.unavailable_app),
        appIcon = appInfo?.icon,
        capturedAt = summary.capturedAt,
        textPreview = summary.textPreview,
        blockCount = summary.blockCount,
    )
}
