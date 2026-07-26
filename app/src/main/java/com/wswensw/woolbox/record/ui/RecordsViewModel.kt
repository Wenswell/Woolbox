package com.wswensw.woolbox.record.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wswensw.woolbox.record.data.RecordRepository
import com.wswensw.woolbox.record.data.RecordsUiPreferences
import com.wswensw.woolbox.record.data.RootTaskPreviewLimit
import com.wswensw.woolbox.record.data.RecordsSnapshot
import com.wswensw.woolbox.record.data.TodoStepDraft
import com.wswensw.woolbox.record.data.RecordsConnection
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordItemDraft
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.model.StreakDays
import com.wswensw.woolbox.record.model.RecordWindow
import com.wswensw.woolbox.record.model.TaskHorizon
import com.wswensw.woolbox.record.model.TodoTask
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordsUiState(
    val isLoading: Boolean = true,
    val hasSnapshot: Boolean = false,
    val activeItems: List<RecordItem> = emptyList(),
    val entries: List<RecordEntry> = emptyList(),
    val latestEntries: Map<Long, RecordEntry> = emptyMap(),
    val tasks: List<TodoTask> = emptyList(),
    val goalValues: Map<Long, Double> = emptyMap(),
    val recentDayValues: Map<Long, List<Double>> = emptyMap(),
    val habitMinutes: Map<Long, Int> = emptyMap(),
    val itemStreaks: Map<Long, Int> = emptyMap(),
    val goalStreak: StreakDays = StreakDays(days = 0, atHistoryLimit = false),
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

    private val error = MutableStateFlow<String?>(null)
    private val _measurementPreview = MutableStateFlow(MeasurementPreviewUiState())
    private val recordsOperations = RecordsOperationCoordinator()
    private var measurementPreviewRequestId = 0L
    private var loadedDate: LocalDate? = null

    val measurementPreview: StateFlow<MeasurementPreviewUiState> = _measurementPreview

    /**
     * Minute-aligned local clock. Only runs while a screen collects it, and
     * reloads the snapshot when the local date moves past the loaded day.
     */
    val clock: StateFlow<RecordsClock> = flow {
        while (true) {
            emit(RecordsClock.now())
            delay(MILLIS_PER_MINUTE - System.currentTimeMillis() % MILLIS_PER_MINUTE)
        }
    }
        .onEach { tick ->
            val loaded = loadedDate
            if (loaded != null && tick.date != loaded) refresh()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordsClock.now(),
        )

    val uiState: StateFlow<RecordsUiState> = combine(
        repository.snapshot,
        repository.connection,
        error,
    ) { snapshot, connection, message ->
        (snapshot?.toUiState() ?: RecordsUiState()).copy(
            isLoading = snapshot == null && message == null,
            connection = connection,
            error = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsUiState(),
    )

    fun refresh() {
        recordsOperations.refresh(viewModelScope) { refreshSnapshot() }
    }

    private suspend fun refreshSnapshot(): Result<Unit> {
        // Claim the requested day before the request so a failed midnight
        // rollover does not retry on every clock tick.
        val date = LocalDate.now()
        loadedDate = date
        return runCatching { repository.refresh(date.toString()) }
            .onFailure { error.value = it.message ?: "Unable to load records." }
            .onSuccess { error.value = null }
    }

    private fun RecordsSnapshot.toUiState() =
        RecordsUiState(
            hasSnapshot = true,
            activeItems = activeItems,
            entries = entries,
            latestEntries = latestEntries,
            tasks = tasks,
            goalValues = goalValues,
            recentDayValues = recentDayValues,
            habitMinutes = habitMinutes,
            itemStreaks = itemStreaks,
            goalStreak = goalStreak,
        )

    fun configureCredentials(username: String, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                recordsOperations.writeAndRefresh(
                    write = { repository.configureCredentials(username, password) },
                    refresh = { refreshSnapshot().getOrThrow() },
                )
            }.fold(
                onSuccess = { onResult(null) },
                onFailure = { failure ->
                    val message = failure.message ?: "Unable to connect to Records."
                    error.value = message
                    onResult(message)
                },
            )
        }
    }

    private suspend fun <T> writeAndRefresh(
        action: suspend () -> T,
        onWritten: (T) -> Unit,
    ): Result<T> =
        runCatching {
            recordsOperations.writeAndRefresh(
                write = action,
                onWritten = onWritten,
                refresh = { refreshSnapshot() },
            )
        }

    fun saveItem(
        itemId: Long?,
        draft: RecordItemDraft,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            writeAndRefresh(
                action = { repository.saveItem(itemId, draft) },
                onWritten = { onResult(null) },
            ).onFailure { failure ->
                onResult(failure.message ?: "Unable to save record item.")
            }
        }
    }

    fun createEntry(
        item: RecordItem,
        value: Double,
        occurredAt: Long = System.currentTimeMillis(),
        onResult: (entryId: Long?, error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            writeAndRefresh(
                action = { repository.createEntry(item, value, occurredAt) },
                onWritten = { entryId ->
                    reloadMeasurementPreview(item.id)
                    onResult(entryId, null)
                },
            ).onFailure { failure ->
                onResult(null, failure.message ?: "Unable to save record.")
            }
        }
    }

    fun deleteEntry(entryId: Long, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val itemId = _measurementPreview.value.entries.firstOrNull { it.id == entryId }?.itemId
            writeAndRefresh(
                action = { repository.deleteEntry(entryId) },
                onWritten = { itemId?.let(::reloadMeasurementPreview) },
            ).onFailure { failure ->
                onError(failure.message ?: "Unable to undo record.")
            }
        }
    }

    fun createTask(
        title: String,
        steps: List<TodoStepDraft>,
        horizon: TaskHorizon,
        onResult: (taskId: Long?, error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            writeAndRefresh(
                action = { repository.createTask(title, steps, horizon) },
                onWritten = { taskId -> onResult(taskId, null) },
            ).onFailure { failure ->
                onResult(null, failure.message ?: "Unable to create task.")
            }
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

    fun createStep(
        taskId: Long,
        title: String,
        windows: List<RecordWindow>,
        onResult: (String?) -> Unit,
    ) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to add subtask.",
    ) {
        repository.createStep(taskId, title, windows)
    }

    fun updateStep(
        stepId: Long,
        title: String,
        windows: List<RecordWindow>,
        onResult: (String?) -> Unit,
    ) = runTaskAction(
        onResult = onResult,
        failureMessage = "Unable to update subtask.",
    ) {
        repository.updateStep(stepId, title, windows)
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
        val requestId = ++measurementPreviewRequestId
        _measurementPreview.value = MeasurementPreviewUiState(
            itemId = itemId,
            isLoading = true,
        )
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                repository.entriesForItem(
                    itemId = itemId,
                    startDate = LocalDate.now().minusDays(1).toString(),
                    endDate = LocalDate.now().toString(),
                ).filter { entry -> entry.occurredAt in (now - 24 * 60 * 60 * 1_000)..now }
            }.fold(
                onSuccess = { entries ->
                    if (requestId == measurementPreviewRequestId) {
                        _measurementPreview.value = MeasurementPreviewUiState(
                            itemId = itemId,
                            entries = entries,
                        )
                    }
                },
                onFailure = { failure ->
                    if (requestId == measurementPreviewRequestId) {
                        _measurementPreview.value = MeasurementPreviewUiState(
                            itemId = itemId,
                            error = failure.message ?: "Unable to load measurements.",
                        )
                    }
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
            writeAndRefresh(
                action = action,
                onWritten = { onResult(null) },
            ).onFailure { failure ->
                val message = failure.message ?: failureMessage
                error.value = message
                onResult(message)
            }
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
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
