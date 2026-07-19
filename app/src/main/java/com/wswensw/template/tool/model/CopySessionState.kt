package com.wswensw.template.tool.model

enum class CopySessionScreen {
    EDITOR,
    TEXT_SELECTION,
}

data class CopySessionUiState(
    val packageName: String = "",
    val blocks: List<CopyTextBlock> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val screen: CopySessionScreen = CopySessionScreen.EDITOR,
    val captureDraftText: String = "",
    val reorderedDraftText: String = "",
    val useReadingOrder: Boolean = true,
) {
    val currentDraftText: String
        get() = if (useReadingOrder) reorderedDraftText else captureDraftText

    fun updateCurrentDraft(text: String): CopySessionUiState = if (useReadingOrder) {
        copy(reorderedDraftText = text)
    } else {
        copy(captureDraftText = text)
    }

    fun toggleSelection(id: String): CopySessionUiState = withSelection(
        selectedIds.toMutableSet().also { selected ->
            if (!selected.add(id)) selected.remove(id)
        },
    )

    fun toggleAll(): CopySessionUiState {
        val allIds = blocks.mapTo(linkedSetOf(), CopyTextBlock::id)
        return withSelection(if (selectedIds.size == allIds.size) emptySet() else allIds)
    }

    private fun withSelection(selectedIds: Set<String>): CopySessionUiState = copy(
        selectedIds = selectedIds,
        captureDraftText = CopyTextSelection.selected(
            blocks = blocks,
            selectedIds = selectedIds,
            order = CopyTextOrder.CAPTURE,
        ),
        reorderedDraftText = CopyTextSelection.selected(
            blocks = blocks,
            selectedIds = selectedIds,
            order = CopyTextOrder.READING,
        ),
    )
}

fun TextSnapshot.initialCopySessionUiState(): CopySessionUiState {
    val selectedIds = blocks.mapTo(linkedSetOf(), CopyTextBlock::id)
    return CopySessionUiState(
        packageName = packageName,
        blocks = blocks,
        selectedIds = selectedIds,
        captureDraftText = CopyTextSelection.selected(
            blocks = blocks,
            selectedIds = selectedIds,
            order = CopyTextOrder.CAPTURE,
        ),
        reorderedDraftText = CopyTextSelection.selected(
            blocks = blocks,
            selectedIds = selectedIds,
            order = CopyTextOrder.READING,
        ),
    )
}
