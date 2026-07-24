package com.wswensw.woolbox.record.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wswensw.woolbox.record.data.RecordRepository
import com.wswensw.woolbox.record.data.RecordsUiPreferences
import com.wswensw.woolbox.record.data.RootTaskPreviewLimit
import com.wswensw.woolbox.record.data.RecordsSnapshot
import com.wswensw.woolbox.record.data.RecordsConnection
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordItemDraft
import com.wswensw.woolbox.record.model.TaskHorizon
import com.wswensw.woolbox.record.model.TodoTask
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasSnapshot: Boolean = false,
    val activeItems: List<RecordItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val entries: List<RecordEntry> = emptyList(),
    val latestEntries: Map<Long, RecordEntry> = emptyMap(),
    val tasks: List<TodoTask> = emptyList(),
    val goalValues: Map<Long, Double> = emptyMap(),
    val recentDayValues: Map<Long, List<Double>> = emptyMap(),
    val error: String? = null,
    val connection: RecordsConnection = RecordsConnection(isConfigured = false, username = ""),
) {
    fun findItem(id: Long): RecordItem? = activeItems.firstOrNull { it.id == id }

    fun findTask(id: Long): TodoTask? = tasks.firstOrNull { it.id == id }
}

data class MeasurementPreviewUiState(
    val itemId: Long? = null,
    val entries: List<RecordEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class RecordsViewModel(
    private val repository: RecordRepository,
    private val uiPreferences: RecordsUiPreferences,
) : ViewModel() {
    val rootTaskPreviewLimit = uiPreferences.rootTaskPreviewLimit

    fun setRootTaskPreviewLimit(limit: RootTaskPreviewLimit) {
        uiPreferences.setRootTaskPreviewLimit(limit)
    }

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val error = MutableStateFlow<String?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val _measurementPreview = MutableStateFlow(MeasurementPreviewUiState())
    private var refreshInFlight: Deferred<Result<Unit>>? = null

    val measurementPreview: StateFlow<MeasurementPreviewUiState> = _measurementPreview

    private val recordsContent = combine(repository.snapshot, selectedDate) { snapshot, date ->
        snapshot?.toUiState(date) ?: RecordsUiState(selectedDate = date)
    }

    val uiState: StateFlow<RecordsUiState> = combine(
        recordsContent,
        repository.connection,
        error,
        isRefreshing,
    ) { content, connection, message, refreshing ->
        content.copy(
            isLoading = !content.hasSnapshot && message == null,
            isRefreshing = refreshing,
            connection = connection,
            error = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsUiState(),
    )

    fun refresh() {
        startRefresh()
    }

    private fun startRefresh(): Deferred<Result<Unit>> = synchronized(this) {
        refreshInFlight?.let { return it }
        viewModelScope.async {
            isRefreshing.value = true
            selectedDate.value = LocalDate.now()
            runCatching { repository.refresh(selectedDate.value.toString()) }
                .onFailure { error.value = it.message ?: "Unable to load records." }
                .onSuccess { error.value = null }
                .also { isRefreshing.value = false }
        }.also { refresh ->
            refreshInFlight = refresh
            refresh.invokeOnCompletion {
                synchronized(this) {
                    if (refreshInFlight === refresh) refreshInFlight = null
                }
            }
        }
    }

    private suspend fun refreshOrThrow() {
        startRefresh().await().getOrThrow()
    }

    private fun RecordsSnapshot.toUiState(date: LocalDate) =
        RecordsUiState(
            hasSnapshot = true,
            activeItems = activeItems,
            selectedDate = date,
            entries = entries,
            latestEntries = latestEntries,
            tasks = tasks,
            goalValues = goalValues,
            recentDayValues = recentDayValues,
        )

    fun configureCredentials(username: String, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.configureCredentials(username, password)
                refreshOrThrow()
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
                onSuccess = { refreshOrThrow(); onResult(null) },
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
                refreshOrThrow()
                reloadMeasurementPreview(item.id)
                entryId
            }.fold(
                onSuccess = { entryId -> onResult(entryId, null) },
                onFailure = { error -> onResult(null, error.message ?: "Unable to save record.") },
            )
        }
    }

    fun deleteEntry(entryId: Long, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val itemId = _measurementPreview.value.entries.firstOrNull { it.id == entryId }?.itemId
            runCatching {
                repository.deleteEntry(entryId)
                refreshOrThrow()
                itemId?.let(::reloadMeasurementPreview)
            }
                .onFailure { error -> onError(error.message ?: "Unable to undo record.") }
        }
    }

    fun createTask(title: String, stepTitles: List<String>, horizon: TaskHorizon, onResult: (taskId: Long?, error: String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val taskId = repository.createTask(title, stepTitles, horizon)
                refreshOrThrow()
                taskId
            }.fold(
                onSuccess = { taskId -> onResult(taskId, null) },
                onFailure = { error -> onResult(null, error.message ?: "Unable to create task.") },
            )
        }
    }

    fun updateTask(taskId: Long, title: String, horizon: TaskHorizon, onResult: (String?) -> Unit) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to update task.",
    ) {
        repository.updateTask(taskId, title, horizon)
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

    fun reorderSteps(taskId: Long, stepIds: List<Long>, onResult: (String?) -> Unit) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to reorder subtasks.",
    ) {
        repository.reorderSteps(taskId, stepIds)
    }

    fun loadMeasurementPreview(itemId: Long) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            _measurementPreview.value = MeasurementPreviewUiState(
                itemId = itemId,
                isLoading = true,
            )
            runCatching {
                repository.entriesForItem(
                    itemId = itemId,
                    startDate = LocalDate.now().minusDays(1).toString(),
                    endDate = LocalDate.now().toString(),
                ).filter { entry -> entry.occurredAt in (now - 24 * 60 * 60 * 1_000)..now }
            }.fold(
                onSuccess = { entries ->
                    _measurementPreview.value = MeasurementPreviewUiState(
                        itemId = itemId,
                        entries = entries,
                    )
                },
                onFailure = { failure ->
                    _measurementPreview.value = MeasurementPreviewUiState(
                        itemId = itemId,
                        error = failure.message ?: "Unable to load measurements.",
                    )
                },
            )
        }
    }

    private fun reloadMeasurementPreview(itemId: Long) {
        if (_measurementPreview.value.itemId == itemId) {
            loadMeasurementPreview(itemId)
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
                refreshOrThrow()
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
            return RecordsViewModel(
                repository = RecordRepository(applicationContext),
                uiPreferences = RecordsUiPreferences(applicationContext),
            ) as T
        }
    }
}
