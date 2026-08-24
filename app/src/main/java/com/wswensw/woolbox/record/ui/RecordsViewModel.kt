package com.wswensw.woolbox.record.ui

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wswensw.woolbox.R
import com.wswensw.woolbox.record.data.RecordRepository
import com.wswensw.woolbox.record.data.RecordsSnapshot
import com.wswensw.woolbox.record.data.RecordsConnection
import com.wswensw.woolbox.record.data.TemplateStepDraft
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.GoalPool
import com.wswensw.woolbox.record.model.GoalPoolDraft
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordItemDraft
import com.wswensw.woolbox.record.model.DailyCheckin
import com.wswensw.woolbox.record.model.RecordsClock
import com.wswensw.woolbox.record.model.TaskTemplate
import com.wswensw.woolbox.record.model.TemplateCadence
import com.wswensw.woolbox.record.model.TodayTask
import com.wswensw.woolbox.record.reminder.HourlyCheckinReminderScheduler
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
    val goals: List<GoalPool> = emptyList(),
    val entries: List<RecordEntry> = emptyList(),
    val latestEntries: Map<Long, RecordEntry> = emptyMap(),
    val templates: List<TaskTemplate> = emptyList(),
    val dailySuggestions: List<TaskTemplate> = emptyList(),
    val unfinishedTasks: List<TodayTask> = emptyList(),
    val todayTasks: List<TodayTask> = emptyList(),
    val dailyCheckin: DailyCheckin? = null,
    val goalValues: Map<Long, Double> = emptyMap(),
    val recentDayValues: Map<Long, List<Double>> = emptyMap(),
    val error: String? = null,
    val connection: RecordsConnection = RecordsConnection(isConfigured = false, username = ""),
) {
    fun findItem(id: Long): RecordItem? = activeItems.firstOrNull { it.id == id }

    fun findGoal(id: Long): GoalPool? = goals.firstOrNull { it.id == id }

    fun findTemplate(id: Long): TaskTemplate? = templates.firstOrNull { it.id == id }
}

data class MeasurementPreviewUiState(
    val itemId: Long? = null,
    val entries: List<RecordEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class RecordsViewModel(
    private val repository: RecordRepository,
    private val hourlyCheckinReminderScheduler: HourlyCheckinReminderScheduler,
    private val resources: Resources,
) : ViewModel() {
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
            if (loaded != null && tick.date != loaded) {
                refresh()
            }
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
            .onFailure { error.value = it.localizedFailure(R.string.records_load_failed) }
            .onSuccess {
                error.value = null
                hourlyCheckinReminderScheduler.reconcile(repository.snapshot.value?.dailyCheckin)
            }
    }

    private fun RecordsSnapshot.toUiState() =
        RecordsUiState(
            hasSnapshot = true,
            activeItems = activeItems,
            goals = goals,
            entries = entries,
            latestEntries = latestEntries,
            templates = templates,
            dailySuggestions = dailySuggestions,
            unfinishedTasks = unfinishedTasks,
            todayTasks = todayTasks,
            dailyCheckin = dailyCheckin,
            goalValues = goalValues,
            recentDayValues = recentDayValues,
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
                    val message = failure.localizedFailure(R.string.records_connect_failed)
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
                onResult(failure.localizedFailure(R.string.record_item_save_failed))
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
                onResult(null, failure.localizedFailure(R.string.record_save_failed))
            }
        }
    }

    fun saveGoal(goalId: Long?, draft: GoalPoolDraft, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            writeAndRefresh(
                action = { repository.saveGoal(goalId, draft) },
                onWritten = { onResult(null) },
            ).onFailure { failure ->
                onResult(failure.localizedFailure(R.string.goal_save_failed))
            }
        }
    }

    fun createGoalEntry(goal: GoalPool, value: Double, onResult: (String?) -> Unit) =
        runRecordsAction(onResult, R.string.goal_progress_save_failed) {
            repository.createGoalEntry(goal, value, System.currentTimeMillis())
            Unit
        }

    fun setGoalSubgoalCompleted(
        subgoalId: Long,
        completed: Boolean,
        onResult: (String?) -> Unit,
    ) = runRecordsAction(onResult, R.string.goal_subgoal_update_failed) {
        repository.setGoalSubgoalCompleted(subgoalId, completed, System.currentTimeMillis())
    }

    fun deleteEntry(entryId: Long, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val itemId = _measurementPreview.value.entries.firstOrNull { it.id == entryId }?.itemId
            writeAndRefresh(
                action = { repository.deleteEntry(entryId) },
                onWritten = { itemId?.let(::reloadMeasurementPreview) },
            ).onFailure { failure ->
                onError(failure.localizedFailure(R.string.record_undo_failed))
            }
        }
    }

    fun saveTemplate(
        templateId: Long?,
        title: String,
        cadence: TemplateCadence,
        steps: List<TemplateStepDraft>,
        onResult: (templateId: Long?, error: String?) -> Unit,
    ) {
        viewModelScope.launch {
            writeAndRefresh(
                action = { repository.saveTemplate(templateId, title, cadence, steps) },
                onWritten = { savedId -> onResult(savedId, null) },
            ).onFailure { failure ->
                onResult(null, failure.localizedFailure(R.string.template_save_failed))
            }
        }
    }

    fun archiveTemplate(templateId: Long, onResult: (String?) -> Unit) = runRecordsAction(
        onResult = onResult,
        failureMessageRes = R.string.template_archive_failed,
    ) {
        repository.archiveTemplate(templateId)
    }

    fun createTodayTask(
        title: String,
        steps: List<TemplateStepDraft>,
        onResult: (String?) -> Unit,
    ) = runRecordsAction(onResult, R.string.today_task_add_failed) {
        repository.createTodayTask(LocalDate.now(), title, steps)
    }

    fun createTodayTaskFromTemplate(templateId: Long, onResult: (String?) -> Unit) =
        runRecordsAction(onResult, R.string.today_task_add_failed) {
            repository.createTodayTaskFromTemplate(LocalDate.now(), templateId)
        }

    fun createTodayTaskFromTask(sourceTaskId: Long, onResult: (String?) -> Unit) =
        runRecordsAction(onResult, R.string.today_task_add_failed) {
            repository.createTodayTaskFromTask(LocalDate.now(), sourceTaskId)
        }

    fun markAwake(onResult: (String?) -> Unit) = runRecordsAction(onResult, R.string.hourly_checkin_save_failed) {
        repository.markAwake(LocalDate.now(), System.currentTimeMillis())
    }

    fun markHourlyCheckin(hour: Int, onResult: (String?) -> Unit) =
        runRecordsAction(onResult, R.string.hourly_checkin_save_failed) {
            repository.markHourlyCheckin(LocalDate.now(), hour, System.currentTimeMillis())
        }

    fun setTodayTaskStepCompleted(
        task: TodayTask,
        stepId: Long,
        completed: Boolean,
        onResult: (String?) -> Unit,
    ) = runRecordsAction(onResult, R.string.today_task_update_failed) {
        repository.setTodayTaskStepCompleted(
            task = task,
            stepId = stepId,
            completed = completed,
            occurredAt = System.currentTimeMillis(),
        )
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
                            error = failure.localizedFailure(R.string.measurements_load_failed),
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

    private fun runRecordsAction(
        onResult: (String?) -> Unit,
        @StringRes failureMessageRes: Int,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            writeAndRefresh(
                action = action,
                onWritten = { onResult(null) },
            ).onFailure { failure ->
                val message = failure.localizedFailure(failureMessageRes)
                error.value = message
                onResult(message)
            }
        }
    }

    private fun Throwable.localizedFailure(@StringRes fallbackRes: Int): String =
        message ?: resources.getString(fallbackRes)

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
                hourlyCheckinReminderScheduler = HourlyCheckinReminderScheduler(applicationContext),
                resources = applicationContext.resources,
            ) as T
        }
    }
}
