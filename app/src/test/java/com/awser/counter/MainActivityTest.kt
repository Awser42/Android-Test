package com.awser.counter

import android.app.AlertDialog
import android.content.Context
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
        chooseItem(1)
        assertEquals(50, store.goal)
        assertEquals(4L, store.count)
        click("goal")
        chooseItem(3)
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

    @Test fun saveRestoreAndConfirmedDeleteWorkThroughHistoryControls() {
        repeat(7) { click("counter") }
        click("save")
        val save = ShadowAlertDialog.getLatestAlertDialog()
        save.window!!.decorView.findViewWithTag<EditText>("session_name").setText("Evening count")
        clickDialogButton(save, AlertDialog.BUTTON_POSITIVE)
        val session = store.sessions().single()
        assertEquals("Evening count", session.label)
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
}
