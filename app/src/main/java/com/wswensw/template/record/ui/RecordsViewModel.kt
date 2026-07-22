package com.wswensw.template.record.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wswensw.template.record.data.RecordRepository
import com.wswensw.template.record.model.ItemStreak
import com.wswensw.template.record.data.RecordsConnection
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordItemDraft
import com.wswensw.template.record.model.MeasurementRange
import com.wswensw.template.record.model.TodoTask
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class RecordsContent(
    val activeItems: List<RecordItem>,
    val archivedItems: List<RecordItem>,
    val selectedDate: LocalDate,
    val entries: List<RecordEntry>,
    val goalValues: Map<Long, Double>,
)

data class RecordsUiState(
    val isLoading: Boolean = true,
    val activeItems: List<RecordItem> = emptyList(),
    val archivedItems: List<RecordItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val entries: List<RecordEntry> = emptyList(),
    val latestEntries: Map<Long, RecordEntry> = emptyMap(),
    val tasks: List<TodoTask> = emptyList(),
    val goalValues: Map<Long, Double> = emptyMap(),
    val streaks: Map<Long, ItemStreak> = emptyMap(),
    val error: String? = null,
    val connection: RecordsConnection = RecordsConnection(isConfigured = false, username = ""),
) {
    fun findItem(id: Long): RecordItem? =
        activeItems.firstOrNull { it.id == id } ?: archivedItems.firstOrNull { it.id == id }

    fun findTask(id: Long): TodoTask? = tasks.firstOrNull { it.id == id }
}

data class MeasurementDetailUiState(
    val itemId: Long? = null,
    val range: MeasurementRange = MeasurementRange.LAST_24_HOURS,
    val entries: List<RecordEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class RecordsViewModel(
    private val repository: RecordRepository,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val selectedEntries = repository.entries
    private val error = MutableStateFlow<String?>(null)
    private val isLoading = MutableStateFlow(true)
    private val _measurementDetail = MutableStateFlow(MeasurementDetailUiState())

    val measurementDetail: StateFlow<MeasurementDetailUiState> = _measurementDetail

    private val recordsContent = combine(
        combine(
            repository.activeItems,
            repository.archivedItems,
            selectedDate,
            selectedEntries,
            repository.goalValues,
        ) { activeItems, archivedItems, date, entries, goalValues ->
            RecordsContent(
                activeItems = activeItems,
                archivedItems = archivedItems,
                selectedDate = date,
                entries = entries,
                goalValues = goalValues,
            )
        },
        repository.latestEntries,
        repository.streaks,
        repository.tasks,
    ) { content, latestEntries, streaks, tasks ->
        RecordsUiState(
            activeItems = content.activeItems,
            archivedItems = content.archivedItems,
            selectedDate = content.selectedDate,
            entries = content.entries,
            latestEntries = latestEntries,
            tasks = tasks,
            goalValues = content.goalValues,
            streaks = streaks,
        )
    }

    val uiState: StateFlow<RecordsUiState> = combine(
        recordsContent,
        repository.connection,
        error,
        isLoading,
    ) { content, connection, message, loading ->
        content.copy(isLoading = loading, connection = connection, error = message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsUiState(),
    )

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            selectedDate.value = LocalDate.now()
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
        item: RecordItem,
        value: Double,
        occurredAt: Long = System.currentTimeMillis(),
        onResult: (entryId: Long?, error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val entryId = repository.createEntry(item, value, occurredAt)
                selectedDate.value = LocalDate.now()
                repository.refresh(selectedDate.value.toString())
                reloadMeasurementDetail(item.id)
                entryId
            }.fold(
                onSuccess = { entryId -> onResult(entryId, null) },
                onFailure = { error -> onResult(null, error.message ?: "Unable to save record.") },
            )
        }
    }

    fun updateEntry(
        entry: RecordEntry,
        value: Double,
        occurredAt: Long,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.updateEntry(entry, value, occurredAt)
                repository.refresh(selectedDate.value.toString())
                reloadMeasurementDetail(entry.itemId)
            }.fold(
                onSuccess = { onResult(null) },
                onFailure = { error -> onResult(error.message ?: "Unable to update record.") },
            )
        }
    }

    fun deleteEntry(entryId: Long, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val itemId = _measurementDetail.value.entries.firstOrNull { it.id == entryId }?.itemId
            runCatching {
                repository.deleteEntry(entryId)
                repository.refresh(selectedDate.value.toString())
                itemId?.let(::reloadMeasurementDetail)
            }
                .onFailure { error -> onError(error.message ?: "Unable to undo record.") }
        }
    }

    fun createTask(title: String, onResult: (taskId: Long?, error: String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val taskId = repository.createTask(title)
                repository.refresh(selectedDate.value.toString())
                taskId
            }.fold(
                onSuccess = { taskId -> onResult(taskId, null) },
                onFailure = { error -> onResult(null, error.message ?: "Unable to create task.") },
            )
        }
    }

    fun updateTask(taskId: Long, title: String, onResult: (String?) -> Unit) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to update task.",
    ) {
        repository.updateTask(taskId, title)
    }

    fun setTaskCompleted(taskId: Long, completed: Boolean, onResult: (String?) -> Unit = {}) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to update task.",
    ) {
        repository.setTaskCompleted(taskId, completed)
    }

    fun deleteTask(taskId: Long, onResult: (String?) -> Unit) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to delete task.",
    ) {
        repository.deleteTask(taskId)
    }

    fun createStep(taskId: Long, title: String, onResult: (String?) -> Unit) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to add subtask.",
    ) {
        repository.createStep(taskId, title)
    }

    fun updateStep(stepId: Long, title: String, onResult: (String?) -> Unit) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to update subtask.",
    ) {
        repository.updateStep(stepId, title)
    }

    fun setStepCompleted(stepId: Long, completed: Boolean, onResult: (String?) -> Unit = {}) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to update subtask.",
    ) {
        repository.setStepCompleted(stepId, completed)
    }

    fun deleteStep(stepId: Long, onResult: (String?) -> Unit) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to delete subtask.",
    ) {
        repository.deleteStep(stepId)
    }

    fun loadMeasurementEntries(itemId: Long, range: MeasurementRange) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            _measurementDetail.value = MeasurementDetailUiState(
                itemId = itemId,
                range = range,
                isLoading = true,
            )
            runCatching {
                repository.entriesForItem(
                    itemId = itemId,
                    startDate = range.queryStartDate(now),
                    endDate = LocalDate.now().toString(),
                ).filter { entry -> range.contains(entry.occurredAt, now) }
            }.fold(
                onSuccess = { entries ->
                    _measurementDetail.value = MeasurementDetailUiState(
                        itemId = itemId,
                        range = range,
                        entries = entries,
                    )
                },
                onFailure = { failure ->
                    _measurementDetail.value = MeasurementDetailUiState(
                        itemId = itemId,
                        range = range,
                        error = failure.message ?: "Unable to load measurements.",
                    )
                },
            )
        }
    }

    private fun reloadMeasurementDetail(itemId: Long) {
        val detail = _measurementDetail.value
        if (detail.itemId == itemId) {
            loadMeasurementEntries(itemId, detail.range)
        }
    }

    private fun runTaskAction(
        onResult: (String?) -> Unit,
        failureMessage: String,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                action()
                repository.refresh(selectedDate.value.toString())
            }.fold(
                onSuccess = {
                    error.value = null
                    onResult(null)
                },
                onFailure = { failure ->
                    val message = failure.message ?: failureMessage
                    error.value = message
                    onResult(message)
                },
            )
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(RecordsViewModel::class.java))
            applicationContext.deleteDatabase("records.db")
            return RecordsViewModel(RecordRepository(applicationContext)) as T
        }
    }
}
