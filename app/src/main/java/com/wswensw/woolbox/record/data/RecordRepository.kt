package com.wswensw.woolbox.record.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordItemDraft
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordValidationException
import com.wswensw.woolbox.record.model.RecordValueType
import com.wswensw.woolbox.record.model.RecordWindow
import com.wswensw.woolbox.record.model.TodoStep
import com.wswensw.woolbox.record.model.TaskHorizon
import com.wswensw.woolbox.record.model.TodoTask
import com.wswensw.woolbox.record.model.parseTaskHorizon
import com.wswensw.woolbox.record.model.FOCUS_HISTORY_DAYS
import com.wswensw.woolbox.record.model.StreakDays
import com.wswensw.woolbox.record.model.dailyGoalStreak
import com.wswensw.woolbox.record.model.goalValues
import com.wswensw.woolbox.record.model.habitMinutes
import com.wswensw.woolbox.record.model.itemStreaks
import com.wswensw.woolbox.record.model.itemRecentDayValues
import com.wswensw.woolbox.record.model.isMeasurementValueOnGrid
import com.wswensw.woolbox.record.model.measurementStepCount
import com.wswensw.woolbox.record.model.validated
import com.wswensw.woolbox.record.model.validatedTaskTitle
import com.wswensw.woolbox.record.model.validatedWindows
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class RecordsConnection(
    val isConfigured: Boolean,
    val username: String,
)

/** A subtask being created, with the times of day it is suggested at. */
data class TodoStepDraft(
    val title: String,
    val windows: List<RecordWindow> = emptyList(),
)

data class RecordsSnapshot(
    val activeItems: List<RecordItem>,
    val entries: List<RecordEntry>,
    val latestEntries: Map<Long, RecordEntry>,
    val tasks: List<TodoTask>,
    val goalValues: Map<Long, Double>,
    val recentDayValues: Map<Long, List<Double>>,
    /** Time of day each item is normally recorded at, learned from history. */
    val habitMinutes: Map<Long, Int>,
    /** Consecutive days each item was done, counting back from today. */
    val itemStreaks: Map<Long, Int>,
    /** Consecutive days every daily goal was met. */
    val goalStreak: StreakDays,
)

class RecordRepository(context: Context) {
    private val credentialStore = BasicAuthStore(context.applicationContext)
    private val _snapshot = MutableStateFlow<RecordsSnapshot?>(null)
    private val _connection = MutableStateFlow(credentialStore.connection())

    val snapshot: StateFlow<RecordsSnapshot?> = _snapshot
    val connection: StateFlow<RecordsConnection> = _connection

    suspend fun refresh(localDate: String) = withContext(Dispatchers.IO) {
        val date = LocalDate.parse(localDate)
        // Habits and streaks need more than the card window, so one range
        // covers both: the cards read the last 7 days out of the same entries.
        val lookbackStart = date.minusDays((FOCUS_HISTORY_DAYS - 1).toLong()).toString()
        val overview = request(
            "GET",
            "overview",
            query = mapOf(
                "startDate" to lookbackStart,
                "endDate" to localDate,
            ),
        )
        val activeItems = overview.getJSONArray("items").mapObjects(::parseItem)
        val rangeEntries = overview.getJSONArray("accumulateEntries").mapObjects(::parseEntry)
        val todayEntries = rangeEntries.filter { it.localDate == localDate }
        _snapshot.value = RecordsSnapshot(
            activeItems = activeItems,
            entries = todayEntries,
            latestEntries = overview.getJSONArray("latestMeasurementEntries")
            .mapObjects(::parseEntry)
            .associateBy(RecordEntry::itemId),
            tasks = overview.getJSONArray("tasks").mapObjects(::parseTask),
            goalValues = goalValues(activeItems, date, todayEntries, rangeEntries),
            recentDayValues = itemRecentDayValues(
                itemIds = activeItems
                    .filter { it.mode == RecordMode.ACCUMULATE }
                    .map(RecordItem::id),
                entries = rangeEntries,
                today = date,
            ),
            habitMinutes = habitMinutes(rangeEntries, date),
            itemStreaks = itemStreaks(activeItems, rangeEntries, date),
            goalStreak = dailyGoalStreak(activeItems, rangeEntries, date),
        )
    }

    suspend fun saveItem(itemId: Long?, input: RecordItemDraft): Long = withContext(Dispatchers.IO) {
        val draft = input.validated()
        val body = JSONObject().apply {
            put("name", draft.name)
            put("description", draft.description)
            put("mode", draft.mode.name)
            put("valueType", draft.valueType.name)
            putNullable("defaultValue", draft.defaultValue)
            putNullable("minValue", draft.minValue)
            putNullable("maxValue", draft.maxValue)
            put("stepValue", draft.stepValue)
            put("unit", draft.unit)
            putNullable("goalValue", draft.goalValue)
            putNullable("goalPeriod", draft.goalPeriod?.name)
            put("priority", draft.priority)
            put("windows", windowsJson(draft.windows))
        }
        request(
            method = if (itemId == null) "POST" else "PUT",
            path = if (itemId == null) "items" else "items/$itemId",
            body = body,
        ).getJSONObject("item").getLong("id")
    }

    suspend fun createEntry(item: RecordItem, value: Double, occurredAt: Long): Long =
        withContext(Dispatchers.IO) {
            requireEntryValue(item, value)
            val local = Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault())
            val body = JSONObject()
                .put("itemId", item.id)
                .put("occurredAt", occurredAt)
                .put("localDate", local.toLocalDate().toString())
                .put("localMinute", local.hour * 60 + local.minute)
                .put("value", value)
            request("POST", "entries", body = body).getJSONObject("entry").getLong("id")
        }

    suspend fun entriesForItem(itemId: Long, startDate: String, endDate: String): List<RecordEntry> =
        withContext(Dispatchers.IO) {
            request(
                "GET",
                "entries",
                query = mapOf("startDate" to startDate, "endDate" to endDate, "itemId" to itemId.toString()),
            ).getJSONArray("entries").mapObjects(::parseEntry)
        }

    suspend fun deleteEntry(entryId: Long) = withContext(Dispatchers.IO) {
        request("DELETE", "entries/$entryId")
        Unit
    }

    suspend fun createTask(
        title: String,
        steps: List<TodoStepDraft>,
        horizon: TaskHorizon,
    ): Long = withContext(Dispatchers.IO) {
        if (steps.isEmpty()) {
            throw RecordValidationException("At least one subtask is required.")
        }
        request(
            "POST",
            "tasks",
            body = JSONObject()
                .put("title", validatedTaskTitle(title))
                .put("horizon", horizon.name)
                .put("steps", JSONArray(steps.map(::stepJson))),
        ).getJSONObject("task").getLong("id")
    }

    suspend fun updateTask(taskId: Long, title: String, horizon: TaskHorizon) = withContext(Dispatchers.IO) {
        request(
            "PUT",
            "tasks/$taskId",
            body = JSONObject()
                .put("title", validatedTaskTitle(title))
                .put("horizon", horizon.name),
        )
        Unit
    }

    suspend fun deleteTask(taskId: Long) = withContext(Dispatchers.IO) {
        request("DELETE", "tasks/$taskId")
        Unit
    }

    suspend fun createStep(
        taskId: Long,
        title: String,
        windows: List<RecordWindow> = emptyList(),
    ): Long = withContext(Dispatchers.IO) {
        request(
            "POST",
            "tasks/$taskId/steps",
            body = stepJson(TodoStepDraft(title = title, windows = windows)),
        ).getJSONObject("step").getLong("id")
    }

    suspend fun updateStep(
        stepId: Long,
        title: String,
        windows: List<RecordWindow> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        request(
            "PUT",
            "steps/$stepId",
            body = stepJson(TodoStepDraft(title = title, windows = windows)),
        )
        Unit
    }

    suspend fun setStepCompleted(stepId: Long, completed: Boolean) = withContext(Dispatchers.IO) {
        request("PUT", "steps/$stepId/completion", body = JSONObject().put("completed", completed))
        Unit
    }

    suspend fun deleteStep(stepId: Long) = withContext(Dispatchers.IO) {
        request("DELETE", "steps/$stepId")
        Unit
    }

    suspend fun reorderSteps(taskId: Long, stepIds: List<Long>) = withContext(Dispatchers.IO) {
        request(
            "PUT",
            "tasks/$taskId/steps/order",
            body = JSONObject().put("ids", JSONArray(stepIds)),
        )
        Unit
    }

    suspend fun configureCredentials(username: String, password: String) = withContext(Dispatchers.IO) {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isEmpty() || password.isEmpty()) {
            throw RecordValidationException("Username and password are required.")
        }
        val candidate = normalizedUsername to password
        request(
            method = "GET",
            path = "items",
            query = mapOf("archived" to "false"),
            authentication = candidate,
        )
        credentialStore.save(normalizedUsername, password)
        _connection.value = credentialStore.connection()
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        query: Map<String, String> = emptyMap(),
        authentication: Pair<String, String>? = null,
    ): JSONObject {
        val auth = authentication ?: credentialStore.load()
            ?: throw RecordValidationException("Records credentials are not configured.")
        val url = (BASE_API_URL + path).toHttpUrl().newBuilder().apply {
            query.forEach(::addQueryParameter)
        }.build()
        val requestBody = body?.toString()?.toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(auth.first, auth.second))
            .method(method, requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrNull()
                throw RecordValidationException(message ?: "Records request failed (${response.code}).")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    companion object {
        const val BASE_URL = "https://gateway.1662803.xyz/apps/records/"
        private const val BASE_API_URL = "${BASE_URL}api/"
        private val JSON = "application/json".toMediaType()
        private val client = OkHttpClient()
    }
}

private fun parseItem(value: JSONObject): RecordItem {
    val item = RecordItem(
        id = value.getLong("id"),
        name = value.getString("name"),
        description = value.getString("description"),
        mode = RecordMode.valueOf(value.getString("mode")),
        valueType = RecordValueType.valueOf(value.getString("valueType")),
        defaultValue = value.nullableDouble("defaultValue"),
        minValue = value.nullableDouble("minValue"),
        maxValue = value.nullableDouble("maxValue"),
        stepValue = value.getDouble("stepValue"),
        unit = value.getString("unit"),
        goalValue = value.nullableDouble("goalValue"),
        goalPeriod = value.nullableString("goalPeriod")?.let(GoalPeriod::valueOf),
        priority = value.getInt("priority"),
        archived = value.getBoolean("archived"),
        windows = value.getJSONArray("windows").mapObjects(::parseWindow),
        createdAt = value.getLong("createdAt"),
        updatedAt = value.getLong("updatedAt"),
    )
    if (item.mode == RecordMode.MEASUREMENT) {
        val minValue = item.minValue ?: throw RecordValidationException("Measurement minimum is missing from the server response.")
        val maxValue = item.maxValue ?: throw RecordValidationException("Measurement maximum is missing from the server response.")
        if (item.valueType != RecordValueType.NUMBER || item.defaultValue != null || item.goalValue != null || item.goalPeriod != null || item.windows.isNotEmpty()) {
            throw RecordValidationException("Measurement configuration is invalid in the server response.")
        }
        measurementStepCount(minValue, maxValue, item.stepValue)
    }
    return item
}

private fun parseEntry(value: JSONObject) = RecordEntry(
    id = value.getLong("id"),
    itemId = value.getLong("itemId"),
    occurredAt = value.getLong("occurredAt"),
    localDate = value.getString("localDate"),
    localMinute = value.getInt("localMinute"),
    value = value.getDouble("value"),
    createdAt = value.getLong("createdAt"),
    itemName = value.getString("itemName"),
    itemMode = RecordMode.valueOf(value.getString("itemMode")),
    itemUnit = value.getString("itemUnit"),
    itemValueType = RecordValueType.valueOf(value.getString("itemValueType")),
)

private fun stepJson(step: TodoStepDraft) = JSONObject()
    .put("title", validatedTaskTitle(step.title))
    .put("windows", windowsJson(step.windows))

private fun windowsJson(windows: List<RecordWindow>) = JSONArray(
    validatedWindows(windows).map { window ->
        JSONObject()
            .put("startMinute", window.startMinute)
            .put("endMinute", window.endMinute)
    },
)

private fun parseWindow(value: JSONObject) = RecordWindow(
    id = value.getLong("id"),
    startMinute = value.getInt("startMinute"),
    endMinute = value.getInt("endMinute"),
)

private fun parseTask(value: JSONObject) = TodoTask(
    id = value.getLong("id"),
    title = value.getString("title"),
    horizon = parseTaskHorizon(value.getString("horizon")),
    completedAt = value.nullableLong("completedAt"),
    createdAt = value.getLong("createdAt"),
    updatedAt = value.getLong("updatedAt"),
    steps = value.getJSONArray("steps").mapObjects(::parseStep),
)

private fun parseStep(value: JSONObject) = TodoStep(
    id = value.getLong("id"),
    taskId = value.getLong("taskId"),
    title = value.getString("title"),
    completedAt = value.nullableLong("completedAt"),
    createdAt = value.getLong("createdAt"),
    updatedAt = value.getLong("updatedAt"),
    windows = value.getJSONArray("windows").mapObjects(::parseWindow),
)

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { index -> transform(getJSONObject(index)) }

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.nullableDouble(key: String): Double? =
    if (isNull(key)) null else getDouble(key)

private fun JSONObject.nullableLong(key: String): Long? =
    if (isNull(key)) null else getLong(key)

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else getString(key)

private fun requireEntryValue(item: RecordItem, value: Double) {
    if (!value.isFinite()) {
        throw RecordValidationException("Value must be a finite number.")
    }
    if (item.mode == RecordMode.ACCUMULATE && value <= 0) {
        throw RecordValidationException("Value must be a positive number.")
    }
    if (item.mode == RecordMode.MEASUREMENT) {
        val minValue = item.minValue ?: throw RecordValidationException("Measurement minimum is missing.")
        val maxValue = item.maxValue ?: throw RecordValidationException("Measurement maximum is missing.")
        if (!isMeasurementValueOnGrid(value, minValue, maxValue, item.stepValue)) {
            throw RecordValidationException("Value must be within the configured measurement range and step.")
        }
    }
}

private class BasicAuthStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun connection(): RecordsConnection = RecordsConnection(
        isConfigured = preferences.contains(DATA),
        username = preferences.getString(USERNAME, "").orEmpty(),
    )

    fun save(username: String, password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal("$username\u0000$password".toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString(USERNAME, username)
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): Pair<String, String>? {
        val iv = preferences.getString(IV, null) ?: return null
        val data = preferences.getString(DATA, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        val values = String(
            cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)),
            StandardCharsets.UTF_8,
        ).split('\u0000', limit = 2)
        return values.takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES = "records_credentials"
        private const val KEY_ALIAS = "records_basic_auth"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val USERNAME = "username"
        private const val IV = "iv"
        private const val DATA = "data"
    }
}
