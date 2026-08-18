package com.wswensw.woolbox.record.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wswensw.woolbox.record.model.GoalPeriod
import com.wswensw.woolbox.record.model.CommitmentRole
import com.wswensw.woolbox.record.model.DailyPlan
import com.wswensw.woolbox.record.model.DailyPlanPauseReason
import com.wswensw.woolbox.record.model.DailyPlanState
import com.wswensw.woolbox.record.model.RecordEntry
import com.wswensw.woolbox.record.model.RecordItem
import com.wswensw.woolbox.record.model.RecordItemDraft
import com.wswensw.woolbox.record.model.RecordMode
import com.wswensw.woolbox.record.model.RecordValidationException
import com.wswensw.woolbox.record.model.RecordValueType
import com.wswensw.woolbox.record.model.TaskTemplate
import com.wswensw.woolbox.record.model.TaskTemplateStep
import com.wswensw.woolbox.record.model.TemplateCadence
import com.wswensw.woolbox.record.model.TodayCommitment
import com.wswensw.woolbox.record.model.TodayCommitmentStep
import com.wswensw.woolbox.record.model.goalValues
import com.wswensw.woolbox.record.model.itemRecentDayValues
import com.wswensw.woolbox.record.model.isMeasurementValueOnGrid
import com.wswensw.woolbox.record.model.measurementStepCount
import com.wswensw.woolbox.record.model.validated
import com.wswensw.woolbox.record.model.validatedFirstMove
import com.wswensw.woolbox.record.model.validatedTemplateTitle
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

data class TemplateStepDraft(
    val title: String,
)

data class RecordsSnapshot(
    val activeItems: List<RecordItem>,
    val entries: List<RecordEntry>,
    val latestEntries: Map<Long, RecordEntry>,
    val templates: List<TaskTemplate>,
    val dailyPlan: DailyPlan?,
    val goalValues: Map<Long, Double>,
    val recentDayValues: Map<Long, List<Double>>,
)

class RecordRepository(context: Context) {
    private val credentialStore = BasicAuthStore(context.applicationContext)
    private val _snapshot = MutableStateFlow<RecordsSnapshot?>(null)
    private val _connection = MutableStateFlow(credentialStore.connection())

    val snapshot: StateFlow<RecordsSnapshot?> = _snapshot
    val connection: StateFlow<RecordsConnection> = _connection

    suspend fun refresh(localDate: String) = withContext(Dispatchers.IO) {
        val date = LocalDate.parse(localDate)
        val lookbackStart = date.minusDays(6).toString()
        val overview = request(
            "GET",
            "overview",
            query = mapOf(
                "startDate" to lookbackStart,
                "endDate" to localDate,
                "planDate" to localDate,
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
            templates = overview.getJSONArray("templates").mapObjects(::parseTemplate),
            dailyPlan = overview.nullableObject("dailyPlan")?.let(::parseDailyPlan),
            goalValues = goalValues(activeItems, date, todayEntries, rangeEntries),
            recentDayValues = itemRecentDayValues(
                itemIds = activeItems
                    .filter { it.mode == RecordMode.ACCUMULATE }
                    .map(RecordItem::id),
                entries = rangeEntries,
                today = date,
            ),
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

    suspend fun saveTemplate(
        templateId: Long?,
        title: String,
        cadence: TemplateCadence,
        steps: List<TemplateStepDraft>,
    ): Long = withContext(Dispatchers.IO) {
        if (steps.isEmpty()) {
            throw RecordValidationException("At least one subtask is required.")
        }
        request(
            if (templateId == null) "POST" else "PUT",
            if (templateId == null) "templates" else "templates/$templateId",
            body = JSONObject().apply {
                put("title", validatedTemplateTitle(title))
                put("cadence", cadence.name)
                put("steps", JSONArray(steps.map(::templateStepJson)))
            },
        ).getJSONObject("template").getLong("id")
    }

    suspend fun archiveTemplate(
        templateId: Long,
    ) = withContext(Dispatchers.IO) {
        request(
            "POST",
            "templates/batch-archive",
            body = JSONObject()
                .put("ids", JSONArray(listOf(templateId)))
                .put("archived", true),
        )
        Unit
    }

    suspend fun createDailyPlan(
        localDate: LocalDate,
        firstMove: String,
        primaryTemplateId: Long,
        secondaryTemplateId: Long?,
    ) = withContext(Dispatchers.IO) {
        request(
            "POST",
            "daily-plans",
            body = JSONObject().apply {
                put("localDate", localDate.toString())
                put("firstMove", validatedFirstMove(firstMove))
                put("primaryTemplateId", primaryTemplateId)
                putNullable("secondaryTemplateId", secondaryTemplateId)
            },
        )
        Unit
    }

    suspend fun getDailyPlan(localDate: LocalDate): DailyPlan? = withContext(Dispatchers.IO) {
        request("GET", "daily-plans/$localDate")
            .nullableObject("dailyPlan")
            ?.let(::parseDailyPlan)
    }

    suspend fun updateDailyPlan(
        plan: DailyPlan,
        firstMove: String,
        primaryTemplateId: Long,
        secondaryTemplateId: Long?,
        occurredAt: Long,
    ) = dailyPlanWrite(
        plan = plan,
        suffix = "",
        body = JSONObject().apply {
            put("firstMove", validatedFirstMove(firstMove))
            put("primaryTemplateId", primaryTemplateId)
            putNullable("secondaryTemplateId", secondaryTemplateId)
            put("occurredAt", occurredAt)
        },
    )

    suspend fun startDailyPlan(plan: DailyPlan, occurredAt: Long) =
        dailyPlanTransition(plan, "start", occurredAt)

    suspend fun reviewDailyPlan(plan: DailyPlan, currentCommitmentId: Long, occurredAt: Long) =
        dailyPlanTransition(plan, "review", occurredAt, currentCommitmentId)

    suspend fun continueDailyPlan(plan: DailyPlan, currentCommitmentId: Long, occurredAt: Long) =
        dailyPlanTransition(plan, "continue", occurredAt, currentCommitmentId)

    suspend fun addDailyPlanCommitment(plan: DailyPlan, templateId: Long, occurredAt: Long) =
        dailyPlanWrite(
            plan = plan,
            method = "POST",
            suffix = "/commitments",
            body = JSONObject()
                .put("templateId", templateId)
                .put("occurredAt", occurredAt),
        )

    suspend fun endDailyPlan(plan: DailyPlan, occurredAt: Long) =
        dailyPlanTransition(plan, "end", occurredAt)

    private suspend fun dailyPlanTransition(
        plan: DailyPlan,
        suffix: String,
        occurredAt: Long,
        currentCommitmentId: Long? = null,
    ) = dailyPlanWrite(
        plan = plan,
        suffix = "/$suffix",
        body = JSONObject()
            .put("occurredAt", occurredAt)
            .apply { currentCommitmentId?.let { put("currentCommitmentId", it) } },
    )

    private suspend fun dailyPlanWrite(
        plan: DailyPlan,
        suffix: String,
        body: JSONObject,
        method: String = "PUT",
    ) =
        withContext(Dispatchers.IO) {
            body.put("expectedRevision", plan.revision)
            request(method, "daily-plans/${plan.localDate}$suffix", body = body)
            Unit
        }

    suspend fun setCommitmentStepCompleted(
        plan: DailyPlan,
        stepId: Long,
        completed: Boolean,
        occurredAt: Long,
    ) = withContext(Dispatchers.IO) {
        request(
            "PUT",
            "commitment-steps/$stepId/completion",
            body = JSONObject()
                .put("completed", completed)
                .put("expectedRevision", plan.revision)
                .put("occurredAt", occurredAt),
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
        createdAt = value.getLong("createdAt"),
        updatedAt = value.getLong("updatedAt"),
    )
    if (item.mode == RecordMode.MEASUREMENT) {
        val minValue = item.minValue ?: throw RecordValidationException("Measurement minimum is missing from the server response.")
        val maxValue = item.maxValue ?: throw RecordValidationException("Measurement maximum is missing from the server response.")
        if (item.valueType != RecordValueType.NUMBER || item.defaultValue != null || item.goalValue != null || item.goalPeriod != null) {
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

private fun templateStepJson(step: TemplateStepDraft) = JSONObject()
    .put("title", validatedTemplateTitle(step.title))

private fun parseTemplate(value: JSONObject) = TaskTemplate(
    id = value.getLong("id"),
    title = value.getString("title"),
    cadence = TemplateCadence.valueOf(value.getString("cadence")),
    archived = value.getBoolean("archived"),
    createdAt = value.getLong("createdAt"),
    updatedAt = value.getLong("updatedAt"),
    steps = value.getJSONArray("steps").mapObjects(::parseTemplateStep),
)

internal fun parseDailyPlan(value: JSONObject) = DailyPlan(
    id = value.getLong("id"),
    localDate = LocalDate.parse(value.getString("localDate")),
    firstMove = value.getString("firstMove"),
    firstMoveCompletedAt = value.nullableLong("firstMoveCompletedAt"),
    currentCommitmentId = value.nullableLong("currentCommitmentId"),
    state = DailyPlanState.valueOf(value.getString("state")),
    pauseReason = value.nullableString("pauseReason")?.let(DailyPlanPauseReason::valueOf),
    completedRounds = value.getInt("completedRounds"),
    nextReviewAt = value.nullableLong("nextReviewAt"),
    revision = value.getInt("revision"),
    endedAt = value.nullableLong("endedAt"),
    createdAt = value.getLong("createdAt"),
    updatedAt = value.getLong("updatedAt"),
    commitments = value.getJSONArray("commitments").mapObjects(::parseCommitment),
)

private fun parseTemplateStep(value: JSONObject) = TaskTemplateStep(
    id = value.getLong("id"),
    templateId = value.getLong("templateId"),
    title = value.getString("title"),
    position = value.getInt("position"),
    createdAt = value.getLong("createdAt"),
    updatedAt = value.getLong("updatedAt"),
)

private fun parseCommitment(value: JSONObject) = TodayCommitment(
    id = value.getLong("id"),
    planId = value.getLong("planId"),
    templateId = value.nullableLong("templateId"),
    role = CommitmentRole.valueOf(value.getString("role")),
    position = value.getInt("position"),
    title = value.getString("title"),
    cadence = TemplateCadence.valueOf(value.getString("cadence")),
    completedAt = value.nullableLong("completedAt"),
    createdAt = value.getLong("createdAt"),
    updatedAt = value.getLong("updatedAt"),
    steps = value.getJSONArray("steps").mapObjects(::parseCommitmentStep),
)

private fun parseCommitmentStep(value: JSONObject) = TodayCommitmentStep(
    id = value.getLong("id"),
    commitmentId = value.getLong("commitmentId"),
    title = value.getString("title"),
    position = value.getInt("position"),
    completedAt = value.nullableLong("completedAt"),
    createdAt = value.getLong("createdAt"),
    updatedAt = value.getLong("updatedAt"),
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

private fun JSONObject.nullableObject(key: String): JSONObject? =
    if (isNull(key)) null else getJSONObject(key)

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
