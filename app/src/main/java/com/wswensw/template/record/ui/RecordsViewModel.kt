package com.wswensw.template.record.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wswensw.template.record.data.RecordRepository
import com.wswensw.template.record.data.RecordsConnection
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordItemDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordsUiState(
    val isLoading: Boolean = true,
    val activeItems: List<RecordItem> = emptyList(),
    val archivedItems: List<RecordItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val entries: List<RecordEntry> = emptyList(),
    val error: String? = null,
    val connection: RecordsConnection = RecordsConnection(isConfigured = false, username = ""),
) {
    fun findItem(id: Long): RecordItem? =
        activeItems.firstOrNull { it.id == id } ?: archivedItems.firstOrNull { it.id == id }
}

class RecordsViewModel(
    private val repository: RecordRepository,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val selectedEntries = repository.entries
    private val error = MutableStateFlow<String?>(null)
    private val isLoading = MutableStateFlow(true)

    private val contentState = combine(
        repository.activeItems,
        repository.archivedItems,
        selectedDate,
        selectedEntries,
        error,
    ) { activeItems, archivedItems, date, entries, message ->
        RecordsUiState(
            activeItems = activeItems,
            archivedItems = archivedItems,
            selectedDate = date,
            entries = entries,
            error = message,
        )
    }

    val uiState: StateFlow<RecordsUiState> = combine(
        contentState,
        repository.connection,
        isLoading,
    ) { content, connection, loading ->
        content.copy(isLoading = loading, connection = connection)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsUiState(),
    )

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            runCatching { repository.refresh(selectedDate.value.toString()) }
                .onFailure { error.value = it.message ?: "Unable to load records." }
                .onSuccess { error.value = null }
            isLoading.value = false
        }
    }

    fun configureCredentials(username: String, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.configureCredentials(username, password)
                repository.refresh(selectedDate.value.toString())
            }.fold(
                onSuccess = {
                    error.value = null
                    onResult(null)
                },
                onFailure = { failure ->
                    val message = failure.message ?: "Unable to connect to Records."
                    error.value = message
                    onResult(message)
                },
            )
        }
    }

    fun saveItem(
        itemId: Long?,
        draft: RecordItemDraft,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.saveItem(itemId, draft) }.fold(
                onSuccess = { refresh(); onResult(null) },
                onFailure = { error -> onResult(error.message ?: "Unable to save record item.") },
            )
        }
    }

    fun createEntry(
        itemId: Long,
        value: Double,
        onResult: (entryId: Long?, error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val occurredAt = System.currentTimeMillis()
                val entryId = repository.createEntry(itemId, value, occurredAt)
                selectedDate.value = LocalDate.now()
                repository.refresh(selectedDate.value.toString())
                entryId
            }.fold(
                onSuccess = { entryId -> onResult(entryId, null) },
                onFailure = { error -> onResult(null, error.message ?: "Unable to save record.") },
            )
        }
    }

    fun deleteEntry(entryId: Long, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.deleteEntry(entryId); repository.refresh(selectedDate.value.toString()) }
                .onFailure { error -> onError(error.message ?: "Unable to undo record.") }
        }
    }

    fun loadEntryCount(itemId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.entryCount(itemId) }.onSuccess(onResult)
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(RecordsViewModel::class.java))
            return RecordsViewModel(RecordRepository(applicationContext)) as T
        }
    }
}
