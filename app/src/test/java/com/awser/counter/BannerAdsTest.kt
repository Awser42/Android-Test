package com.awser.counter

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

/** A host-only fixture: no Mobile Ads SDK, ad request, identifier or consent network call. */
internal class TestBannerAdController(activity: Activity, private val privacyChanged: (Boolean) -> Unit) : BannerAdController {
    val events = mutableListOf<String>()
    private val height = (50 * activity.resources.displayMetrics.density).toInt()
    private val banner = TextView(activity).apply {
        tag = "test_banner_fixture"
        text = "TEST AD · Layout fixture"
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.DKGRAY)
        setBackgroundColor(Color.rgb(242, 243, 244))
        contentDescription = "Test banner layout fixture. No ad request."
    }
    private var container: FrameLayout? = null
    override var privacyOptionsRequired: Boolean = false
        private set

    override fun attach(parent: FrameLayout) {
        events += "attach"
        (banner.parent as? ViewGroup)?.removeView(banner)
        parent.minimumHeight = height
        parent.layoutParams = (parent.layoutParams ?: FrameLayout.LayoutParams(-1, height)).apply { this.height = this@TestBannerAdController.height }
        parent.addView(banner, FrameLayout.LayoutParams(-1, height))
        container = parent
    }

    override fun start() { events += "start" }
    override fun resume() { events += "resume" }
    override fun pause() { events += "pause" }
    override fun destroy() {
        events += "destroy"
        container?.removeView(banner)
        container = null
    }
    override fun showPrivacyOptions() { events += "privacy" }

    fun requirePrivacyOptions(required: Boolean) {
        privacyOptionsRequired = required
        privacyChanged(required)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BannerAdsTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var ads: TestBannerAdController
    private var destroyed = false

    @Before fun launchWithAnOfflineBanner() {
        RuntimeEnvironment.getApplication().getSharedPreferences(CounterStore.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        BannerAds.factory = { activity, privacyChanged -> TestBannerAdController(activity, privacyChanged).also { ads = it } }
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    }

    @After fun close() {
        if (!destroyed) controller.pause().stop().destroy()
        BannerAds.factory = null
    }

    @Test fun activityMountsTheBannerAndDelegatesItsLifecycle() {
        val root = controller.get().window.decorView
        assertNotNull(root.findViewWithTag<FrameLayout>("ad_banner_container"))
        assertNotNull(root.findViewWithTag<TextView>("test_banner_fixture"))
        assertTrue(ads.events.contains("start"))
        assertTrue(ads.events.contains("resume"))
        controller.pause()
        assertEquals("pause", ads.events.last())
        controller.resume()
        assertEquals("resume", ads.events.last())
        controller.pause().stop().destroy()
        destroyed = true
        assertEquals("destroy", ads.events.last())
    }

    @Test fun requiredAdPrivacyCanBeOpenedAndSurvivesThemeChangesWithoutLosingCount() {
        val root = controller.get().window.decorView
        assertEquals(View.GONE, root.findViewWithTag<View>("ad_privacy").visibility)
        repeat(4) { root.findViewWithTag<View>("counter").performClick() }
        ads.requirePrivacyOptions(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, root.findViewWithTag<View>("ad_privacy").visibility)
        root.findViewWithTag<View>("ad_privacy").performClick()
        assertEquals("privacy", ads.events.last())
        root.findViewWithTag<View>("theme").performClick()
        assertEquals(View.VISIBLE, root.findViewWithTag<View>("ad_privacy").visibility)
        assertNotNull("The loaded banner is reattached after a theme change", root.findViewWithTag<TextView>("test_banner_fixture"))
        assertEquals("Theme changes must not restart ad consent/initialization", 1, ads.events.count { it == "start" })
        assertEquals(4L, CounterStore(controller.get()).count)
        ads.requirePrivacyOptions(false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, root.findViewWithTag<View>("ad_privacy").visibility)
        assertEquals(4L, CounterStore(controller.get()).count)
    }
}
