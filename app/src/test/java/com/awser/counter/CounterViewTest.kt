package com.awser.counter

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration

/** Regression checks for gesture cancellation, reduced motion and lifecycle cleanup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h418dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CounterViewTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var counter: CounterView
    private var count = 0L
    private var downTime = 0L

    @Before fun createCounter() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        setAnimationScale(0f)
        counter = CounterView(activity).apply {
            update(0L, true)
            onIncrement = { this@CounterViewTest.count++; update(this@CounterViewTest.count, true) }
        }
        val host = FrameLayout(activity).apply { addView(counter, FrameLayout.LayoutParams(360, 418)) }
        activity.setContentView(host)
        advanceFrames(32)
        measure(counter)
    }

    @After fun close() {
        controller.pause().stop().destroy()
        setAnimationScale(1f)
        ShadowChoreographer.setPaused(false)
    }

    @Test fun cancelledTouchDoesNotCountAndReleasesTheSilverButton() {
        val resting = frame(counter)
        touch(MotionEvent.ACTION_DOWN)
        assertFalse("Touching the button visibly depresses it", resting.contentEquals(frame(counter)))
        touch(MotionEvent.ACTION_CANCEL)
        assertEquals("A cancelled gesture must not count", 0L, count)
        assertArrayEquals("Cancellation restores the resting artwork", resting, frame(counter))
    }

    @Test fun reducedMotionStillCountsRealTapsAndLeavesAStableFinalFrame() {
        assertFalse(ValueAnimator.areAnimatorsEnabled())
        repeat(3) {
            touch(MotionEvent.ACTION_DOWN)
            touch(MotionEvent.ACTION_UP)
            assertEquals("One native click per completed touch", (it + 1).toLong(), count)
        }
        assertTrue(counter.counterButton.contentDescription.toString().contains("3"))
        val expected = CounterView(activity).apply { update(3L, true) }
        measure(expected)
        assertArrayEquals("Disabled motion leaves no stuck press or backlight", frame(expected), frame(counter))
    }

    @Test fun compactCounterRoutesVisibleMainButtonTapsPastTheResetTouchRectangle() {
        var resets = 0
        counter.onReset = { resets++ }
        counter.layoutParams = FrameLayout.LayoutParams(190, 221)
        advanceFrames(32)
        assertEquals(190, counter.width)
        assertEquals(221, counter.height)
        val reset = counter.findViewWithTag<View>("reset")
        assertTrue("Reset keeps its accessible 48dp bounds", reset.width >= 48)
        assertTrue("Reset keeps its accessible 48dp bounds", reset.height >= 48)

        fun tapVisiblePoint(logicalX: Float, logicalY: Float) {
            val scale = counter.width / 360f
            val verticalOffset = (counter.height - 418f * scale) / 2f
            val startedAt = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val event = MotionEvent.obtain(startedAt, SystemClock.uptimeMillis(), action,
                    logicalX * scale, logicalY * scale + verticalOffset, 0)
                try {
                    assertTrue("A visible button consumes the parent-routed touch", counter.dispatchTouchEvent(event))
                } finally {
                    event.recycle()
                }
                advanceFrames(16)
            }
        }

        // This visible silver-button edge falls inside the reset's rectangular accessibility bounds.
        tapVisiblePoint(235f, 252f)
        assertEquals("The visible large button increments exactly once", 1L, count)
        assertEquals("The reset rectangle must not steal this main-button tap", 0, resets)
        tapVisiblePoint(268f, 209f)
        assertEquals("The reset's visible centre remains usable", 1, resets)
        assertEquals("A reset tap must not increment", 1L, count)
    }

    @Test fun changingPaletteTintsTheLcdWhileKeepingSilverButtonsAndCountIntact() {
        count = 102L
        counter.update(count, true)
        val originalLcd = frameRegion(counter, Rect(60, 86, 300, 173))
        val silverButton = frameRegion(counter, buttonBounds(counter.counterButton))
        val resetArtworkArea = Rect(251, 192, 285, 226)
        val resetArtwork = frameRegion(counter, resetArtworkArea)

        val ocean = CounterThemes.resolve("ocean")
        counter.setColorTheme(ocean)
        assertFalse("A chosen palette visibly changes the bright LCD", originalLcd.contentEquals(frameRegion(counter, Rect(60, 86, 300, 173))))
        assertArrayEquals("The blank silver count button retains its artwork", silverButton, frameRegion(counter, buttonBounds(counter.counterButton)))
        assertArrayEquals("The small silver reset button retains its artwork", resetArtwork, frameRegion(counter, resetArtworkArea))
        assertEquals(102L, count)
        assertTrue(counter.counterButton.contentDescription.toString().contains("102"))

        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
        assertEquals("Palette selection keeps native taps working exactly once", 103L, count)
        val expected = CounterView(activity).apply { setColorTheme(ocean); update(103L, true) }
        measure(expected)
        assertArrayEquals("Counting keeps the selected palette", frame(expected), frame(counter))
    }

    @Test fun unlitLcdStaysNeutralAndRestoresItsSelectedPaletteAfterCounting() {
        count = 17L
        counter.update(count, true)
        counter.setColorTheme(CounterThemes.resolve("rose"))
        counter.setDisplayLightEnabled(false)
        val lcd = Rect(60, 86, 300, 173)
        val neutral = frameRegion(counter, lcd)
        val ocean = CounterThemes.resolve("ocean")
        counter.setColorTheme(ocean)
        assertArrayEquals("An unlit display stays neutral across palette selections", neutral, frameRegion(counter, lcd))

        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
        assertEquals(18L, count)
        assertFalse("The unlit LCD still draws the updated number", neutral.contentEquals(frameRegion(counter, lcd)))
        counter.setDisplayLightEnabled(true)
        val expected = CounterView(activity).apply { setColorTheme(ocean); update(18L, true) }
        measure(expected)
        assertArrayEquals("Turning lighting back on restores the selected color without losing count", frame(expected), frame(counter))
    }

    @Test fun pauseAndDetachRestoreTheRestingArtworkAfterAnAnimatedPress() {
        setAnimationScale(1f)
        val resting = frame(counter)
        touch(MotionEvent.ACTION_DOWN)
        // Advance Robolectric's virtual frames, rather than waiting on wall-clock timers.
        advanceFrames(1000)
        assertFalse("The animated press changes the visible artwork", resting.contentEquals(frame(counter)))
        counter.pauseAnimations()
        assertArrayEquals("Pausing releases the artwork", resting, frame(counter))
        touch(MotionEvent.ACTION_CANCEL)

        exportFrame(0)
        touch(MotionEvent.ACTION_DOWN)
        advanceAndExport(32, 1)
        advanceAndExport(32, 2)
        advanceAndExport(32, 3)
        touch(MotionEvent.ACTION_UP)
        exportFrame(4)
        advanceAndExport(64, 5)
        advanceAndExport(128, 6)
        advanceAndExport(256, 7)
        assertEquals(1L, count)
        val expected = CounterView(activity).apply { update(1L, true) }
        measure(expected)
        val afterClick = frame(expected)
        assertArrayEquals("A completed animated click settles to its saved count", afterClick, frame(counter))

        touch(MotionEvent.ACTION_DOWN)
        advanceFrames(1000)
        assertFalse(afterClick.contentEquals(frame(counter)))
        activity.setContentView(FrameLayout(activity))
        advanceFrames(16)
        assertFalse(counter.isAttachedToWindow)
        assertEquals(1L, count)
        assertArrayEquals("Detaching releases the artwork", afterClick, frame(counter))
    }

    private fun touch(action: Int) {
        val button = counter.counterButton
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, button.width / 2f, button.height / 2f, 0)
        try {
            assertTrue("The native button consumes this touch", button.dispatchTouchEvent(event))
        } finally {
            event.recycle()
        }
        advanceFrames(16)
    }

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(418, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 360, 418)
    }

    private fun frame(view: View): IntArray {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(view.width * view.height)
        bitmap.getPixels(pixels, 0, view.width, 0, 0, view.width, view.height)
        bitmap.recycle()
        return pixels
    }

    private fun buttonBounds(view: View) = Rect(view.left, view.top, view.right, view.bottom)

    private fun frameRegion(view: View, area: Rect): IntArray {
        val pixels = frame(view)
        return IntArray(area.width() * area.height()).also { result ->
            for (row in 0 until area.height()) pixels.copyInto(result, row * area.width(), (area.top + row) * view.width + area.left, (area.top + row) * view.width + area.right)
        }
    }

    private fun advanceAndExport(milliseconds: Long, index: Int) {
        advanceFrames(milliseconds)
        exportFrame(index)
    }

    private fun advanceFrames(milliseconds: Long) {
        var remaining = milliseconds
        while (remaining > 0) {
            val step = minOf(remaining, 16L)
            ShadowSystemClock.advanceBy(Duration.ofMillis(step))
            shadowOf(Looper.getMainLooper()).idle()
            remaining -= step
        }
    }

    private fun exportFrame(index: Int) {
        val directory = File(System.getProperty("screenshot.dir", "build/screenshots"), "animation").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(counter.width, counter.height, Bitmap.Config.ARGB_8888)
        counter.draw(Canvas(bitmap))
        File(directory, "counter-${index.toString().padStart(2, '0')}.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun setAnimationScale(scale: Float) {
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        // Android's framework test API sets the same process-wide scale consulted by ValueAnimator.
        ReflectionHelpers.callStaticMethod<Any>(ValueAnimator::class.java, "setDurationScale", ClassParameter.from(Float::class.javaPrimitiveType!!, scale))
    }
}
