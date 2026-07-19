package com.wswensw.template.record.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wswensw.template.record.data.RecordDatabase
import com.wswensw.template.record.data.RecordRepository
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordItemDraft
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordsUiState(
    val isLoading: Boolean = true,
    val activeItems: List<RecordItem> = emptyList(),
    val archivedItems: List<RecordItem> = emptyList(),
) {
    fun findItem(id: Long): RecordItem? =
        activeItems.firstOrNull { it.id == id } ?: archivedItems.firstOrNull { it.id == id }
}

class RecordsViewModel(
    private val repository: RecordRepository,
) : ViewModel() {
    val uiState: StateFlow<RecordsUiState> = combine(
        repository.activeItems,
        repository.archivedItems,
    ) { activeItems, archivedItems ->
        RecordsUiState(
            isLoading = false,
            activeItems = activeItems,
            archivedItems = archivedItems,
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
