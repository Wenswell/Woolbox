package com.wswensw.woolbox.tool.storage

import androidx.room.withTransaction
import com.wswensw.woolbox.tool.model.CopyHistoryEntry
import com.wswensw.woolbox.tool.model.CopyHistorySummary
import com.wswensw.woolbox.tool.model.CopySessionScreen
import com.wswensw.woolbox.tool.model.CopySessionUiState
import com.wswensw.woolbox.tool.model.CopyTextBlock
import com.wswensw.woolbox.tool.model.ScreenBounds
import com.wswensw.woolbox.tool.model.TextSnapshot
import com.wswensw.woolbox.tool.model.TextSource
import com.wswensw.woolbox.tool.model.initialCopySessionUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CopyHistoryRepository(
    private val database: CopyHistoryDatabase,
) {
    private val dao = database.copyHistoryDao()

    val history: Flow<List<CopyHistorySummary>> = dao.observeHistory().map { entries ->
        entries.map(CopyHistorySummaryRow::toModel)
    }

    suspend fun get(historyId: Long): CopyHistoryEntry? = dao.get(historyId)?.toModel()

    suspend fun insertSnapshot(
        snapshot: TextSnapshot,
        capturedAt: Long = System.currentTimeMillis(),
    ): Long {
        require(snapshot.blocks.isNotEmpty()) { "Copy history requires a non-empty snapshot." }
        val state = snapshot.initialCopySessionUiState()
        return database.withTransaction {
            val historyId = dao.insertEntry(state.toEntity(capturedAt = capturedAt))
            dao.insertBlocks(state.toBlockEntities(historyId))
            historyId
        }
    }

    suspend fun updateSession(historyId: Long, state: CopySessionUiState) {
        database.withTransaction {
            check(
                dao.updateSession(
                    historyId = historyId,
                    captureDraftText = state.captureDraftText,
                    readingDraftText = state.reorderedDraftText,
                    useReadingOrder = state.useReadingOrder,
                ) == 1,
            ) { "Copy history entry $historyId does not exist." }
            dao.clearSelection(historyId)
            if (state.selectedIds.isNotEmpty()) {
                dao.selectBlocks(historyId, state.selectedIds)
            }
        }
    }

    suspend fun delete(historyId: Long) {
        check(dao.delete(historyId) == 1) { "Copy history entry $historyId does not exist." }
    }
}

internal fun CopyHistorySummaryRow.toModel(): CopyHistorySummary = CopyHistorySummary(
    id = id,
    packageName = packageName,
    capturedAt = capturedAt,
    textPreview = textPreview,
    blockCount = blockCount,
)

internal fun CopySessionUiState.toEntity(
    capturedAt: Long,
    id: Long = 0,
): CopyHistoryEntity = CopyHistoryEntity(
    id = id,
    packageName = packageName,
    capturedAt = capturedAt,
    captureDraftText = captureDraftText,
    readingDraftText = reorderedDraftText,
    useReadingOrder = useReadingOrder,
)

internal fun CopySessionUiState.toBlockEntities(historyId: Long): List<CopyHistoryBlockEntity> =
    blocks.mapIndexed { position, block ->
        CopyHistoryBlockEntity(
            historyId = historyId,
            position = position,
            blockId = block.id,
            text = block.text,
            source = block.source.name,
            left = block.bounds.left,
            top = block.bounds.top,
            right = block.bounds.right,
            bottom = block.bounds.bottom,
            windowId = block.windowId,
            captureOrder = block.captureOrder,
            selected = block.id in selectedIds,
        )
    }

internal fun CopyHistoryWithBlocks.toModel(): CopyHistoryEntry {
    val orderedBlocks = blocks.sortedBy(CopyHistoryBlockEntity::position)
    return CopyHistoryEntry(
        id = entry.id,
        capturedAt = entry.capturedAt,
        session = CopySessionUiState(
            packageName = entry.packageName,
            blocks = orderedBlocks.map(CopyHistoryBlockEntity::toModel),
            selectedIds = orderedBlocks
                .filter(CopyHistoryBlockEntity::selected)
                .mapTo(linkedSetOf(), CopyHistoryBlockEntity::blockId),
            screen = CopySessionScreen.EDITOR,
            captureDraftText = entry.captureDraftText,
            reorderedDraftText = entry.readingDraftText,
            useReadingOrder = entry.useReadingOrder,
        ),
    )
}

private fun CopyHistoryBlockEntity.toModel(): CopyTextBlock = CopyTextBlock(
    id = blockId,
    text = text,
    source = TextSource.valueOf(source),
    bounds = ScreenBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    ),
    windowId = windowId,
    captureOrder = captureOrder,
)
