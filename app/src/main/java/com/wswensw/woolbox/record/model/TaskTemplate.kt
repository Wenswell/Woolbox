package com.wswensw.woolbox.record.model

enum class TemplateCadence {
    MANUAL,
    DAILY,
}

data class TaskTemplate(
    val id: Long,
    val title: String,
    val cadence: TemplateCadence,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val steps: List<TaskTemplateStep>,
)

data class TaskTemplateStep(
    val id: Long,
    val templateId: Long,
    val title: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

fun validatedTemplateTitle(value: String): String {
    val title = value.trim()
    if (title.isEmpty()) throw RecordValidationException("Title is required.")
    return title
}
