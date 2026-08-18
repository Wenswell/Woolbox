package com.wswensw.woolbox.record.model

enum class TaskRepeatMode {
    ONCE,
    DAILY,
}

data class TodoTask(
    val id: Long,
    val title: String,
    val repeatMode: TaskRepeatMode,
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
