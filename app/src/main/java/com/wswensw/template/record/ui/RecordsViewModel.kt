package com.wswensw.template.record.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wswensw.template.record.data.RecordDatabase
import com.wswensw.template.record.data.RecordRepository
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordItemDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class RecordsUiState(
    val isLoading: Boolean = true,
    val activeItems: List<RecordItem> = emptyList(),
    val archivedItems: List<RecordItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val entries: List<RecordEntry> = emptyList(),
) {
    fun findItem(id: Long): RecordItem? =
        activeItems.firstOrNull { it.id == id } ?: archivedItems.firstOrNull { it.id == id }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(
    private val repository: RecordRepository,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val selectedEntries = selectedDate.flatMapLatest { date ->
        repository.entriesOnDate(date.toString())
    }

    val uiState: StateFlow<RecordsUiState> = combine(
        repository.activeItems,
        repository.archivedItems,
        selectedDate,
        selectedEntries,
    ) { activeItems, archivedItems, date, entries ->
        RecordsUiState(
            isLoading = false,
            activeItems = activeItems,
            archivedItems = archivedItems,
            selectedDate = date,
            entries = entries,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsUiState(),
    )

    fun saveItem(
        itemId: Long?,
        draft: RecordItemDraft,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.saveItem(itemId, draft) }.fold(
                onSuccess = { onResult(null) },
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
                entryId
            }.fold(
                onSuccess = { entryId -> onResult(entryId, null) },
                onFailure = { error -> onResult(null, error.message ?: "Unable to save record.") },
            )
        }
    }

    fun deleteEntry(entryId: Long, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.deleteEntry(entryId) }
                .onFailure { error -> onError(error.message ?: "Unable to undo record.") }
        }
    }

    fun loadEntryCount(itemId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            onResult(repository.entryCount(itemId))
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(RecordsViewModel::class.java))
            val database = RecordDatabase.getInstance(applicationContext)
            return RecordsViewModel(RecordRepository(database)) as T
        }
    }
}
