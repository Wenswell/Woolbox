package com.wswensw.woolbox.log

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "events")
data class ToolLogEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val tool: String,
    val session: String,
    val action: String,
    val details: String,
) {
    fun json(): String = JSONObject().put("id", id).put("time", time).put("tool", tool)
        .put("session", session).put("action", action).put("details", details).toString()
}

@Dao
interface ToolLogDao {
    @Insert suspend fun insert(event: ToolLogEvent)
    @Query("SELECT * FROM events WHERE :tool = '' OR tool = :tool ORDER BY id DESC LIMIT 200")
    fun recent(tool: String): Flow<List<ToolLogEvent>>
    @Query("SELECT * FROM events WHERE id > :after AND (:tool = '' OR tool = :tool) ORDER BY id LIMIT 200")
    suspend fun batch(tool: String, after: Long): List<ToolLogEvent>
}

@Database(entities = [ToolLogEvent::class], version = 1, exportSchema = false)
abstract class ToolLogDatabase : RoomDatabase() { abstract fun events(): ToolLogDao }

object ToolLogs {
    private lateinit var database: ToolLogDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var pending: Job? = null
    private val mutableError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val error = mutableError as kotlinx.coroutines.flow.StateFlow<String?>
    @Synchronized fun initialize(context: Context) {
        if (!::database.isInitialized) database = Room.databaseBuilder(context.applicationContext,
            ToolLogDatabase::class.java, "tool_logs.db").build()
    }
    fun session(): String = UUID.randomUUID().toString()
    @Synchronized fun record(context: Context, tool: String, session: String, action: String, details: String = "") {
        initialize(context)
        val event = ToolLogEvent(tool = tool, session = session, action = action, details = details)
        val previous = pending
        pending = scope.launch {
            previous?.join()
            try { database.events().insert(event) }
            catch (e: Exception) {
                mutableError.value = e.message ?: e.javaClass.simpleName
                android.util.Log.e("ToolLogs", "Log persistence failed", e)
            }
        }
    }
    fun recent(context: Context, tool: String): Flow<List<ToolLogEvent>> {
        initialize(context)
        return database.events().recent(tool)
    }
    suspend fun export(context: Context, tool: String, uri: android.net.Uri) = withContext(Dispatchers.IO) {
        initialize(context)
        val pendingWrites = synchronized(this@ToolLogs) { pending }
        pendingWrites?.join()
        requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { writer ->
            var after = 0L
            while (true) {
                val batch = database.events().batch(tool, after)
                if (batch.isEmpty()) break
                batch.forEach { writer.appendLine(it.json()) }
                after = batch.last().id
            }
        }
    }
}
