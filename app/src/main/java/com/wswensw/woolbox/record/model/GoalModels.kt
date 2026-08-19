package com.wswensw.woolbox.record.model

import java.time.LocalDate

data class GoalSubgoal(
    val id: Long,
    val goalId: Long,
    val title: String,
    val targetValue: Double,
    val position: Int,
    val completedAt: Long?,
)

data class GoalPool(
    val id: Long,
    val title: String,
    val description: String,
    val unit: String,
    val defaultValue: Double?,
    val stepValue: Double,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val archived: Boolean,
    val currentValue: Double,
    val subgoals: List<GoalSubgoal>,
) {
    val completed: Boolean get() = subgoals.isNotEmpty() && subgoals.all { it.completedAt != null }
    fun pending(today: LocalDate = LocalDate.now()): Boolean = today < startDate
    fun expired(today: LocalDate = LocalDate.now()): Boolean = !completed && today > endDate
}

data class GoalSubgoalDraft(
    val id: Long? = null,
    val title: String,
    val targetValue: Double,
)

data class GoalPoolDraft(
    val title: String,
    val description: String,
    val unit: String,
    val defaultValue: Double?,
    val stepValue: Double,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val subgoals: List<GoalSubgoalDraft>,
)

fun GoalPoolDraft.validated(): GoalPoolDraft {
    val cleanTitle = title.trim()
    val cleanUnit = unit.trim()
    require(cleanTitle.isNotEmpty()) { "Title is required." }
    require(cleanUnit.isNotEmpty()) { "Unit is required." }
    require(stepValue.isFinite() && stepValue > 0) { "Step must be a positive number." }
    require(defaultValue == null || defaultValue.isFinite() && defaultValue > 0) {
        "Default value must be a positive number."
    }
    require(startDate <= endDate) { "Start date must not be after end date." }
    require(subgoals.isNotEmpty()) { "At least one subgoal is required." }
    val normalizedSubgoals = subgoals.map { subgoal ->
        val cleanSubgoalTitle = subgoal.title.trim()
        require(cleanSubgoalTitle.isNotEmpty()) { "Subgoal title is required." }
        require(subgoal.targetValue.isFinite() && subgoal.targetValue > 0) {
            "Subgoal target must be a positive number."
        }
        subgoal.copy(title = cleanSubgoalTitle)
    }
    return copy(
        title = cleanTitle,
        description = description.trim(),
        unit = cleanUnit,
        subgoals = normalizedSubgoals,
    )
}
