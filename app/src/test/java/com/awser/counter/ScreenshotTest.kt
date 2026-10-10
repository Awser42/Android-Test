package com.awser.counter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
        store.setGoal(100)
        store.setVibrationEnabled(false)
        BannerAds.factory = { activity, privacyChanged -> TestBannerAdController(activity, privacyChanged) }
    }

    @After fun clearAdFixture() { BannerAds.factory = null }

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

    private fun export(name: String, width: Int, height: Int, darkTheme: Boolean) {
        CounterStore(RuntimeEnvironment.getApplication()).setDarkTheme(darkTheme)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        try {
            val root = activity.window.decorView
            shadowOf(Looper.getMainLooper()).idle()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            // CounterView sizes its native hit areas during the first layout; finish that request.
            forceLayoutTree(root)
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val counter = root.findViewWithTag<View>("counter")
            assertNotNull("Counter tap target exists in $name", counter)
            assertTrue("Counter tap target has useful width in $name", counter.width >= 48 * activity.resources.displayMetrics.density)
            assertTrue("Counter tap target has useful height in $name", counter.height >= 48 * activity.resources.displayMetrics.density)
            // A short landscape window may legitimately use the app's vertical scroll container.
            counter.requestRectangleOnScreen(Rect(0, 0, counter.width, counter.height), true)
            val visibleArea = Rect()
            assertTrue("Counter remains visible in $name", counter.getGlobalVisibleRect(visibleArea))
            assertEquals("Entire tap target visible in $name", counter.width, visibleArea.width())
            assertEquals("Entire tap target visible in $name", counter.height, visibleArea.height())
            val banner = root.findViewWithTag<View>("ad_banner_container")
            assertNotNull("A separate banner slot exists in $name", banner)
            assertNotNull("Host screenshot uses an explicit test-only ad fixture", root.findViewWithTag<View>("test_banner_fixture"))
            assertTrue("Loaded banner retains at least50dp in $name", banner.height >= 50 * activity.resources.displayMetrics.density)
            val bannerArea = Rect()
            assertTrue("Banner slot is visible in $name", banner.getGlobalVisibleRect(bannerArea))
            assertEquals("Full banner width remains visible in $name", banner.width, bannerArea.width())
            assertEquals("Full banner height remains visible in $name", banner.height, bannerArea.height())
            assertFalse("Banner must not cover the main counter in $name", Rect.intersects(bannerArea, visibleArea))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val directory = File(System.getProperty("screenshot.dir", "build/screenshots")).apply { mkdirs() }
            val output = File(directory, "$name.png")
            output.outputStream().use { stream -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
            assertTrue("Screenshot was exported", output.length() > 10_000)
            println("Screenshot: ${output.absolutePath}")
            bitmap.recycle()
            // Essential controls remain reachable on narrow phones and in landscape.
            listOf("vibration", "goal", "rename", "theme", "light", "save", "history").forEach { tag ->
                val control = root.findViewWithTag<View>(tag)
                assertNotNull("$tag exists in $name", control)
                assertTrue("$tag has 48dp touch width in $name", control.width >= 48 * activity.resources.displayMetrics.density)
                assertTrue("$tag has 48dp touch height in $name", control.height >= 48 * activity.resources.displayMetrics.density)
                control.requestRectangleOnScreen(Rect(0, 0, control.width, control.height), true)
                val visible = Rect()
                assertTrue("$tag is reachable in $name", control.getGlobalVisibleRect(visible))
                assertEquals("$tag full width visible in $name", control.width, visible.width())
                assertEquals("$tag full height visible in $name", control.height, visible.height())
                assertFalse("Banner must not cover $tag in $name", Rect.intersects(bannerArea, visible))
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun forceLayoutTree(view: View) {
        view.forceLayout()
        if (view is ViewGroup) for (index in 0 until view.childCount) forceLayoutTree(view.getChildAt(index))
    }
}
