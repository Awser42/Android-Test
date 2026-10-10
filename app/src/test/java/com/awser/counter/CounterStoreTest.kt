package com.awser.counter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CounterStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences(CounterStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    private lateinit var store: CounterStore

    @Before fun startFresh() {
        prefs.edit().clear().commit()
        store = CounterStore(context)
    }

    @Test fun newInstallStartsWithNoTargetAndUsefulOfflineDefaults() {
        assertEquals(0L, store.count)
        assertEquals(CounterStore.NO_GOAL, store.goal)
        assertFalse(prefs.contains("goal"))
        assertTrue(store.darkTheme)
        assertTrue(store.vibrationEnabled)
        assertTrue(store.displayLightEnabled)
        assertEquals("Counter", store.counterName)
        assertTrue(store.sessions().isEmpty())
    }

    @Test fun tapsAndSettingsSurviveFreshStoreInstances() {
        repeat(37) { store.increment() }
        store.setGoal(50)
        store.setDarkTheme(false)
        store.setVibrationEnabled(false)
        store.setCounterName("Workout")
        val restarted = CounterStore(context)
        assertEquals(37L, restarted.count)
        assertEquals(50, restarted.goal)
        assertFalse(restarted.darkTheme)
        assertFalse(restarted.vibrationEnabled)
        assertEquals("Workout", restarted.counterName)
        assertEquals(37L, prefs.getLong("count", -1L))
    }

    @Test fun clearingTheTargetPreservesCountSettingsAndSavedSessions() {
        store.updateCount(58L)
        store.setGoal(250)
        store.setCounterName("Steps")
        store.setDarkTheme(false)
        store.setVibrationEnabled(false)
        store.setDisplayLightEnabled(false)
        val saved = store.saveSession(store.counterName)

        store.setGoal(CounterStore.NO_GOAL)
        val restarted = CounterStore(context)
        assertEquals(CounterStore.NO_GOAL, restarted.goal)
        assertEquals(CounterStore.NO_GOAL, prefs.getInt("goal", -1))
        assertEquals(58L, restarted.count)
        assertEquals("Steps", restarted.counterName)
        assertFalse(restarted.darkTheme)
        assertFalse(restarted.vibrationEnabled)
        assertFalse(restarted.displayLightEnabled)
        assertEquals(listOf(saved), restarted.sessions())
        assertEquals(250, saved.goal)
        assertEquals(59L, restarted.increment())
        assertEquals(CounterStore.NO_GOAL, CounterStore(context).goal)
        assertEquals(59L, prefs.getLong("count", -1L))
    }

    @Test fun existingExplicitTargetsAndSavedTargetsSurviveTheOptionalTargetUpgrade() {
        val previousSession = JSONObject().put("id", "existing-session").put("label", "Steps")
            .put("count", 58L).put("timestamp", 123L).put("goal", 250)
        prefs.edit().putLong("count", 58L).putInt("goal", 250).putString("counter_name", "Steps")
            .putBoolean("dark_theme", false).putBoolean("vibration", false)
            .putBoolean("display_light_enabled", false)
            .putString("sessions", JSONArray().put(previousSession).toString()).commit()

        val upgraded = CounterStore(context)
        assertEquals(250, upgraded.goal)
        assertEquals(58L, upgraded.count)
        assertEquals("Steps", upgraded.counterName)
        assertFalse(upgraded.darkTheme)
        assertFalse(upgraded.vibrationEnabled)
        assertFalse(upgraded.displayLightEnabled)
        assertEquals(250, upgraded.sessions().single().goal)
        assertEquals(58L, upgraded.sessions().single().count)
        assertEquals(250, prefs.getInt("goal", -1))
    }

    @Test fun noTargetSnapshotsRestoreAndPersistTheCountNameAndAbsentTargetTogether() {
        store.updateCount(102L)
        store.setCounterName("Laps")
        val saved = store.saveSession(store.counterName)
        assertEquals(CounterStore.NO_GOAL, saved.goal)
        assertEquals(saved, CounterStore(context).sessions().single())
        assertEquals(CounterStore.NO_GOAL, JSONArray(prefs.getString("sessions", "[]"))
            .getJSONObject(0).getInt("goal"))

        store.updateCount(7L)
        store.setGoal(25)
        store.setCounterName("Workout")
        assertTrue(store.restoreSession(saved.id))
        val restarted = CounterStore(context)
        assertEquals(102L, restarted.count)
        assertEquals("Laps", restarted.counterName)
        assertEquals(CounterStore.NO_GOAL, restarted.goal)
        assertEquals(CounterStore.NO_GOAL, prefs.getInt("goal", -1))
        assertEquals(saved, restarted.sessions().single())
    }

    @Test fun existingPreferencesWithoutDisplayLightSettingDefaultToEnabled() {
        prefs.edit().putLong("count", 58L).putInt("goal", 250).putString("counter_name", "Steps")
            .putBoolean("dark_theme", false).putBoolean("vibration", false).commit()

        val upgraded = CounterStore(context)
        assertTrue(upgraded.displayLightEnabled)
        assertFalse(prefs.contains("display_light_enabled"))
        assertEquals(58L, upgraded.count)
        assertEquals(250, upgraded.goal)
        assertEquals("Steps", upgraded.counterName)
        assertFalse(upgraded.darkTheme)
        assertFalse(upgraded.vibrationEnabled)
    }

    @Test fun displayLightOffAndOnSurviveFreshStoreInstances() {
        store.setDisplayLightEnabled(false)
        val restarted = CounterStore(context)
        assertFalse(restarted.displayLightEnabled)
        assertFalse(prefs.getBoolean("display_light_enabled", true))

        restarted.setDisplayLightEnabled(true)
        assertTrue(CounterStore(context).displayLightEnabled)
        assertTrue(store.displayLightEnabled)
        assertTrue(prefs.getBoolean("display_light_enabled", false))
    }

    @Test fun changingDisplayLightLeavesCounterAndSavedSessionsUntouched() {
        store.updateCount(102L)
        store.setGoal(250)
        store.setCounterName("Workout")
        store.setDarkTheme(false)
        store.setVibrationEnabled(false)
        val saved = store.saveSession(store.counterName)

        listOf(false, true).forEach { enabled ->
            store.setDisplayLightEnabled(enabled)
            val restarted = CounterStore(context)
            assertEquals(enabled, restarted.displayLightEnabled)
            assertEquals(102L, restarted.count)
            assertEquals(250, restarted.goal)
            assertEquals("Workout", restarted.counterName)
            assertFalse(restarted.darkTheme)
            assertFalse(restarted.vibrationEnabled)
            assertEquals(listOf(saved), restarted.sessions())
        }
    }

    @Test fun resetClearsOnlyActiveCount() {
        store.updateCount(123L)
        store.setGoal(100)
        store.setCounterName("Laps")
        val saved = store.saveSession(store.counterName)
        store.reset()
        assertEquals(0L, CounterStore(context).count)
        assertEquals(100, store.goal)
        assertEquals("Laps", store.counterName)
        assertEquals(saved, store.sessions().single())
    }

    @Test fun presetAndCustomGoalsDoNotSilentlyResetTheCounter() {
        store.updateCount(58L)
        listOf(25, 50, 100, 250, CounterStore.NO_GOAL).forEach { goal ->
            store.setGoal(goal)
            assertEquals(goal, CounterStore(context).goal)
            assertEquals(58L, store.count)
        }
    }

    @Test fun sessionSnapshotsRestoreAndDeleteWithoutLosingActiveState() {
        store.updateCount(102L)
        store.setGoal(100)
        val first = store.saveSession("  Steps  ")
        assertEquals(102L, store.count)
        assertEquals("Steps", first.label)
        assertTrue(first.timestamp > 0L)
        store.updateCount(7L)
        store.setGoal(25)
        store.setCounterName("Workout")
        val second = store.saveSession("Workout")
        assertNotEquals(first.id, second.id)
        assertEquals(listOf(second, first), CounterStore(context).sessions())
        assertTrue(store.restoreSession(first.id))
        val restarted = CounterStore(context)
        assertEquals(102L, restarted.count)
        assertEquals(100, restarted.goal)
        assertEquals("Steps", restarted.counterName)
        assertFalse(store.restoreSession("missing"))
        assertEquals(102L, store.count)
        assertTrue(store.deleteSession(first.id))
        assertFalse(store.deleteSession(first.id))
        assertEquals(listOf(second), CounterStore(context).sessions())
        assertEquals(102L, store.count)
    }

    @Test fun boundsPreventNegativeCountsOrOverflow() {
        store.updateCount(Long.MIN_VALUE)
        assertEquals(0L, store.count)
        store.updateCount(Long.MAX_VALUE)
        assertEquals(CounterStore.MAX_COUNT, store.count)
        assertEquals(CounterStore.MAX_COUNT, store.increment())
        store.setGoal(Int.MIN_VALUE)
        assertEquals(CounterStore.NO_GOAL, store.goal)
        store.setGoal(1)
        assertEquals(1, store.goal)
        store.setGoal(Int.MAX_VALUE)
        assertEquals(CounterStore.MAX_GOAL, store.goal)
        store.setCounterName("   ")
        assertEquals(CounterStore.DEFAULT_COUNTER_NAME, store.counterName)
        store.setCounterName("a".repeat(200))
        assertEquals(CounterStore.MAX_LABEL_LENGTH, store.counterName.length)
    }

    @Test fun malformedAndWrongTypedStorageRecoversWithDefaults() {
        prefs.edit().putString("count", "broken").putLong("goal", 0L).putString("dark_theme", "no")
            .putString("vibration", "no").putString("display_light_enabled", "no")
            .putInt("counter_name", 9).putString("sessions", "not json").commit()
        assertEquals(0L, store.count)
        assertEquals(CounterStore.NO_GOAL, store.goal)
        assertTrue(store.darkTheme)
        assertTrue(store.vibrationEnabled)
        assertTrue(store.displayLightEnabled)
        assertEquals("Counter", store.counterName)
        assertTrue(store.sessions().isEmpty())
        assertEquals(1L, store.increment())
        assertEquals(1L, CounterStore(context).count)
        assertEquals(1, store.saveSession("Recovered").count.toInt())
        assertEquals(1, store.sessions().size)
    }

    @Test fun invalidSavedRowsAreSkippedAndDuplicateIdsAreRemoved() {
        fun row(id: String, count: Long) = JSONObject().put("id", id).put("label", "Counter").put("count", count)
            .put("timestamp", 123L).put("goal", 100)
        val rows = JSONArray().put(row("good", 3L)).put(row("negative", -1L)).put(row("good", 4L))
            .put("bad row").put(JSONObject()).put(row("overflow", Long.MAX_VALUE))
        prefs.edit().putString("sessions", rows.toString()).commit()
        assertEquals(1, store.sessions().size)
        assertEquals(3L, store.sessions().single().count)
    }

    @Test fun missingOrMalformedSavedTargetsRecoverAsFreeCountingWithoutLosingSessions() {
        fun row(id: String, goal: Any?) = JSONObject().put("id", id).put("label", "Counter")
            .put("count", 3L).put("timestamp", 123L).also { if (goal != null) it.put("goal", goal) }
        val rows = JSONArray().put(row("missing", null)).put(row("text", "broken"))
            .put(row("negative", -1)).put(row("overflow", Long.MAX_VALUE))
            .put(row("fraction", 3.5)).put(row("none", CounterStore.NO_GOAL)).put(row("valid", 50))
        prefs.edit().putString("sessions", rows.toString()).commit()

        val sessions = store.sessions()
        assertEquals(7, sessions.size)
        assertTrue(sessions.dropLast(1).all { it.goal == CounterStore.NO_GOAL })
        assertEquals(50, sessions.last().goal)
        store.setGoal(250)
        assertTrue(store.restoreSession("missing"))
        assertEquals(3L, CounterStore(context).count)
        assertEquals(CounterStore.NO_GOAL, CounterStore(context).goal)
    }

    @Test fun sessionHistoryHasABoundedSizeAndKeepsNewestSnapshots() {
        repeat(CounterStore.MAX_SESSIONS + 5) { store.saveSession("Session $it") }
        val sessions = store.sessions()
        assertEquals(CounterStore.MAX_SESSIONS, sessions.size)
        assertEquals("Session 104", sessions.first().label)
        assertEquals("Session 5", sessions.last().label)
    }

    @Test fun differentStoreInstancesCannotLoseConcurrentTaps() {
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val done = CountDownLatch(4)
        repeat(4) {
            executor.submit {
                try {
                    val local = CounterStore(context)
                    start.await()
                    repeat(25) { local.increment() }
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        try {
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals(100L, store.count)
            assertEquals(100L, CounterStore(context).count)
        } finally {
            executor.shutdownNow()
        }
    }
}
