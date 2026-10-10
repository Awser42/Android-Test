package com.awser.counter

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Small, entirely offline store. A committed tap survives process recreation. */
class CounterStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class Session(
        val id: String,
        val label: String,
        val count: Long,
        val timestamp: Long,
        val goal: Int,
    )

    val count: Long get() = synchronized(lock) { readLong(COUNT, 0L).coerceIn(0L, MAX_COUNT) }
    /** Zero means free counting; existing positive targets remain unchanged. */
    val goal: Int get() = synchronized(lock) { readInt(GOAL, DEFAULT_GOAL).coerceIn(NO_GOAL, MAX_GOAL) }
    val darkTheme: Boolean get() = synchronized(lock) { readBoolean(DARK_THEME, true) }
    val vibrationEnabled: Boolean get() = synchronized(lock) { readBoolean(VIBRATION, true) }
    val displayLightEnabled: Boolean get() = synchronized(lock) { readBoolean(DISPLAY_LIGHT_ENABLED, true) }
    val counterName: String get() = synchronized(lock) { cleanLabel(readString(COUNTER_NAME, DEFAULT_COUNTER_NAME)) }

    fun increment(): Long = synchronized(lock) {
        val next = (count + 1L).coerceAtMost(MAX_COUNT)
        commit(preferences.edit().putLong(COUNT, next))
        next
    }

    fun updateCount(value: Long) = synchronized(lock) {
        commit(preferences.edit().putLong(COUNT, value.coerceIn(0L, MAX_COUNT)))
    }

    fun reset() = updateCount(0L)

    fun setGoal(value: Int) = synchronized(lock) {
        commit(preferences.edit().putInt(GOAL, value.coerceIn(NO_GOAL, MAX_GOAL)))
    }

    fun setDarkTheme(enabled: Boolean) = synchronized(lock) {
        commit(preferences.edit().putBoolean(DARK_THEME, enabled))
    }

    fun setVibrationEnabled(enabled: Boolean) = synchronized(lock) {
        commit(preferences.edit().putBoolean(VIBRATION, enabled))
    }

    fun setDisplayLightEnabled(enabled: Boolean) = synchronized(lock) {
        commit(preferences.edit().putBoolean(DISPLAY_LIGHT_ENABLED, enabled))
    }

    fun setCounterName(value: String) = synchronized(lock) {
        commit(preferences.edit().putString(COUNTER_NAME, cleanLabel(value)))
    }

    /** Saving makes a snapshot; it deliberately leaves the active count intact. */
    fun saveSession(label: String): Session = synchronized(lock) {
        val session = Session(UUID.randomUUID().toString(), cleanLabel(label), count, System.currentTimeMillis(), goal)
        writeSessions((listOf(session) + sessions()).take(MAX_SESSIONS))
        session
    }

    fun sessions(): List<Session> = synchronized(lock) {
        val result = mutableListOf<Session>()
        val seenIds = mutableSetOf<String>()
        val json = try {
            JSONArray(readString(SESSIONS, "[]"))
        } catch (_: Exception) {
            return@synchronized emptyList()
        }
        for (index in 0 until json.length()) {
            val entry = json.optJSONObject(index) ?: continue
            val id = entry.optString("id", "")
            val label = entry.optString("label", "").trim()
            val savedCount = entry.optLong("count", -1L)
            val timestamp = entry.optLong("timestamp", -1L)
            val savedGoal = readSessionGoal(entry)
            if (id.isBlank() || id.length > 100 || label.isBlank() || savedCount !in 0L..MAX_COUNT ||
                timestamp < 0L || !seenIds.add(id)) continue
            result.add(Session(id, cleanLabel(label), savedCount, timestamp, savedGoal))
            if (result.size == MAX_SESSIONS) break
        }
        result
    }

    /** Restore the count, its goal and its name together in one disk commit. */
    fun restoreSession(id: String): Boolean = synchronized(lock) {
        val session = sessions().firstOrNull { it.id == id } ?: return@synchronized false
        commit(preferences.edit().putLong(COUNT, session.count).putInt(GOAL, session.goal).putString(COUNTER_NAME, session.label))
        true
    }

    fun deleteSession(id: String): Boolean = synchronized(lock) {
        val existing = sessions()
        val remaining = existing.filterNot { it.id == id }
        if (remaining.size == existing.size) return@synchronized false
        writeSessions(remaining)
        true
    }

    private fun writeSessions(sessions: List<Session>) {
        val json = JSONArray()
        sessions.forEach { session ->
            json.put(JSONObject().put("id", session.id).put("label", session.label).put("count", session.count)
                .put("timestamp", session.timestamp).put("goal", session.goal))
        }
        commit(preferences.edit().putString(SESSIONS, json.toString()))
    }

    private fun commit(editor: SharedPreferences.Editor) {
        check(editor.commit()) { "Could not save the counter. Please free some device storage." }
    }

    private fun readLong(key: String, fallback: Long): Long = try { preferences.getLong(key, fallback) } catch (_: ClassCastException) { fallback }
    private fun readInt(key: String, fallback: Int): Int = try { preferences.getInt(key, fallback) } catch (_: ClassCastException) { fallback }
    private fun readBoolean(key: String, fallback: Boolean): Boolean = try { preferences.getBoolean(key, fallback) } catch (_: ClassCastException) { fallback }
    private fun readString(key: String, fallback: String): String = try { preferences.getString(key, fallback) ?: fallback } catch (_: ClassCastException) { fallback }
    private fun cleanLabel(value: String) = value.trim().take(MAX_LABEL_LENGTH).ifBlank { DEFAULT_COUNTER_NAME }
    private fun readSessionGoal(entry: JSONObject): Int {
        val value = (entry.opt("goal") as? Number)?.toDouble() ?: return NO_GOAL
        return if (value in NO_GOAL.toDouble()..MAX_GOAL.toDouble() && value % 1.0 == 0.0) value.toInt() else NO_GOAL
    }

    companion object {
        const val PREFERENCES_NAME = "a_counter"
        const val MAX_COUNT = 999_999_999L
        const val MAX_GOAL = 999_999_999
        const val NO_GOAL = 0
        const val DEFAULT_GOAL = NO_GOAL
        const val DEFAULT_COUNTER_NAME = "Counter"
        const val MAX_SESSIONS = 100
        const val MAX_LABEL_LENGTH = 160
        private val lock = Any()
        private const val COUNT = "count"
        private const val GOAL = "goal"
        private const val DARK_THEME = "dark_theme"
        private const val VIBRATION = "vibration"
        private const val DISPLAY_LIGHT_ENABLED = "display_light_enabled"
        private const val COUNTER_NAME = "counter_name"
        private const val SESSIONS = "sessions"
    }
}
