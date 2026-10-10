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

/** Exercises the real controller with a disconnected SDK boundary and deterministic time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BannerControllerRegressionTest {
    private lateinit var activityController: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var slot: FrameLayout
    private lateinit var sdk: OfflineBannerSdk
    private lateinit var ads: GoogleBannerAds
    private var elapsed = 1000L

    @Before fun setup() {
        activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activity = activityController.get()
        slot = measuredSlot()
        sdk = OfflineBannerSdk(activity)
        ads = GoogleBannerAds(activity, {}, sdk, { elapsed }, { it() })
        ads.attach(slot)
        ads.start()
    }

    @After fun close() {
        ads.destroy()
        activityController.pause().stop().destroy()
    }

    @Test fun failedFirstPrivacyRefreshCanRecoverOnlyAfterManualCooledDownRetry() {
        assertEquals("Creating the counter must not start a background privacy flow", 0, sdk.refreshes)
        ads.resume()
        assertEquals(1, sdk.refreshes)
        sdk.consentFailed(BannerAdStatus("Ad privacy check needs an internet connection.", "offline", true))
        assertTrue(ads.status.canRetry)
        assertEquals(0, sdk.initializations)
        ads.retry()
        assertEquals("Immediate retries cannot call UMP again", 1, sdk.refreshes)
        assertTrue(ads.status.message.contains("Retry in"))
        elapsed += 59_999
        ads.retry()
        assertEquals(1, sdk.refreshes)
        elapsed++
        ads.retry()
        assertEquals("A later manual retry refreshes failed first-session consent", 2, sdk.refreshes)
        assertFalse("Retry never grants consent itself", sdk.canRequestAds)
        assertEquals(0, sdk.initializations)
        sdk.canRequestAds = true
        sdk.consentSucceeded()
        assertEquals(1, sdk.consentForms)
        sdk.consentFormCompleted(null)
        assertEquals(1, sdk.initializations)
        sdk.initialized()
        pump()
        assertEquals(1, sdk.banners.size)
        sdk.banners.single().loaded()
        assertEquals("A real success hides the explanatory row", "", ads.status.message)
    }

    @Test fun pausedCounterDefersPrivacyFormInitializationAndAdLoadUntilItReturns() {
        ads.resume()
        ads.pause() // The separate Quran screen is now foreground.
        sdk.consentSucceeded()
        assertEquals(0, sdk.consentForms)
        assertEquals(0, sdk.initializations)
        ads.resume()
        assertEquals(1, sdk.consentForms)
        sdk.canRequestAds = true
        ads.pause()
        sdk.consentFormCompleted(null)
        assertEquals("A form callback while paused cannot initialize ads", 0, sdk.initializations)
        ads.resume()
        assertEquals(1, sdk.initializations)
        ads.pause()
        sdk.initialized()
        pump()
        assertEquals("An initialization callback while paused cannot request an ad", 0, sdk.banners.size)
        ads.resume()
        assertEquals(1, sdk.banners.size)
    }

    @Test fun pausedCounterAlsoCancelsInitializationThatHasNotStartedOnItsWorker() {
        ads.destroy()
        val pendingWork = mutableListOf<() -> Unit>()
        ads = GoogleBannerAds(activity, {}, sdk, { elapsed }, { pendingWork += it })
        ads.attach(slot)
        ads.start()
        ads.resume()
        sdk.canRequestAds = true
        sdk.consentSucceeded()
        sdk.consentFormCompleted(null)
        assertEquals(1, pendingWork.size)
        ads.pause()
        pendingWork.removeAt(0).invoke()
        pump()
        assertEquals("Queued work must recheck foreground before initializing", 0, sdk.initializations)
        ads.resume()
        pendingWork.removeAt(0).invoke()
        assertEquals(1, sdk.initializations)
    }

    @Test fun loadedAdsAndLayoutChangesCannotCreateManualOrFailureRetryLoops() {
        authorizeAndInitialize()
        val first = sdk.banners.single()
        first.loaded()
        repeat(5) { ads.retry() }
        assertEquals("Retry has no effect on a loaded ad", 1, sdk.banners.size)
        first.failed(BannerAdStatus("No ad available right now.", "no fill", true))
        assertEquals(0, slot.childCount)
        repeat(5) {
            slot = measuredSlot()
            ads.attach(slot)
            ads.pause()
            ads.resume()
            pump()
        }
        assertEquals("Theme/layout/resume never retry a failed unit", 1, sdk.banners.size)
        ads.retry()
        assertEquals(1, sdk.banners.size)
        elapsed += 60_000
        ads.retry()
        assertEquals(2, sdk.banners.size)
        assertEquals("Retry keeps valid consent", 1, sdk.refreshes)
        assertEquals("Retry reuses initialized SDK", 1, sdk.initializations)
        first.loaded()
        assertEquals("A stale old callback cannot show a retired banner", "Ad loading…", ads.status.message)
        val current = sdk.banners.last()
        assertSame(current.view, slot.getChildAt(0))
        current.loaded()
        assertEquals("", ads.status.message)
    }

    @Test fun litePlayServicesFailureIsReportedAndRecoverableWithoutStartingSdkEarly() {
        sdk.availability = 1 // Google's SERVICE_MISSING.
        ads.resume()
        sdk.canRequestAds = true
        sdk.consentSucceeded()
        sdk.consentFormCompleted(null)
        assertEquals(0, sdk.initializations)
        assertEquals(0, sdk.banners.size)
        assertTrue(ads.status.message.contains("Google Play services"))
        assertTrue(ads.status.canRetry)
        sdk.availability = 0
        elapsed += 60_000
        ads.retry()
        assertEquals(1, sdk.initializations)
        assertEquals(1, sdk.refreshes)
        sdk.initialized()
        pump()
        assertEquals(1, sdk.banners.size)
    }

    @Test fun retainedValidConsentCanRecoverAnOfflineRefreshWithoutBypassingTheGate() {
        ads.resume()
        sdk.canRequestAds = true
        sdk.consentFailed(BannerAdStatus("Offline", "privacy connection", true))
        assertEquals("Previously valid SDK consent is sufficient", 1, sdk.initializations)
        assertEquals(0, sdk.consentForms)
        sdk.initialized()
        pump()
        assertEquals(1, sdk.banners.size)
    }

    @Test fun destroyingCounterIgnoresLateConsentAndInitializationCallbacks() {
        val observed = mutableListOf<BannerAdStatus>()
        ads.setStatusListener { observed += it }
        ads.resume()
        val before = observed.size
        ads.destroy()
        sdk.canRequestAds = true
        sdk.consentSucceeded()
        ads.resume()
        ads.retry()
        assertEquals(before, observed.size)
        assertEquals(0, sdk.consentForms)
        assertEquals(0, sdk.initializations)
        assertEquals(0, sdk.banners.size)
    }

    @Test fun destroyedCounterIgnoresACompletedBackgroundInitialization() {
        ads.resume()
        sdk.canRequestAds = true
        sdk.consentSucceeded()
        sdk.consentFormCompleted(null)
        assertEquals(1, sdk.initializations)
        ads.destroy()
        sdk.initialized()
        pump()
        assertEquals(0, sdk.banners.size)
    }

    @Test fun errorMessagesDoNotTreatOtherDomainsAsGoogleNoFillOrClaimAccountApproval() {
        val noFill = bannerFailureStatus(3, "com.google.android.gms.ads", "No fill")
        assertEquals("No ad available right now.", noFill.message)
        assertTrue(noFill.canRetry)
        assertTrue(noFill.detail.contains("No fill"))
        assertEquals("Ad unavailable right now.", bannerFailureStatus(3, "another.adapter", "adapter failure").message)
        assertEquals("Ad connection failed. Check your internet.", bannerFailureStatus(2, "com.google.android.gms.ads", "network").message)
        assertEquals("Ad privacy setup needs attention.", consentFailureStatus(3, "App not configured").message)
        assertTrue(consentFailureStatus(3, "App not configured").detail.contains("App not configured"))
        assertEquals("Update Google Play services to show ads.", bannerFailureStatus(0, "com.google.android.gms.ads",
            "This app is using a lightweight version that requires Google Play services.").message)
    }

    private fun authorizeAndInitialize() {
        ads.resume()
        sdk.canRequestAds = true
        sdk.consentSucceeded()
        sdk.consentFormCompleted(null)
        sdk.initialized()
        pump()
        assertEquals(1, sdk.banners.size)
    }

    private fun measuredSlot(): FrameLayout = FrameLayout(activity).apply {
        val width = (320 * activity.resources.displayMetrics.density).toInt()
        val height = (50 * activity.resources.displayMetrics.density).toInt()
        measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        layout(0, 0, width, height)
    }

    private fun pump() { shadowOf(Looper.getMainLooper()).idle() }
}

private class OfflineBannerSdk(private val activity: Activity) : BannerAdSdk {
    override var canRequestAds = false
    override var privacyOptionsRequired = false
    var availability = 0
    var refreshes = 0
    var consentForms = 0
    var initializations = 0
    lateinit var consentSucceeded: () -> Unit
    lateinit var consentFailed: (BannerAdStatus) -> Unit
    lateinit var consentFormCompleted: (BannerAdStatus?) -> Unit
    lateinit var initialized: () -> Unit
    val banners = mutableListOf<OfflineBannerHandle>()
    override fun refreshConsent(success: () -> Unit, failure: (BannerAdStatus) -> Unit) {
        refreshes++
        consentSucceeded = success
        consentFailed = failure
    }
    override fun showConsentForm(complete: (BannerAdStatus?) -> Unit) { consentForms++; consentFormCompleted = complete }
    override fun showPrivacyForm(complete: (BannerAdStatus?) -> Unit) { consentFormCompleted = complete }
    override fun playServicesAvailability() = availability
    override fun initialize(complete: () -> Unit) { initializations++; initialized = complete }
    override fun createBanner(widthDp: Int): BannerAdHandle = OfflineBannerHandle(activity).also { banners += it }
}

private class OfflineBannerHandle(activity: Activity) : BannerAdHandle {
    override val view = View(activity).apply { visibility = View.INVISIBLE }
    lateinit var loaded: () -> Unit
    lateinit var failed: (BannerAdStatus) -> Unit
    override fun load(loaded: () -> Unit, failed: (BannerAdStatus) -> Unit) { this.loaded = loaded; this.failed = failed }
    override fun resume() = Unit
    override fun pause() = Unit
    override fun destroy() = Unit
}
