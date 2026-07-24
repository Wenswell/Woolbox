package com.wswensw.woolbox.record.model

enum class TaskHorizon {
    TODAY,
    LATER,
    ;

    val label: String
        get() = when (this) {
            TODAY -> "Today"
            LATER -> "Later"
        }
}

data class TodoTask(
    val id: Long,
    val title: String,
    val horizon: TaskHorizon,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val steps: List<TodoStep>,
) {
    val isCompleted: Boolean
        get() = completedAt != null

    val completedStepCount: Int
        get() = steps.count(TodoStep::isCompleted)
}

data class TodoStep(
    val id: Long,
    val taskId: Long,
    val title: String,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isCompleted: Boolean
        get() = completedAt != null
}

fun validatedTaskTitle(value: String): String {
    val title = value.trim()
    if (title.isEmpty()) throw RecordValidationException("Title is required.")
    return title
}

fun parseTaskHorizon(value: String): TaskHorizon =
    runCatching { TaskHorizon.valueOf(value) }.getOrElse {
        throw RecordValidationException("Horizon is invalid.")
    }
