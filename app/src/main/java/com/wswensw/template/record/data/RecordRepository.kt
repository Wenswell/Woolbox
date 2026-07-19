package com.wswensw.template.record.data

import androidx.room.withTransaction
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordItemDraft
import com.wswensw.template.record.model.RecordValidationException
import com.wswensw.template.record.model.RecordWindow
import com.wswensw.template.record.model.validated
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecordRepository(
    private val database: RecordDatabase,
) {
    private val dao = database.recordDao()

    val activeItems: Flow<List<RecordItem>> = dao.observeItems(archived = false).map(::mapItems)
    val archivedItems: Flow<List<RecordItem>> = dao.observeItems(archived = true).map(::mapItems)

    fun entriesOnDate(localDate: String): Flow<List<RecordEntry>> =
        dao.observeEntriesOnDate(localDate).map { rows -> rows.map(RecordEntryRow::toModel) }

    fun entriesBetween(startDate: String, endDate: String): Flow<List<RecordEntry>> =
        dao.observeEntriesBetween(startDate, endDate).map { rows -> rows.map(RecordEntryRow::toModel) }

    fun dateCounts(startDate: String, endDate: String): Flow<Map<String, Int>> =
        dao.observeDateCounts(startDate, endDate).map { rows ->
            rows.associate { it.localDate to it.recordCount }
        }

    suspend fun saveItem(itemId: Long?, input: RecordItemDraft): Long {
        val draft = input.validated()
        val excludedId = itemId ?: 0
        if (dao.nameExists(draft.name, excludedId)) {
            throw RecordValidationException("A record item with this name already exists.")
        }

        return database.withTransaction {
            if (itemId == null) {
                val now = System.currentTimeMillis()
                val newId = dao.insertItem(draft.toEntity(createdAt = now, updatedAt = now))
                dao.insertWindows(draft.windows.map { it.toEntity(newId) })
                newId
            } else {
                val current = dao.getItem(itemId)
                    ?: throw RecordValidationException("Record item not found.")
                if (dao.countEntries(itemId) > 0) {
                    requireUnchangedMeasurement(current.item, draft)
                }
                dao.updateItem(
                    draft.toEntity(
                        id = itemId,
                        archived = current.item.archived,
                        createdAt = current.item.createdAt,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                dao.deleteWindows(itemId)
                dao.insertWindows(draft.windows.map { it.toEntity(itemId) })
                itemId
            }
        }
    }

    suspend fun entryCount(itemId: Long): Int = dao.countEntries(itemId)

    suspend fun setArchived(itemId: Long, archived: Boolean) {
        if (dao.setArchived(itemId, archived, System.currentTimeMillis()) != 1) {
            throw RecordValidationException("Record item not found.")
        }
    }

    suspend fun deleteArchivedItem(itemId: Long) {
        val item = dao.getItem(itemId)?.item
            ?: throw RecordValidationException("Record item not found.")
        if (!item.archived) {
            throw RecordValidationException("Archive the record item before deleting it.")
        }
        if (dao.deleteItem(itemId) != 1) {
            throw RecordValidationException("Record item not found.")
        }
    }

    suspend fun createEntry(itemId: Long, value: Double, occurredAt: Long): Long {
        if (!value.isFinite() || value <= 0) {
            throw RecordValidationException("Value must be a positive number.")
        }
        val item = dao.getItem(itemId)?.item
            ?: throw RecordValidationException("Record item not found.")
        if (item.archived) {
            throw RecordValidationException("Archived items cannot be recorded.")
        }

        val local = Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault())
        return dao.insertEntry(
            RecordEntryEntity(
                itemId = itemId,
                occurredAt = occurredAt,
                localDate = local.toLocalDate().toString(),
                localMinute = local.hour * 60 + local.minute,
                value = value,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteEntry(entryId: Long) {
        if (dao.deleteEntry(entryId) != 1) {
            throw RecordValidationException("Record not found.")
        }
    }
}

private fun mapItems(rows: List<RecordItemWithWindows>): List<RecordItem> = rows.map { it.toModel() }

private fun RecordItemWithWindows.toModel() = RecordItem(
    id = item.id,
    name = item.name,
    description = item.description,
    valueType = item.valueType,
    defaultValue = item.defaultValue,
    stepValue = item.stepValue,
    unit = item.unit,
    goalValue = item.goalValue,
    goalPeriod = item.goalPeriod,
    priority = item.priority,
    archived = item.archived,
    windows = windows
        .sortedWith(compareBy(RecordWindowEntity::startMinute, RecordWindowEntity::endMinute))
        .map { window ->
            RecordWindow(
                id = window.id,
                startMinute = window.startMinute,
                endMinute = window.endMinute,
            )
        },
    createdAt = item.createdAt,
    updatedAt = item.updatedAt,
)

private fun RecordEntryRow.toModel() = RecordEntry(
    id = entry.id,
    itemId = entry.itemId,
    occurredAt = entry.occurredAt,
    localDate = entry.localDate,
    localMinute = entry.localMinute,
    value = entry.value,
    createdAt = entry.createdAt,
    itemName = itemName,
    itemUnit = itemUnit,
    itemValueType = itemValueType,
)

private fun RecordItemDraft.toEntity(
    id: Long = 0,
    archived: Boolean = false,
    createdAt: Long,
    updatedAt: Long,
) = RecordItemEntity(
    id = id,
    name = name,
    description = description,
    valueType = valueType,
    defaultValue = defaultValue,
    stepValue = stepValue,
    unit = unit,
    goalValue = goalValue,
    goalPeriod = goalPeriod,
    priority = priority,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun RecordWindow.toEntity(itemId: Long) = RecordWindowEntity(
    itemId = itemId,
    startMinute = startMinute,
    endMinute = endMinute,
)

private fun requireUnchangedMeasurement(current: RecordItemEntity, draft: RecordItemDraft) {
    if (current.valueType != draft.valueType || current.unit != draft.unit) {
        throw RecordValidationException("Type and unit cannot change after records exist.")
    }
}
