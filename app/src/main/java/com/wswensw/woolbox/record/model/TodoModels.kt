package com.wswensw.woolbox.record.model

import java.time.LocalDate

/** A task's derived position relative to the local date being viewed. */
enum class TaskDateBucket {
    OVERDUE,
    TODAY,
    LATER,
}

data class TodoTask(
    val id: Long,
    val title: String,
    /** The local date this task belongs to, or null when it is unscheduled. */
    val plannedDate: LocalDate?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val steps: List<TodoStep>,
) {
    val isCompleted: Boolean
        get() = completedAt != null

    val completedStepCount: Int
        get() = steps.count(TodoStep::isCompleted)

    /** First unfinished subtask in fixed position order. */
    val nextStep: TodoStep?
        get() = steps.firstOrNull { !it.isCompleted }
}

fun TodoTask.dateBucket(today: LocalDate): TaskDateBucket = when {
    plannedDate == null || plannedDate.isAfter(today) -> TaskDateBucket.LATER
    plannedDate.isBefore(today) -> TaskDateBucket.OVERDUE
    else -> TaskDateBucket.TODAY
}

data class TodoStep(
    val id: Long,
    val taskId: Long,
    val title: String,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val windows: List<RecordWindow> = emptyList(),
) {
    val isCompleted: Boolean
        get() = completedAt != null
}

fun validatedTaskTitle(value: String): String {
    val title = value.trim()
    if (title.isEmpty()) throw RecordValidationException("Title is required.")
    return title
}
