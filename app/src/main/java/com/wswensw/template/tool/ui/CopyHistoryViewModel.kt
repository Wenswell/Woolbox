package com.wswensw.template.tool.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wswensw.template.tool.model.CopyHistoryEntry
import com.wswensw.template.tool.model.CopyHistorySummary
import com.wswensw.template.tool.model.CopySessionScreen
import com.wswensw.template.tool.model.CopySessionUiState
import com.wswensw.template.tool.storage.CopyHistoryDatabase
import com.wswensw.template.tool.storage.CopyHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CopyHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CopyHistoryRepository(CopyHistoryDatabase.getInstance(application))
    private val mutableSelectedHistory = MutableStateFlow<CopyHistoryEntry?>(null)

    val history: StateFlow<List<CopyHistorySummary>> = repository.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val selectedHistory = mutableSelectedHistory.asStateFlow()

    fun open(historyId: Long, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.get(historyId) }
                .onSuccess { entry ->
                    if (entry == null) {
                        onError("Copy history entry no longer exists.")
                    } else {
                        mutableSelectedHistory.value = entry
                    }
                }
                .onFailure { error -> onError(error.message ?: "Unable to open copy history.") }
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
