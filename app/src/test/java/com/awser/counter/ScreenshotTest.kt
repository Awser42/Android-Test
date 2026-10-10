package com.awser.counter

import android.content.Context
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.TextView
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

/** Draw the real Android view tree into PNGs; no mock-up or image editor is involved. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @Before fun prepareCounter() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(CounterStore.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val store = CounterStore(context)
        store.updateCount(102L)
        store.setVibrationEnabled(false)
        BannerAds.factory = { activity, privacyChanged -> TestBannerAdController(activity, privacyChanged) }
    }

    @After fun clearAdFixture() {
        BannerAds.factory = null
        RuntimeEnvironment.setFontScale(1f)
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun darkPortrait() = export("counter-dark", 1080, 2400, true)

    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun lightPortrait() = export("counter-light", 1080, 2400, false)

    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun lcdLightOffPortrait() {
        CounterStore(RuntimeEnvironment.getApplication()).setDisplayLightEnabled(false)
        export("counter-display-light-off", 1080, 2400, true)
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun oceanPaletteInDarkMode() {
        CounterStore(RuntimeEnvironment.getApplication()).setColorThemeId("ocean")
        export("counter-ocean-dark", 1080, 2400, true)
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun rosePaletteInLightMode() {
        CounterStore(RuntimeEnvironment.getApplication()).setColorThemeId("rose")
        export("counter-rose-light", 1080, 2400, false)
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun nativeThemePickerNamesEveryPaletteAndKeepsTheLastChoiceReachable() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        try {
            val control = activity.window.decorView.findViewWithTag<View>("theme_colors")
            assertNotNull("The palette picker has a real control", control)
            assertTrue(control.performClick())
            shadowOf(Looper.getMainLooper()).idle()
            val dialog: AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing)
            val root = dialog.window!!.decorView
            val density = activity.resources.displayMetrics.density
            val width = (324 * density).toInt()
            val maximumHeight = (752 * density).toInt()
            repeat(3) {
                forceLayoutTree(root)
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maximumHeight, View.MeasureSpec.AT_MOST))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            }
            assertTextFits(root, "counter-theme-picker")
            CounterThemes.all.forEach { theme ->
                val choice = root.findViewWithTag<TextView>("color_theme_${theme.id}")
                assertNotNull("${theme.name} has a named native choice", choice)
                assertTrue(choice.text.toString().contains(theme.name))
                assertTouchSize(choice, theme.id, activity, "counter-theme-picker")
            }
            savePng(root, "counter-theme-picker", root.width, root.height)
            val last = root.findViewWithTag<View>("color_theme_monochrome")
            fullyVisible(last, "Monochrome palette", "counter-theme-picker")
            assertTrue(last.performClick())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("monochrome", CounterStore(activity).colorThemeId)
            assertEquals("Choosing a palette preserves the count", 102L, CounterStore(activity).count)
            assertFalse(dialog.isShowing)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun narrowPortrait() = export("counter-narrow", 960, 1920, true)

    @Test @Config(qualifiers = "w800dp-h360dp-land-xxhdpi")
    fun landscape() = export("counter-landscape", 2400, 1080, true)

    @Test @Config(qualifiers = "w280dp-h640dp-xxhdpi")
    fun compactSplitWindow() = export("counter-split-280", 840, 1920, true)

    @Test @Config(qualifiers = "w240dp-h480dp-xxhdpi")
    fun veryNarrowWindowWithAdPrivacy() = export("counter-narrow-privacy", 720, 1440, true, privacyRequired = true)

    @Test @Config(qualifiers = "w800dp-h300dp-land-xxhdpi")
    fun shortLandscape() = export("counter-short-landscape", 2400, 900, true)

    @Test @Config(qualifiers = "w600dp-h960dp-xxhdpi")
    fun tabletPortrait() = export("counter-tablet-portrait", 1800, 2880, true)

    @Test @Config(qualifiers = "w960dp-h600dp-land-xxhdpi")
    fun tabletLandscape() = export("counter-tablet-landscape", 2880, 1800, true)

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun largeTextReflowsWithoutClipping() = export("counter-font-150", 960, 1920, true, fontScale = 1.5f)

    @Test @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun largestTextReflowsWithoutClipping() = export("counter-font-200", 960, 1920, true, fontScale = 2f, privacyRequired = true)

    @Test @Config(qualifiers = "w240dp-h480dp-xxhdpi")
    fun alignedHeaderIconsStayCenteredInTheNarrowestWindowWithLargestText() =
        export("counter-header-narrow-font-200", 720, 1440, true, fontScale = 2f, privacyRequired = true)

    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun explicitTargetShowsProgress() {
        CounterStore(RuntimeEnvironment.getApplication()).setGoal(100)
        export("counter-explicit-target", 1080, 2400, true)
    }

    // Both live window sizes must fit the physical display used by ViewRoot visibility.
    @Test @Config(qualifiers = "w800dp-h800dp-land-xxhdpi")
    fun resizingTheActualWindowReflowsWithoutRecreatingTheCounter() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        try {
            val counter = activity.window.decorView.findViewWithTag<View>("counter")
            renderAndCheck(activity, "counter-window-before-resize", 2400, 1080)
            repeat(3) { assertTrue(counter.performClick()) }
            renderAndCheck(activity, "counter-window-resized-280", 840, 1920)
            assertSame("A live window resize must retain the native counter", counter, activity.window.decorView.findViewWithTag<View>("counter"))
            assertEquals("Resources still describe the original wide window", 800, activity.resources.configuration.screenWidthDp)
            assertEquals(105L, CounterStore(activity).count)
            assertTrue(activity.window.decorView.findViewWithTag<TextView>("count_label").text.toString().contains("105"))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun systemInsetsKeepTheCounterAndBannerInsideTheUsableWindow() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        try {
            val root = activity.window.decorView
            shadowOf(Looper.getMainLooper()).idle()
            val screen = root.findViewWithTag<View>("counter_screen")
            val density = activity.resources.displayMetrics.density
            val bars = Insets.of((8 * density).toInt(), (24 * density).toInt(), (8 * density).toInt(), (28 * density).toInt())
            screen.dispatchApplyWindowInsets(WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(), bars).build())
            assertEquals(bars.left, screen.paddingLeft)
            assertEquals(bars.top, screen.paddingTop)
            assertEquals(bars.right, screen.paddingRight)
            assertEquals(bars.bottom, screen.paddingBottom)
            renderAndCheck(activity, "counter-system-insets", 1080, 2400, drainLooper = false)
            val usable = Rect().also { assertTrue(screen.getGlobalVisibleRect(it)) }.apply {
                left += bars.left
                top += bars.top
                right -= bars.right
                bottom -= bars.bottom
            }
            listOf("counter", "ad_banner_container", "save", "history").forEach { tag ->
                val control = root.findViewWithTag<View>(tag)
                val area = fullyVisible(control, tag, "counter-system-insets")
                assertTrue("$tag remains inside the inset-adjusted window", usable.contains(area))
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun export(name: String, width: Int, height: Int, darkTheme: Boolean, fontScale: Float = 1f, privacyRequired: Boolean = false) {
        RuntimeEnvironment.setFontScale(fontScale)
        CounterStore(RuntimeEnvironment.getApplication()).setDarkTheme(darkTheme)
        lateinit var ads: TestBannerAdController
        BannerAds.factory = { activity, privacyChanged -> TestBannerAdController(activity, privacyChanged).also { ads = it } }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        try {
            if (privacyRequired) ads.requirePrivacyOptions(true)
            renderAndCheck(activity, name, width, height)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun renderAndCheck(activity: MainActivity, name: String, width: Int, height: Int, drainLooper: Boolean = true) {
        val root = activity.window.decorView
        if (drainLooper) shadowOf(Looper.getMainLooper()).idle()
        // Complete native layout requests while preserving the exact window bounds under test.
        repeat(3) {
            forceLayoutTree(root)
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
        }
        val store = CounterStore(activity)
        val progress = root.findViewWithTag<View>("progress")
        val countText = root.findViewWithTag<TextView>("count_label").text.toString()
        assertEquals("Progress is optional in $name", if (store.goal > 0) View.VISIBLE else View.GONE, progress.visibility)
        assertEquals("Only selected targets appear in $name", store.goal > 0, countText.contains("Target"))
        assertTextFits(root, name)
        val header = root.findViewWithTag<View>("counter_header")
        assertNotNull("Theme and LCD light have a header in $name", header)
        assertNotNull(header.findViewWithTag<View>("theme"))
        assertNotNull(header.findViewWithTag<View>("light"))
        assertHeaderGeometry(root, activity, name)
        val counter = root.findViewWithTag<View>("counter")
        assertNotNull("Counter tap target exists in $name", counter)
        assertTouchSize(counter, "counter", activity, name)
        val counterArea = fullyVisible(counter, "counter", name)
        val banner = root.findViewWithTag<View>("ad_banner_container")
        assertNotNull("A separate banner slot exists in $name", banner)
        assertNotNull("Host screenshot uses an explicit test-only ad fixture", root.findViewWithTag<View>("test_banner_fixture"))
        assertTrue("Loaded banner retains at least 50dp in $name", banner.height >= 50 * activity.resources.displayMetrics.density)
        val bannerArea = fullyVisible(banner, "ad_banner_container", name)
        assertFalse("Banner must not cover the main counter in $name", Rect.intersects(bannerArea, counterArea))

        savePng(root, name, width, height)

        // Scrolling is valid on short windows; every control must remain wholly reachable.
        val tags = mutableListOf("vibration", "goal", "rename", "theme", "light", "theme_colors", "save", "history")
        if (root.findViewWithTag<View>("ad_privacy").visibility == View.VISIBLE) tags += "ad_privacy"
        tags.forEach { tag ->
            val control = root.findViewWithTag<View>(tag)
            assertNotNull("$tag exists in $name", control)
            assertTouchSize(control, tag, activity, name)
            val visible = fullyVisible(control, tag, name)
            assertFalse("Banner must not cover $tag in $name", Rect.intersects(bannerArea, visible))
        }
    }

    private fun assertTouchSize(view: View, tag: String, activity: MainActivity, name: String) {
        val minimum = 48 * activity.resources.displayMetrics.density
        assertTrue("$tag has 48dp touch width in $name", view.width >= minimum)
        assertTrue("$tag has 48dp touch height in $name", view.height >= minimum)
    }

    private fun assertHeaderGeometry(root: View, activity: MainActivity, name: String) {
        val rows = listOf(
            "header_appearance_controls" to listOf("theme", "light", "theme_colors"),
            "header_counter_controls" to listOf("vibration", "goal", "rename"),
        )
        val expectedSize = (48 * activity.resources.displayMetrics.density + .5f).toInt()
        val columnCenters = mutableListOf<List<Float>>()
        var previousRowBottom = Float.NEGATIVE_INFINITY
        rows.forEach { (rowTag, tags) ->
            val row = root.findViewWithTag<ViewGroup>(rowTag)
            assertNotNull("$rowTag exists in $name", row)
            assertEquals("$rowTag keeps three stable columns in $name", 3, row.childCount)
            val rowLocation = IntArray(2).also { row.getLocationInWindow(it) }
            assertTrue("Header rows never overlap in $name", rowLocation[1].toFloat() >= previousRowBottom)
            previousRowBottom = (rowLocation[1] + row.height).toFloat()
            val centers = tags.mapIndexed { index, tag ->
                val control = row.findViewWithTag<TextView>(tag)
                assertNotNull("$tag remains in its named row in $name", control)
                assertSame("$tag keeps its visible left-to-right order in $name", row.getChildAt(index), control.parent)
                assertEquals("$tag keeps its 48dp touch width in $name", expectedSize, control.width)
                assertEquals("$tag keeps its 48dp touch height in $name", expectedSize, control.height)
                val column = row.getChildAt(index)
                val columnLocation = IntArray(2).also { column.getLocationInWindow(it) }
                val location = IntArray(2).also { control.getLocationInWindow(it) }
                // Integer pixel layout permits half a pixel of centre rounding.
                assertEquals("$tag touch area is centered in its column in $name", columnLocation[0] + column.width / 2f, location[0] + control.width / 2f, .5f)
                assertEquals("$tag touch area is vertically centered in $name", rowLocation[1] + row.height / 2f, location[1] + control.height / 2f, .5f)
                assertTrue("$tag stays inside its row in $name", location[0] >= rowLocation[0] && location[0] + control.width <= rowLocation[0] + row.width)

                // Draw the actual native control even when a short window has scrolled its row away.
                val bitmap = Bitmap.createBitmap(control.width, control.height, Bitmap.Config.ARGB_8888)
                try { control.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
                val glyph = control.foreground
                assertNotNull("$tag has a native icon foreground in $name", glyph)
                val bounds = glyph.bounds
                assertTrue("$tag icon was actually drawn in $name", bounds.width() > 0 && bounds.height() > 0)
                assertTrue("$tag icon stays inside the touch area in $name", Rect(0, 0, control.width, control.height).contains(bounds))
                assertEquals("$tag icon is horizontally centered in $name", control.width / 2f, (bounds.left + bounds.right) / 2f, .5f)
                assertEquals("$tag icon is vertically centered in $name", control.height / 2f, (bounds.top + bounds.bottom) / 2f, .5f)
                assertTrue("$tag has no side-positioned compound icon in $name", control.compoundDrawables.all { it == null })
                location[0] + control.width / 2f
            }
            assertTrue("Icons keep left-to-right order in $name", centers[0] < centers[1] && centers[1] < centers[2])
            assertEquals("Header columns have equal spacing in $name", centers[1] - centers[0], centers[2] - centers[1], 1f)
            columnCenters += centers
        }
        columnCenters[0].indices.forEach { column ->
            assertEquals("Appearance and counter icons share true column centers in $name", columnCenters[0][column], columnCenters[1][column], 0f)
        }
    }

    private fun savePng(view: View, name: String, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File(System.getProperty("screenshot.dir", "build/screenshots") ?: "build/screenshots").apply { mkdirs() }
        val output = File(directory, "$name.png")
        output.outputStream().use { stream -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
        assertTrue("Screenshot was exported", output.length() > 10_000)
        println("Screenshot: ${output.absolutePath}")
        bitmap.recycle()
    }

    private fun fullyVisible(view: View, tag: String, name: String): Rect {
        view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        val visible = Rect()
        assertTrue("$tag is reachable in $name", view.getGlobalVisibleRect(visible))
        assertEquals("$tag full width visible in $name", view.width, visible.width())
        assertEquals("$tag full height visible in $name", view.height, visible.height())
        return visible
    }

    private fun assertTextFits(view: View, name: String) {
        if (view.visibility != View.VISIBLE) return
        if (view is TextView && view.text.isNotEmpty() && view.tag != "test_banner_fixture") {
            val textLayout = view.layout
            val description = "${view.tag ?: view.text} in $name"
            assertNotNull("Native text has a layout for $description", textLayout)
            val contentHeight = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
            assertTrue("Text must not be vertically clipped for $description", textLayout.height <= contentHeight + 1)
            // The user-supplied counter name deliberately has a one-line ellipsis.
            if (view.tag != "counter_name_label") {
                val contentWidth = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
                for (line in 0 until textLayout.lineCount) {
                    assertEquals("Text must not be ellipsized for $description", 0, textLayout.getEllipsisCount(line))
                    assertTrue("Text must fit its control for $description", textLayout.getLineWidth(line) <= contentWidth + 1f)
                }
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) assertTextFits(view.getChildAt(index), name)
    }

    private fun forceLayoutTree(view: View) {
        view.forceLayout()
        if (view is ViewGroup) for (index in 0 until view.childCount) forceLayoutTree(view.getChildAt(index))
    }
}
