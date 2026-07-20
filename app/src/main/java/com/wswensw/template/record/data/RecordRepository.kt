package com.wswensw.template.record.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wswensw.template.record.model.GoalPeriod
import com.wswensw.template.record.model.RecordEntry
import com.wswensw.template.record.model.RecordItem
import com.wswensw.template.record.model.RecordItemDraft
import com.wswensw.template.record.model.RecordValidationException
import com.wswensw.template.record.model.RecordValueType
import com.wswensw.template.record.model.RecordWindow
import com.wswensw.template.record.model.validated
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
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

class RecordRepository(context: Context) {
    private val credentialStore = BasicAuthStore(context.applicationContext)
    private val client = OkHttpClient()
    private val _activeItems = MutableStateFlow<List<RecordItem>>(emptyList())
    private val _archivedItems = MutableStateFlow<List<RecordItem>>(emptyList())
    private val _entries = MutableStateFlow<List<RecordEntry>>(emptyList())
    private val _connection = MutableStateFlow(credentialStore.connection())

    val activeItems: StateFlow<List<RecordItem>> = _activeItems
    val archivedItems: StateFlow<List<RecordItem>> = _archivedItems
    val entries: StateFlow<List<RecordEntry>> = _entries
    val connection: StateFlow<RecordsConnection> = _connection

    suspend fun refresh(localDate: String) = withContext(Dispatchers.IO) {
        val active = request("GET", "items", query = mapOf("archived" to "false"))
        val archived = request("GET", "items", query = mapOf("archived" to "true"))
        val entries = request(
            "GET",
            "entries",
            query = mapOf("startDate" to localDate, "endDate" to localDate),
        )
        _activeItems.value = active.getJSONArray("items").mapObjects(::parseItem)
        _archivedItems.value = archived.getJSONArray("items").mapObjects(::parseItem)
        _entries.value = entries.getJSONArray("entries").mapObjects(::parseEntry)
    }

    suspend fun saveItem(itemId: Long?, input: RecordItemDraft): Long = withContext(Dispatchers.IO) {
        val draft = input.validated()
        val body = JSONObject().apply {
            put("name", draft.name)
            put("description", draft.description)
            put("valueType", draft.valueType.name)
            putNullable("defaultValue", draft.defaultValue)
            put("stepValue", draft.stepValue)
            put("unit", draft.unit)
            putNullable("goalValue", draft.goalValue)
            putNullable("goalPeriod", draft.goalPeriod?.name)
            put("priority", draft.priority)
            put("windows", JSONArray(draft.windows.map { window ->
                JSONObject()
                    .put("startMinute", window.startMinute)
                    .put("endMinute", window.endMinute)
            }))
        }
        request(
            method = if (itemId == null) "POST" else "PUT",
            path = if (itemId == null) "items" else "items/$itemId",
            body = body,
        ).getJSONObject("item").getLong("id")
    }

    suspend fun entryCount(itemId: Long): Int = withContext(Dispatchers.IO) {
        request(
            "GET",
            "entries",
            query = mapOf(
                "startDate" to "0001-01-01",
                "endDate" to "9999-12-31",
                "itemId" to itemId.toString(),
            ),
        ).getJSONArray("entries").length()
    }

    suspend fun createEntry(itemId: Long, value: Double, occurredAt: Long): Long =
        withContext(Dispatchers.IO) {
            if (!value.isFinite() || value <= 0) {
                throw RecordValidationException("Value must be a positive number.")
            }
            val local = Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault())
            val body = JSONObject()
                .put("itemId", itemId)
                .put("occurredAt", occurredAt)
                .put("localDate", local.toLocalDate().toString())
                .put("localMinute", local.hour * 60 + local.minute)
                .put("value", value)
            request("POST", "entries", body = body).getJSONObject("entry").getLong("id")
        }

    suspend fun deleteEntry(entryId: Long) = withContext(Dispatchers.IO) {
        request("DELETE", "entries/$entryId")
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
    }
}

private fun parseItem(value: JSONObject) = RecordItem(
    id = value.getLong("id"),
    name = value.getString("name"),
    description = value.getString("description"),
    valueType = RecordValueType.valueOf(value.getString("valueType")),
    defaultValue = value.nullableDouble("defaultValue"),
    stepValue = value.getDouble("stepValue"),
    unit = value.getString("unit"),
    goalValue = value.nullableDouble("goalValue"),
    goalPeriod = value.nullableString("goalPeriod")?.let(GoalPeriod::valueOf),
    priority = value.getInt("priority"),
    archived = value.getBoolean("archived"),
    windows = value.getJSONArray("windows").mapObjects { window ->
        RecordWindow(
            id = window.getLong("id"),
            startMinute = window.getInt("startMinute"),
            endMinute = window.getInt("endMinute"),
        )
    },
    createdAt = value.getLong("createdAt"),
    updatedAt = value.getLong("updatedAt"),
)

private fun parseEntry(value: JSONObject) = RecordEntry(
    id = value.getLong("id"),
    itemId = value.getLong("itemId"),
    occurredAt = value.getLong("occurredAt"),
    localDate = value.getString("localDate"),
    localMinute = value.getInt("localMinute"),
    value = value.getDouble("value"),
    createdAt = value.getLong("createdAt"),
    itemName = value.getString("itemName"),
    itemUnit = value.getString("itemUnit"),
    itemValueType = RecordValueType.valueOf(value.getString("itemValueType")),
)

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { index -> transform(getJSONObject(index)) }

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.nullableDouble(key: String): Double? =
    if (isNull(key)) null else getDouble(key)

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else getString(key)

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
