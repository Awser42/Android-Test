package com.awser.counter

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.Button
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
    private val bannerControllers = mutableListOf<TestBannerAdController>()
    private val store get() = CounterStore(RuntimeEnvironment.getApplication())

    @Before fun launch() {
        RuntimeEnvironment.getApplication().getSharedPreferences(CounterStore.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store.setVibrationEnabled(false)
        BannerAds.factory = { activity, privacyChanged ->
            TestBannerAdController(activity, privacyChanged).also { bannerControllers.add(it) }
        }
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
    }

    @After fun close() {
        controller.pause().stop().destroy()
        BannerAds.factory = null
        RuntimeEnvironment.setFontScale(1f)
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

    @Test fun colorChooserNamesItsSwatchesAndCommitsTheChosenColor() {
        repeat(6) { click("counter") }
        click("theme_colors")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        CounterThemes.all.forEach { theme ->
            val swatch = colorSwatch(dialog, theme.id)
            assertTrue("${theme.id} has a visible name", swatch.text.toString().contains(theme.name))
            assertEquals("Only the current color is selected", theme.id == CounterThemes.DEFAULT_ID, swatch.isSelected)
        }
        assertTrue(colorSwatch(dialog, CounterThemes.DEFAULT_ID).contentDescription.toString().contains("selected", ignoreCase = true))
        assertTrue(colorSwatch(dialog, "ocean").performClick())
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("Selecting a color closes its chooser", dialog.isShowing)
        assertEquals("ocean", store.colorThemeId)
        assertEquals(6L, store.count)
        assertEquals(0, store.goal)
        assertEquals(View.GONE, tagged("progress").visibility)

        chooseColor("rose")
        assertEquals("rose", store.colorThemeId)
        assertEquals(6L, store.count)
        click("theme_colors")
        val reopened = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(colorSwatch(reopened, "rose").isSelected)
        assertTrue(colorSwatch(reopened, "rose").contentDescription.toString().contains("selected", ignoreCase = true))
        assertFalse(colorSwatch(reopened, "ocean").isSelected)
        reopened.dismiss()
    }

    @Test fun selectedColorAndActiveCounterSurviveActivityRecreation() {
        store.setCounterName("Evening zikr")
        store.setGoal(250)
        repeat(9) { click("counter") }
        chooseColor("ocean")
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()

        assertEquals("ocean", store.colorThemeId)
        assertEquals(9L, store.count)
        assertEquals(250, store.goal)
        assertEquals("Evening zikr", (tagged("counter_name_label") as TextView).text.toString())
        assertTrue(countText().contains("9"))
        assertEquals(View.VISIBLE, tagged("progress").visibility)
        click("theme_colors")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(colorSwatch(dialog, "ocean").isSelected)
        assertFalse(colorSwatch(dialog, CounterThemes.DEFAULT_ID).isSelected)
        dialog.dismiss()
        click("counter")
        assertEquals(10L, store.count)
    }

    @Test fun choosingColorsKeepsPageThemeLcdLightAndFreeCountingIndependent() {
        store.setCounterName("Morning zikr")
        repeat(4) { click("counter") }
        click("theme")
        click("light")
        assertFalse(store.darkTheme)
        assertFalse(store.displayLightEnabled)

        chooseColor("teal")
        assertFalse(store.darkTheme)
        assertFalse(store.displayLightEnabled)
        assertFalse(store.vibrationEnabled)
        assertEquals(4L, store.count)
        assertEquals(0, store.goal)
        assertEquals("Morning zikr", store.counterName)
        assertEquals(View.GONE, tagged("progress").visibility)

        click("theme")
        click("light")
        assertTrue(store.darkTheme)
        assertTrue(store.displayLightEnabled)
        assertEquals("teal", store.colorThemeId)
        chooseColor("amber")
        assertTrue(store.darkTheme)
        assertTrue(store.displayLightEnabled)
        assertEquals(4L, store.count)
        assertEquals(0, store.goal)
        assertEquals(View.GONE, tagged("progress").visibility)
        click("counter")
        assertEquals(5L, store.count)
    }

    @Test fun returningToForegroundReflectsAColorChangedByAnotherStore() {
        chooseColor("ocean")
        repeat(3) { click("counter") }
        controller.pause()
        val updated = CounterStore(activity)
        updated.setColorThemeId("violet")
        controller.resume()

        assertEquals("violet", store.colorThemeId)
        assertEquals(3L, store.count)
        assertEquals(0, store.goal)
        assertTrue(countText().contains("3"))
        click("theme_colors")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(colorSwatch(dialog, "violet").isSelected)
        assertTrue(colorSwatch(dialog, "violet").contentDescription.toString().contains("selected", ignoreCase = true))
        assertFalse(colorSwatch(dialog, "ocean").isSelected)
        dialog.dismiss()
    }

    @Test fun colorRerendersReattachTheExistingBannerWithoutCreatingAnotherController() {
        val ads = bannerControllers.single()
        val banner = tagged("test_banner_fixture")
        repeat(5) { click("counter") }
        listOf("ocean", "rose", "gold").forEach { id ->
            chooseColor(id)
            assertEquals("Color changes must reuse the banner controller", 1, bannerControllers.size)
            assertSame(ads, bannerControllers.single())
            assertSame("The same banner view is reattached", banner, tagged("test_banner_fixture"))
        }
        assertEquals("Color changes must not restart consent or ad initialization", 1, ads.events.count { it == "start" })
        assertFalse("The active banner must not be destroyed by a color change", ads.events.contains("destroy"))
        assertEquals(5L, store.count)
        assertEquals(0, store.goal)
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

    @Test fun saveHistoryRestoreAndDeleteRespondToTouchesRoutedFromTheirActualWindows() {
        layoutActivityWindow()
        repeat(7) { tapActivityControl("counter") }
        assertEquals(7L, store.count)

        tapActivityControl("save")
        val save = latestVisibleDialog()
        val saveInput = save.window!!.decorView.findViewWithTag<EditText>("session_name")
        assertFalse("An optional name must not request keyboard focus on opening Save", saveInput.hasFocus())
        assertEquals("Text entry keeps the native Save/Cancel actions reachable", WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            save.window!!.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
        saveInput.setText("")
        tapDialogButton(save, AlertDialog.BUTTON_POSITIVE)
        assertFalse("Saving closes the dialog without requiring a name", save.isShowing)
        val session = store.sessions().single()
        assertEquals("Counter", session.label)
        assertEquals(7L, session.count)
        assertEquals(0, session.goal)
        assertEquals("Saving preserves the active count", 7L, store.count)

        repeat(2) { tapActivityControl("counter") }
        assertEquals(9L, store.count)
        tapActivityControl("history")
        var history = latestVisibleDialog()
        tapDialogControl(history, "history_row_${session.id}")
        assertFalse("Opening a saved count closes the history list", history.isShowing)
        val restore = latestVisibleDialog()
        tapDialogButton(restore, AlertDialog.BUTTON_POSITIVE)
        assertEquals("Restore responds to the native dialog button", 7L, store.count)
        assertEquals("Counter", store.counterName)
        assertEquals(0, store.goal)

        tapActivityControl("history")
        history = latestVisibleDialog()
        tapDialogControl(history, "history_row_${session.id}")
        val actions = latestVisibleDialog()
        tapDialogButton(actions, AlertDialog.BUTTON_NEUTRAL)
        val confirm = latestVisibleDialog()
        assertEquals("Delete requires the separate confirmation", 1, store.sessions().size)
        tapDialogButton(confirm, AlertDialog.BUTTON_POSITIVE)
        assertTrue("Confirmed delete responds to a touch through its dialog", store.sessions().isEmpty())
        assertEquals("Deleting history preserves the active count", 7L, store.count)
        assertTrue("The empty history remains reachable", latestVisibleDialog().isShowing)
    }

    @Test @Config(qualifiers = "w240dp-h480dp-xxhdpi")
    fun bottomSaveAndHistoryRemainTouchableInAShortNarrowWindowWithLargestText() {
        RuntimeEnvironment.setFontScale(2f)
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
        layoutActivityWindow()

        tapActivityControl("counter")
        tapActivityControl("save")
        val save = latestVisibleDialog()
        tapDialogButton(save, AlertDialog.BUTTON_POSITIVE)
        assertEquals(1L, store.sessions().single().count)
        tapActivityControl("history")
        val history = latestVisibleDialog()
        tapDialogControl(history, "history_row_${store.sessions().single().id}")
        assertTrue("A scrolled history row opens its actions", latestVisibleDialog().isShowing)
        assertEquals(1L, store.count)
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

    private fun colorSwatch(dialog: AlertDialog, id: String): Button {
        val swatch = dialog.window!!.decorView.findViewWithTag<Button>("color_theme_$id")
        assertNotNull("A named color control exists for $id", swatch)
        return swatch
    }

    private fun chooseColor(id: String) {
        click("theme_colors")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        assertTrue("$id is selectable", colorSwatch(dialog, id).performClick())
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("A successful color choice closes the chooser", dialog.isShowing)
        assertEquals(id, store.colorThemeId)
    }

    private fun clickDialogButton(dialog: AlertDialog, which: Int) {
        dialog.getButton(which).performClick()
        // Framework AlertDialog posts its builder listeners through ButtonHandler.
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun latestVisibleDialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog().also {
        assertNotNull("A native dialog opens after the touch", it)
        assertTrue("The native dialog is visible", it.isShowing)
    }

    private fun layoutActivityWindow() {
        val metrics = activity.resources.displayMetrics
        layoutWindow(activity.window.decorView, metrics.widthPixels, metrics.heightPixels, View.MeasureSpec.EXACTLY)
    }

    private fun layoutDialogWindow(dialog: AlertDialog) {
        val metrics = activity.resources.displayMetrics
        // Fit the decor to the real physical display, leaving the usual dialog margins.
        val width = (metrics.widthPixels * 0.9f).toInt()
        val maximumHeight = metrics.heightPixels - (32 * metrics.density).toInt()
        layoutWindow(dialog.window!!.decorView, width, maximumHeight, View.MeasureSpec.AT_MOST)
    }

    private fun layoutWindow(decor: View, width: Int, height: Int, heightMode: Int) {
        shadowOf(Looper.getMainLooper()).idle()
        repeat(3) {
            forceLayoutTree(decor)
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, heightMode))
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        }
    }

    private fun forceLayoutTree(view: View) {
        view.forceLayout()
        if (view is ViewGroup) for (index in 0 until view.childCount) forceLayoutTree(view.getChildAt(index))
    }

    private fun tapActivityControl(tag: String) {
        val decor = activity.window.decorView
        val control = decor.findViewWithTag<View>(tag)
        assertNotNull("A real control exists for $tag", control)
        tapThroughDecor(decor, control, tag)
    }

    private fun tapDialogControl(dialog: AlertDialog, tag: String) {
        layoutDialogWindow(dialog)
        val decor = dialog.window!!.decorView
        val control = decor.findViewWithTag<View>(tag)
        assertNotNull("A native dialog control exists for $tag", control)
        tapThroughDecor(decor, control, tag)
    }

    private fun tapDialogButton(dialog: AlertDialog, which: Int) {
        layoutDialogWindow(dialog)
        tapThroughDecor(dialog.window!!.decorView, dialog.getButton(which), "dialog button $which")
    }

    private fun tapThroughDecor(decor: View, control: View, description: String) {
        assertTrue("$description is enabled", control.isEnabled)
        assertTrue("$description has a measured target", control.width > 0 && control.height > 0)
        control.requestRectangleOnScreen(Rect(0, 0, control.width, control.height), true)
        val visible = Rect()
        assertTrue("$description is reachable in its window", control.getGlobalVisibleRect(visible))
        assertEquals("$description is wholly visible horizontally", control.width, visible.width())
        assertEquals("$description is wholly visible vertically", control.height, visible.height())
        val origin = IntArray(2).also { decor.getLocationOnScreen(it) }
        val x = visible.exactCenterX() - origin[0]
        val y = visible.exactCenterY() - origin[1]
        val startedAt = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(startedAt, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                assertTrue("The window routes the touch to $description", decor.dispatchTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
        // Native View clicks and AlertDialog listeners can be posted to the main looper.
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
