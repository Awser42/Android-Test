package com.awser.counter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuranActivityTest {
    private lateinit var controller: ActivityController<QuranActivity>
    private lateinit var activity: QuranActivity
    private lateinit var savedCounterPreferences: Map<String, *>
    private val root get() = activity.window.decorView

    @Before fun launchWithoutAnyAdvertisingController() {
        val context: Context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences(CounterStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        CounterStore(context).apply {
            updateCount(42L)
            setGoal(25)
            setCounterName("Keep this count")
            setColorThemeId("ocean")
            setDisplayLightEnabled(false)
            setVibrationEnabled(false)
            saveSession("Saved before reading")
        }
        savedCounterPreferences = preferences.all.toMap()
        BannerAds.factory = { _, _ -> throw AssertionError("Quran must never create an advertising controller") }
        controller = Robolectric.buildActivity(QuranActivity::class.java).setup()
        activity = controller.get()
    }

    @After fun closeAndVerifyCounterWasUntouched() {
        try {
            controller.pause().stop().destroy()
            val current = RuntimeEnvironment.getApplication().getSharedPreferences(CounterStore.PREFERENCES_NAME, Context.MODE_PRIVATE).all
            assertEquals("Reading must leave every counter preference and saved session intact", savedCounterPreferences, current)
        } finally {
            BannerAds.factory = null
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test fun allThreeChoicesRenderTheirCompleteBundledArabicTextWithoutAds() {
        val expectedNumbers = mapOf("yaseen" to (1..83).toList(), "ayat_kareema" to listOf(87), "ayatul_kursi" to listOf(255))
        QuranContent.readings.forEach { reading ->
            assertTrue(tagged("quran_choice_${reading.id}").performClick())
            measureWindow(360, 800)
            val output = when (reading.id) {
                "yaseen" -> "quran-yaseen"
                "ayat_kareema" -> "quran-kareema"
                else -> "quran-kursi"
            }
            export(output)
            assertEquals(expectedNumbers.getValue(reading.id), QuranContent.load(activity, reading.id).map { it.number })
            assertRenderedReading(reading.id)
            assertTrue(tagged("quran_choice_${reading.id}").isSelected)
            assertNoAdViews(root)
        }
        assertEquals(42L, CounterStore(activity).count)
    }

    @Test fun selectedReadingSurvivesActivityRecreation() {
        tagged("quran_choice_ayatul_kursi").performClick()
        measureWindow(360, 800)
        controller.recreate()
        activity = controller.get()
        measureWindow(360, 800)
        assertTrue(tagged("quran_choice_ayatul_kursi").isSelected)
        assertFalse(tagged("quran_choice_yaseen").isSelected)
        assertRenderedReading("ayatul_kursi")
        assertEquals(42L, CounterStore(activity).count)
    }

    @Test fun yaseenScrollPositionSurvivesRecreationAndTheLastVerseIsReachable() {
        measureWindow(360, 800)
        val lastVerse = tagged("quran_verse_83")
        lastVerse.requestRectangleOnScreen(Rect(0, 0, lastVerse.width, lastVerse.height), true)
        val visible = Rect()
        assertTrue("Yaseen's 83rd verse is reachable", lastVerse.getGlobalVisibleRect(visible))
        assertEquals(lastVerse.height, visible.height())
        val previousScroll = (tagged("quran_scroll") as ScrollView).scrollY
        assertTrue("All 83 verses make the reading scrollable", previousScroll > 0)
        controller.recreate()
        activity = controller.get()
        measureWindow(360, 800)
        assertEquals("Recreation keeps the reader's place", previousScroll, (tagged("quran_scroll") as ScrollView).scrollY)
        assertRenderedReading("yaseen")
    }

    @Test @Config(qualifiers = "w240dp-h480dp-xxhdpi")
    fun largeArabicAndChoiceLabelsFitANarrowWindowAndKeepReadingScrollable() {
        restartAtFontScale(2f)
        measureWindow(240, 480)
        export("quran-large-text")
        assertReadableViewportAndControls()
        assertRenderedReading("yaseen")
        val scroll = tagged("quran_scroll") as ScrollView
        assertTrue(scroll.fullScroll(View.FOCUS_DOWN))
        assertTrue(scroll.scrollY > 0)
        assertNoAdViews(root)
    }

    @Test @Config(qualifiers = "w800dp-h300dp-land-xxhdpi")
    fun largeTextInShortLandscapeKeepsAUsableReadingViewport() {
        restartAtFontScale(2f)
        measureWindow(800, 300)
        assertReadableViewportAndControls()
        tagged("quran_choice_ayat_kareema").performClick()
        measureWindow(800, 300)
        assertRenderedReading("ayat_kareema")
        assertTrue((tagged("quran_scroll") as ScrollView).height >= dp(96))
        assertNoAdViews(root)
    }

    @Test fun visibleBackButtonFinishesReadingWithoutResettingTheCounter() {
        tagged("quran_choice_ayat_kareema").performClick()
        assertTrue(tagged("quran_back").performClick())
        assertTrue(activity.isFinishing)
        assertEquals(42L, CounterStore(activity).count)
        assertEquals(25, CounterStore(activity).goal)
        assertEquals("ocean", CounterStore(activity).colorThemeId)
    }

    private fun assertRenderedReading(id: String) {
        val reading = QuranContent.readings.first { it.id == id }
        val verses = QuranContent.load(activity, id)
        val verseViews = descendants(root).filterIsInstance<TextView>().filter { it.tag?.toString()?.matches(Regex("quran_verse_[0-9]+")) == true }
        assertEquals("Every verse is rendered once", verses.size, verseViews.size)
        assertEquals(reading.title, (tagged("quran_reading_title") as TextView).text.toString())
        assertEquals(reading.reference, (tagged("quran_reference") as TextView).text.toString())
        assertEquals(if (verses.size == 1) "1 verse" else "${verses.size} verses", (tagged("quran_verse_count") as TextView).text.toString())
        verses.forEach { verse ->
            val text = tagged("quran_verse_${verse.number}") as TextView
            assertEquals("Arabic verse codepoints are unchanged", verse.text, text.text.toString())
            assertEquals(View.TEXT_DIRECTION_RTL, text.textDirection)
            assertEquals(View.LAYOUT_DIRECTION_RTL, text.layoutDirection)
            assertTrue("Arabic diacritics have font padding", text.includeFontPadding)
            assertTextFits(text)
        }
        val basmala = QuranContent.basmala(activity, id)
        val basmalaViews = descendants(root).filter { it.tag == "quran_basmala" }
        assertEquals("Chapter-opening basmala is shown separately once", if (basmala == null) 0 else 1, basmalaViews.size)
        if (basmala != null) assertEquals(basmala, (basmalaViews.single() as TextView).text.toString())
    }

    private fun assertReadableViewportAndControls() {
        assertTrue("Large fixed controls must leave space for Quran text", tagged("quran_scroll").height >= dp(96))
        (listOf("quran_back") + QuranContent.readings.map { "quran_choice_${it.id}" }).forEach { tag ->
            val control = tagged(tag)
            assertTrue("$tag has an accessible width", control.width >= dp(48))
            assertTrue("$tag has an accessible height", control.height >= dp(48))
            val visible = Rect()
            assertTrue("$tag remains visible", control.getGlobalVisibleRect(visible))
            assertEquals(control.width, visible.width())
            assertEquals(control.height, visible.height())
            assertTextFits(control as TextView)
        }
    }

    private fun assertTextFits(text: TextView) {
        val layout = text.layout
        assertNotNull("Native Arabic/text layout exists", layout)
        assertTrue("Text must not be vertically clipped", layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom + 1)
        val contentWidth = text.width - text.compoundPaddingLeft - text.compoundPaddingRight
        for (line in 0 until layout.lineCount) {
            assertEquals("Reading text is never ellipsized", 0, layout.getEllipsisCount(line))
            val width = layout.getLineWidth(line)
            val visibleWidth = layout.getLineMax(line)
            val left = layout.getLineLeft(line)
            val right = layout.getLineRight(line)
            val detail = "${text.tag} line=$line width=$width visible=$visibleWidth left=$left right=$right available=$contentWidth"
            if (width > contentWidth + 1f) println("Native text bounds: $detail")
            // getLineWidth includes unpainted trailing spaces; alignment uses the visible extent.
            assertTrue("Reading text fits the available width: $detail", visibleWidth <= contentWidth + 1f)
            assertTrue("Reading text stays inside its left edge: $detail", left >= -1f)
            assertTrue("Reading text stays inside its right edge: $detail", right <= contentWidth + 1f)
        }
    }

    private fun assertNoAdViews(view: View) {
        assertFalse("Quran has no ad SDK views", view.javaClass.name.startsWith("com.google.android.gms.ads."))
        assertNotEquals("Quran has no banner slot", "ad_banner_container", view.tag)
        assertNotEquals("Quran has no ad privacy controller", "ad_privacy", view.tag)
        if (view is ViewGroup) for (index in 0 until view.childCount) assertNoAdViews(view.getChildAt(index))
    }

    private fun restartAtFontScale(scale: Float) {
        controller.pause().stop().destroy()
        RuntimeEnvironment.setFontScale(scale)
        controller = Robolectric.buildActivity(QuranActivity::class.java).setup()
        activity = controller.get()
    }

    private fun measureWindow(widthDp: Int, heightDp: Int) {
        shadowOf(Looper.getMainLooper()).idle()
        repeat(3) {
            forceLayout(root)
            root.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, dp(widthDp), dp(heightDp))
        }
    }

    private fun forceLayout(view: View) {
        view.forceLayout()
        if (view is ViewGroup) for (index in 0 until view.childCount) forceLayout(view.getChildAt(index))
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun tagged(tag: String): View = root.findViewWithTag<View>(tag)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()

    private fun export(name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val directory = File(System.getProperty("screenshot.dir", "build/screenshots") ?: "build/screenshots").apply { mkdirs() }
        val file = File(directory, "$name.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue("The actual Quran view was exported", file.length() > 10_000)
        println("Screenshot: ${file.absolutePath}")
        bitmap.recycle()
    }
}
