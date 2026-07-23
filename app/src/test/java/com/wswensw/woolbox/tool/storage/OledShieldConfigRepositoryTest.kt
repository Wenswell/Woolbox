package com.wswensw.woolbox.tool.storage

import android.content.SharedPreferences
import com.wswensw.woolbox.tool.model.OledShieldConfig
import com.wswensw.woolbox.tool.model.OledShieldProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OledShieldConfigRepositoryTest {
    @Test
    fun recordUseCreatesTypedProfileAndUpsertsTimestamp() {
        val preferences = MemorySharedPreferences()
        val timestamps = ArrayDeque(listOf(100L, 200L))
        val repository = OledShieldConfigRepository(preferences, timestamps::removeFirst)

        repository.recordUse("com.example.reader", config(opacity = 40, width = 60))
        repository.recordUse(
            "com.example.reader",
            config(opacity = 75, width = 80, height = 35, panelX = 0.25f, panelY = 0.7f),
        )

        assertEquals(setOf("com.example.reader"), preferences.getStringSet("profiles", emptySet()))
        assertEquals(75, preferences.getInt("profile.com.example.reader.opacity", 0))
        assertEquals(80, preferences.getInt("profile.com.example.reader.width", 0))
        assertEquals(35, preferences.getInt("profile.com.example.reader.height", 0))
        assertEquals(0.25f, preferences.getFloat("profile.com.example.reader.panel_x", 0f))
        assertEquals(0.7f, preferences.getFloat("profile.com.example.reader.panel_y", 0f))
        assertEquals(200L, preferences.getLong("profile.com.example.reader.recorded_at", 0L))
        assertEquals(
            listOf(
                OledShieldProfile(
                    packageName = "com.example.reader",
                    config = config(
                        opacity = 75,
                        width = 80,
                        height = 35,
                        panelX = 0.25f,
                        panelY = 0.7f,
                    ),
                    recordedAt = 200L,
                ),
            ),
            repository.loadProfiles(),
        )
    }

    @Test
    fun saveUpdatesConfigWithoutChangingRecordedAtAndRequiresExistingProfile() {
        val preferences = MemorySharedPreferences()
        val repository = OledShieldConfigRepository(preferences) { 123L }
        repository.recordUse("com.example.reader", config(opacity = 70))

        repository.saveForPackage("com.example.reader", config(opacity = 45, height = 30))

        assertEquals(45, repository.loadForPackage("com.example.reader").opacityPercent)
        assertEquals(30, repository.loadForPackage("com.example.reader").heightPercent)
        assertEquals(123L, repository.loadProfiles().single().recordedAt)
        assertThrows(IllegalStateException::class.java) {
            repository.saveForPackage("com.example.missing", config())
        }
    }

    @Test
    fun profilesAreSortedByRecordedAtThenPackageNameDescending() {
        val timestamps = ArrayDeque(listOf(100L, 200L, 200L))
        val repository = OledShieldConfigRepository(MemorySharedPreferences(), timestamps::removeFirst)

        repository.recordUse("com.example.first", config())
        repository.recordUse("com.example.alpha", config())
        repository.recordUse("com.example.zulu", config())

        assertEquals(
            listOf("com.example.zulu", "com.example.alpha", "com.example.first"),
            repository.loadProfiles().map { it.packageName },
        )
    }

    @Test
    fun deleteRemovesIndexAndEveryProfileField() {
        val preferences = MemorySharedPreferences()
        val repository = OledShieldConfigRepository(preferences) { 100L }
        repository.recordUse("com.example.reader", config())

        repository.deleteProfile("com.example.reader")

        assertTrue(repository.loadProfiles().isEmpty())
        assertFalse(preferences.contains("profiles"))
        assertTrue(preferences.all.keys.none { it.startsWith("profile.com.example.reader.") })
    }

    @Test
    fun profilesFlowEmitsInitialCreateAndDeleteStates() = runBlocking {
        val repository = OledShieldConfigRepository(MemorySharedPreferences()) { 100L }
        val subscriptionReady = CompletableDeferred<Unit>()
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            repository.profiles
                .onEach { subscriptionReady.complete(Unit) }
                .take(3)
                .toList()
        }
        withTimeout(1_000L) { subscriptionReady.await() }

        repository.recordUse("com.example.reader", config())
        repository.deleteProfile("com.example.reader")

        val values = withTimeout(1_000L) { emissions.await() }
        assertEquals(listOf(0, 1, 0), values.map(List<*>::size))
    }

    private fun config(
        opacity: Int = 100,
        width: Int = 100,
        height: Int = 15,
        panelX: Float = 0.08f,
        panelY: Float = 0.45f,
    ) = OledShieldConfig(
        opacityPercent = opacity,
        widthPercent = width,
        heightPercent = height,
        panelXPercent = panelX,
        panelYPercent = panelY,
    )
}

private class MemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any>()
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        (values[key] as? Set<String>)?.toSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners -= listener
    }

    private inner class Editor : SharedPreferences.Editor {
        private val updates = linkedMapOf<String, Any?>()
        private val removals = linkedSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = update(key, value)

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor =
            update(key, values?.toSet())

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = update(key, value)

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = update(key, value)

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = update(key, value)

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = update(key, value)

        override fun remove(key: String): SharedPreferences.Editor = apply {
            updates -= key
            removals += key
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clear = true
            updates.clear()
            removals.clear()
        }

        override fun commit(): Boolean {
            val changedKeys = linkedSetOf<String>()
            if (clear) {
                changedKeys += values.keys
                values.clear()
            }
            removals.forEach { key ->
                if (values.remove(key) != null) changedKeys += key
            }
            updates.forEach { (key, value) ->
                if (value == null) {
                    if (values.remove(key) != null) changedKeys += key
                } else if (values.put(key, value) != value) {
                    changedKeys += key
                }
            }
            listeners.toList().forEach { listener ->
                changedKeys.forEach { key -> listener.onSharedPreferenceChanged(this@MemorySharedPreferences, key) }
            }
            return true
        }

        override fun apply() {
            commit()
        }

        private fun update(key: String, value: Any?): SharedPreferences.Editor = apply {
            removals -= key
            updates[key] = value
        }
    }
}
