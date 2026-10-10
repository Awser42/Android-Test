package com.awser.counter

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class MainActivityTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    private val store get() = CounterStore(RuntimeEnvironment.getApplication())

    @Before fun launch() {
        RuntimeEnvironment.getApplication().getSharedPreferences(CounterStore.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store.setVibrationEnabled(false)
        BannerAds.factory = { activity, privacyChanged -> TestBannerAdController(activity, privacyChanged) }
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
    }

    @After fun close() {
        controller.pause().stop().destroy()
        BannerAds.factory = null
    }

    @Test fun actualCounterButtonUpdatesVisibleCountAndPersistsEveryTap() {
        repeat(45) { click("counter") }
        assertEquals(45L, store.count)
        val label = activity.window.decorView.findViewWithTag<TextView>("count_label")
        assertNotNull(label)
        assertTrue(label.text.toString().contains("45"))
        val counter = activity.window.decorView.findViewWithTag<View>("counter")
        assertTrue(counter.contentDescription.toString().contains("45"))
    }

    @Test fun freshInstallCountsFreelyWithoutAnImplicitTargetOrProgressBar() {
        assertEquals(0, store.goal)
        assertEquals(View.GONE, tagged("progress").visibility)
        repeat(27) { click("counter") }
        assertEquals(27L, store.count)
        assertEquals(0, store.goal)
        assertEquals(View.GONE, tagged("progress").visibility)
        assertTrue(countText().contains("27"))
        assertFalse("Free counting must not show an unwanted target", countText().contains("Target"))
    }

    @Test fun resetRequiresConfirmationAndCancelPreservesTheCount() {
        repeat(8) { click("counter") }
        click("reset")
        assertEquals(8L, store.count)
        var dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)
        clickDialogButton(dialog, AlertDialog.BUTTON_NEGATIVE)
        assertEquals(8L, store.count)
        click("reset")
        dialog = ShadowAlertDialog.getLatestAlertDialog()
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE)
        assertEquals(0L, store.count)
    }

    @Test fun reopeningTheActivityRestoresCountAndGoal() {
        store.setGoal(50)
        repeat(12) { click("counter") }
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        assertEquals(12L, store.count)
        assertEquals(50, store.goal)
        val label = activity.window.decorView.findViewWithTag<TextView>("count_label")
        assertTrue(label.text.toString().contains("12"))
        assertTrue(label.text.toString().contains("50"))
        assertEquals(View.VISIBLE, tagged("progress").visibility)
        click("counter")
        assertEquals(13L, store.count)
    }

    @Test fun themeAndVibrationTogglesPreserveActiveCounter() {
        repeat(3) { click("counter") }
        assertTrue(store.darkTheme)
        assertFalse(store.vibrationEnabled)
        click("theme")
        assertFalse(store.darkTheme)
        assertEquals(3L, store.count)
        click("vibration")
        assertTrue(store.vibrationEnabled)
        assertEquals(3L, store.count)
        click("theme")
        assertTrue(store.darkTheme)
        click("counter")
        assertEquals(4L, store.count)
    }

    @Test fun headerThemeAndLcdLightStayIndependentAndPersistWithoutChangingTheCounter() {
        store.setGoal(250)
        store.setCounterName("Evening count")
        repeat(9) { click("counter") }
        assertTrue(store.darkTheme)
        assertTrue(store.displayLightEnabled)
        click("light")
        assertFalse(store.displayLightEnabled)
        click("theme")
        assertFalse(store.darkTheme)
        assertFalse("Changing page theme must keep the LCD preference", store.displayLightEnabled)
        assertEquals(9L, store.count)
        assertEquals(250, store.goal)
        assertEquals("Evening count", store.counterName)
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        assertFalse(store.darkTheme)
        assertFalse(store.displayLightEnabled)
        assertEquals(9L, store.count)
        assertEquals(250, store.goal)
        assertEquals("Evening count", store.counterName)
        click("light")
        assertTrue(store.displayLightEnabled)
        assertFalse("Changing the LCD preference must keep the page theme", store.darkTheme)
        click("counter")
        assertEquals(10L, store.count)
    }

    @Test @Config(sdk = [23])
    fun minimumSupportedAndroidCanTapAnimatedCounterAndReset() {
        click("counter")
        click("counter")
        assertEquals(2L, store.count)
        click("reset")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE)
        assertEquals(0L, store.count)
        click("theme")
        assertFalse(store.darkTheme)
    }

    @Test fun targetDialogSupportsPresetsAndRejectsInvalidCustomGoals() {
        repeat(4) { click("counter") }
        click("goal")
        chooseItem(2)
        assertEquals(50, store.goal)
        assertEquals(4L, store.count)
        click("goal")
        chooseItem(4)
        val custom = ShadowAlertDialog.getLatestAlertDialog()
        val input = custom.window!!.decorView.findViewWithTag<EditText>("custom_goal")
        input.setText("0")
        clickDialogButton(custom, AlertDialog.BUTTON_POSITIVE)
        assertTrue("Invalid target should keep the dialog open", custom.isShowing)
        assertNotNull(input.error)
        assertEquals(50, store.goal)
        input.setText("250")
        clickDialogButton(custom, AlertDialog.BUTTON_POSITIVE)
        assertFalse(custom.isShowing)
        assertEquals(250, store.goal)
        assertEquals(4L, store.count)
    }

    @Test fun choosingNoTargetRemovesProgressWithoutDiscardingTheCount() {
        repeat(9) { click("counter") }
        click("goal")
        chooseItem(1)
        assertEquals(25, store.goal)
        assertEquals(View.VISIBLE, tagged("progress").visibility)
        click("goal")
        chooseItem(0)
        assertEquals(0, store.goal)
        assertEquals(9L, store.count)
        assertEquals(View.GONE, tagged("progress").visibility)
        assertFalse(countText().contains("Target"))
        click("counter")
        assertEquals(10L, store.count)
    }

    @Test fun closingAndReopeningRestoresFreeCountAllPreferencesAndSavedSessions() {
        repeat(11) { click("counter") }
        click("rename")
        val rename = ShadowAlertDialog.getLatestAlertDialog()
        rename.window!!.decorView.findViewWithTag<EditText>("counter_name").setText("Evening")
        clickDialogButton(rename, AlertDialog.BUTTON_POSITIVE)
        click("goal")
        chooseItem(0)
        click("light")
        click("theme")
        click("vibration")
        click("save")
        val save = ShadowAlertDialog.getLatestAlertDialog()
        save.window!!.decorView.findViewWithTag<EditText>("session_name").setText("Saved Evening")
        clickDialogButton(save, AlertDialog.BUTTON_POSITIVE)
        val session = store.sessions().single()
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        assertEquals(11L, store.count)
        assertEquals(0, store.goal)
        assertEquals("Evening", store.counterName)
        assertFalse(store.darkTheme)
        assertFalse(store.displayLightEnabled)
        assertTrue(store.vibrationEnabled)
        assertEquals(session, store.sessions().single())
        assertEquals(0, session.goal)
        assertTrue(countText().contains("11"))
        assertFalse(countText().contains("Target"))
        assertEquals(View.GONE, tagged("progress").visibility)
        assertEquals("Evening", (tagged("counter_name_label") as TextView).text.toString())
        click("history")
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView.findViewWithTag<View>("history_row_${session.id}"))
    }

    @Test fun rotationRetainsTheVisibleFreeCount() {
        repeat(8) { click("counter") }
        val rotated = Configuration(activity.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 800
            screenHeightDp = 360
        }
        controller.configurationChange(rotated)
        activity = controller.get()
        assertEquals(8L, store.count)
        assertEquals(0, store.goal)
        assertTrue(countText().contains("8"))
        assertEquals(View.GONE, tagged("progress").visibility)
        click("counter")
        assertEquals(9L, store.count)
    }

    @Test fun returningToForegroundRefreshesStateChangedByAnotherStore() {
        repeat(2) { click("counter") }
        controller.pause()
        val updated = CounterStore(activity)
        updated.updateCount(17L)
        updated.setGoal(0)
        updated.setCounterName("Updated elsewhere")
        updated.setDarkTheme(false)
        updated.setDisplayLightEnabled(false)
        updated.setVibrationEnabled(true)
        val session = updated.saveSession("Refreshed history")
        controller.resume()
        assertTrue(countText().contains("17"))
        assertFalse(countText().contains("Target"))
        assertEquals("Updated elsewhere", (tagged("counter_name_label") as TextView).text.toString())
        assertEquals(View.GONE, tagged("progress").visibility)
        assertTrue(tagged("counter").contentDescription.toString().contains("17"))
        assertTrue(tagged("theme").contentDescription.toString().lowercase().contains("dark"))
        assertTrue(tagged("light").contentDescription.toString().lowercase().contains("on"))
        assertTrue(tagged("vibration").contentDescription.toString().lowercase().contains("on"))
        click("history")
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView.findViewWithTag<View>("history_row_${session.id}"))
    }

    @Test fun saveRestoreAndConfirmedDeleteWorkThroughHistoryControls() {
        repeat(7) { click("counter") }
        click("save")
        val save = ShadowAlertDialog.getLatestAlertDialog()
        save.window!!.decorView.findViewWithTag<EditText>("session_name").setText("Evening count")
        clickDialogButton(save, AlertDialog.BUTTON_POSITIVE)
        val session = store.sessions().single()
        assertEquals("Evening count", session.label)
        assertEquals(0, session.goal)
        assertEquals(7L, store.count)
        repeat(2) { click("counter") }
        assertEquals(9L, store.count)
        click("history")
        var history = ShadowAlertDialog.getLatestAlertDialog()
        history.window!!.decorView.findViewWithTag<View>("history_row_${session.id}").performClick()
        var action = ShadowAlertDialog.getLatestAlertDialog()
        clickDialogButton(action, AlertDialog.BUTTON_POSITIVE)
        assertEquals(7L, store.count)
        assertEquals("Evening count", store.counterName)
        assertEquals(0, store.goal)
        assertEquals(View.GONE, tagged("progress").visibility)
        click("history")
        history = ShadowAlertDialog.getLatestAlertDialog()
        history.window!!.decorView.findViewWithTag<View>("history_row_${session.id}").performClick()
        action = ShadowAlertDialog.getLatestAlertDialog()
        clickDialogButton(action, AlertDialog.BUTTON_NEUTRAL)
        val confirmDelete = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(1, store.sessions().size)
        clickDialogButton(confirmDelete, AlertDialog.BUTTON_POSITIVE)
        assertTrue(store.sessions().isEmpty())
        assertEquals(7L, store.count)
    }

    @Test fun renamingTheCounterUpdatesTheVisibleNameWithoutClearingTheCount() {
        click("counter")
        click("rename")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val input = dialog.window!!.decorView.findViewWithTag<EditText>("counter_name")
        assertNotNull(input)
        input.setText("Workout")
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE)
        assertFalse(dialog.isShowing)
        assertEquals("Workout", store.counterName)
        assertEquals(1L, store.count)
        val name = activity.window.decorView.findViewWithTag<TextView>("counter_name_label")
        assertNotNull(name)
        assertTrue(name.text.toString().contains("Workout"))
    }

    private fun chooseItem(index: Int) {
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val list = dialog.listView
        assertNotNull(list)
        list.performItemClick(null, index, list.adapter.getItemId(index))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun clickDialogButton(dialog: AlertDialog, which: Int) {
        dialog.getButton(which).performClick()
        // Framework AlertDialog posts its builder listeners through ButtonHandler.
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun click(tag: String) {
        val view = activity.window.decorView.findViewWithTag<View>(tag)
        assertNotNull("A real accessible control exists for $tag", view)
        assertTrue("$tag is clickable", view.performClick())
    }

    private fun tagged(tag: String): View = activity.window.decorView.findViewWithTag<View>(tag)
    private fun countText(): String = (tagged("count_label") as TextView).text.toString()
}
