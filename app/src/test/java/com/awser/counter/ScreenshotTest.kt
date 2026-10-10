package com.awser.counter

import android.content.Context
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

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val directory = File(System.getProperty("screenshot.dir", "build/screenshots")).apply { mkdirs() }
        val output = File(directory, "$name.png")
        output.outputStream().use { stream -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
        assertTrue("Screenshot was exported", output.length() > 10_000)
        println("Screenshot: ${output.absolutePath}")
        bitmap.recycle()

        // Scrolling is valid on short windows; every control must remain wholly reachable.
        val tags = mutableListOf("vibration", "goal", "rename", "theme", "light", "save", "history")
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
