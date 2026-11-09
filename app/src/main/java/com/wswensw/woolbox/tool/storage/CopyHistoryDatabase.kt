package com.wswensw.woolbox.tool.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "copy_history_entries",
    indices = [Index(value = ["capturedAt", "id"])],
)
internal data class CopyHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val capturedAt: Long,
    val captureDraftText: String,
    val readingDraftText: String,
    val useReadingOrder: Boolean,
)

@Entity(
    tableName = "copy_history_blocks",
    primaryKeys = ["historyId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CopyHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class CopyHistoryBlockEntity(
    val historyId: Long,
    val position: Int,
    val blockId: String,
    val text: String,
    val source: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val windowId: Int,
    val captureOrder: Int,
    val selected: Boolean,
)

internal data class CopyHistoryWithBlocks(
    @Embedded val entry: CopyHistoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "historyId",
    )
    val blocks: List<CopyHistoryBlockEntity>,
)

internal data class CopyHistorySummaryRow(
    val id: Long,
    val packageName: String,
    val capturedAt: Long,
    val textPreview: String,
    val blockCount: Int,
)

@Dao
internal interface CopyHistoryDao {
    @Query(
        """
        SELECT e.id,
               e.packageName,
               e.capturedAt,
               substr(
                   CASE WHEN e.useReadingOrder
                       THEN e.readingDraftText
                       ELSE e.captureDraftText
                   END,
                   1,
                   240
               ) AS textPreview,
               (
                   SELECT COUNT(*)
                   FROM copy_history_blocks b
                   WHERE b.historyId = e.id
               ) AS blockCount
        FROM copy_history_entries e
        ORDER BY e.capturedAt DESC, e.id DESC
        """,
    )
    fun observeHistory(): Flow<List<CopyHistorySummaryRow>>

    @Transaction
    @Query("SELECT * FROM copy_history_entries WHERE id = :historyId")
    suspend fun get(historyId: Long): CopyHistoryWithBlocks?

    @Insert
    suspend fun insertEntry(entry: CopyHistoryEntity): Long

    @Insert
    suspend fun insertBlocks(blocks: List<CopyHistoryBlockEntity>)

    @Query(
        """
        UPDATE copy_history_entries
        SET captureDraftText = :captureDraftText,
            readingDraftText = :readingDraftText,
            useReadingOrder = :useReadingOrder
        WHERE id = :historyId
        """,
    )
    suspend fun updateSession(
        historyId: Long,
        captureDraftText: String,
        readingDraftText: String,
        useReadingOrder: Boolean,
    ): Int

    @Query("UPDATE copy_history_blocks SET selected = 0 WHERE historyId = :historyId")
    suspend fun clearSelection(historyId: Long)

    @Query(
        """
        UPDATE copy_history_blocks
        SET selected = 1
        WHERE historyId = :historyId AND blockId IN (:selectedIds)
        """,
    )
    suspend fun selectBlocks(historyId: Long, selectedIds: Set<String>)

    @Query("DELETE FROM copy_history_blocks WHERE historyId = :historyId")
    suspend fun deleteBlocks(historyId: Long)

    @Query("DELETE FROM copy_history_entries WHERE id = :historyId")
    suspend fun delete(historyId: Long): Int
}

@Database(
    entities = [CopyHistoryEntity::class, CopyHistoryBlockEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CopyHistoryDatabase : RoomDatabase() {
    internal abstract fun copyHistoryDao(): CopyHistoryDao

    companion object {
        @Volatile
        private var instance: CopyHistoryDatabase? = null

        fun getInstance(context: Context): CopyHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CopyHistoryDatabase::class.java,
                "copy_history.db",
            ).build().also { instance = it }
        }
    }
}
