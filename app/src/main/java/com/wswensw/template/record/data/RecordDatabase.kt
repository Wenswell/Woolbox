package com.wswensw.template.record.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordValueType
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "record_items",
    indices = [Index(value = ["name"], unique = true)],
)
data class RecordItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val description: String,
    val valueType: RecordValueType,
    val defaultValue: Double?,
    val stepValue: Double,
    val unit: String,
    val goalValue: Double?,
    val goalPeriod: GoalPeriod?,
    val priority: Int,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "record_windows",
    foreignKeys = [
        ForeignKey(
            entity = RecordItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class RecordWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val startMinute: Int,
    val endMinute: Int,
)

@Entity(
    tableName = "record_entries",
    foreignKeys = [
        ForeignKey(
            entity = RecordItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("itemId"),
        Index(value = ["localDate", "localMinute"]),
    ],
)
data class RecordEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val occurredAt: Long,
    val localDate: String,
    val localMinute: Int,
    val value: Double,
    val createdAt: Long,
)

data class RecordItemWithWindows(
    @Embedded val item: RecordItemEntity,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val windows: List<RecordWindowEntity>,
)

data class RecordEntryRow(
    @Embedded val entry: RecordEntryEntity,
    val itemName: String,
    val itemUnit: String,
    val itemValueType: RecordValueType,
)

data class DateRecordCount(
    val localDate: String,
    val recordCount: Int,
)

class RecordConverters {
    @TypeConverter
    fun valueTypeToString(value: RecordValueType): String = value.name

    @TypeConverter
    fun stringToValueType(value: String): RecordValueType = RecordValueType.valueOf(value)

    @TypeConverter
    fun goalPeriodToString(value: GoalPeriod?): String? = value?.name

    @TypeConverter
    fun stringToGoalPeriod(value: String?): GoalPeriod? = value?.let(GoalPeriod::valueOf)
}

@Dao
interface RecordDao {
    @Transaction
    @Query("SELECT * FROM record_items WHERE archived = :archived ORDER BY name COLLATE NOCASE")
    fun observeItems(archived: Boolean): Flow<List<RecordItemWithWindows>>

    @Transaction
    @Query("SELECT * FROM record_items WHERE id = :id")
    suspend fun getItem(id: Long): RecordItemWithWindows?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM record_items
            WHERE name = :name COLLATE NOCASE AND id != :excludedId
        )
        """,
    )
    suspend fun nameExists(name: String, excludedId: Long): Boolean

    @Insert
    suspend fun insertItem(item: RecordItemEntity): Long

    @Update
    suspend fun updateItem(item: RecordItemEntity)

    @Insert
    suspend fun insertWindows(windows: List<RecordWindowEntity>)

    @Query("DELETE FROM record_windows WHERE itemId = :itemId")
    suspend fun deleteWindows(itemId: Long)

    @Query("SELECT COUNT(*) FROM record_entries WHERE itemId = :itemId")
    suspend fun countEntries(itemId: Long): Int

    @Query("UPDATE record_items SET archived = :archived, updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun setArchived(itemId: Long, archived: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM record_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long): Int

    @Insert
    suspend fun insertEntry(entry: RecordEntryEntity): Long

    @Query("DELETE FROM record_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long): Int

    @Query(
        """
        SELECT e.*, i.name AS itemName, i.unit AS itemUnit, i.valueType AS itemValueType
        FROM record_entries e
        JOIN record_items i ON i.id = e.itemId
        WHERE e.localDate = :localDate
        ORDER BY e.localMinute, e.createdAt, e.id
        """,
    )
    fun observeEntriesOnDate(localDate: String): Flow<List<RecordEntryRow>>

    @Query(
        """
        SELECT e.*, i.name AS itemName, i.unit AS itemUnit, i.valueType AS itemValueType
        FROM record_entries e
        JOIN record_items i ON i.id = e.itemId
        WHERE e.localDate BETWEEN :startDate AND :endDate
        ORDER BY e.localDate, e.localMinute, e.createdAt, e.id
        """,
    )
    fun observeEntriesBetween(startDate: String, endDate: String): Flow<List<RecordEntryRow>>

    @Query(
        """
        SELECT localDate, COUNT(*) AS recordCount
        FROM record_entries
        WHERE localDate BETWEEN :startDate AND :endDate
        GROUP BY localDate
        """,
    )
    fun observeDateCounts(startDate: String, endDate: String): Flow<List<DateRecordCount>>
}

@Database(
    entities = [RecordItemEntity::class, RecordWindowEntity::class, RecordEntryEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RecordConverters::class)
abstract class RecordDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao

    companion object {
        @Volatile
        private var instance: RecordDatabase? = null

        fun getInstance(context: Context): RecordDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RecordDatabase::class.java,
                "records.db",
            ).build().also { instance = it }
        }
    }
}
