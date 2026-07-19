package com.wswensw.template.tool.model

data class CopyHistoryEntry(
    val id: Long,
    val capturedAt: Long,
    val session: CopySessionUiState,
)

data class CopyHistorySummary(
    val id: Long,
    val packageName: String,
    val capturedAt: Long,
    val textPreview: String,
    val blockCount: Int,
)
