package com.awser.counter

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlin.math.floor

/** UI-facing controller; JVM tests can replace it without touching either Google SDK. */
interface BannerAdController {
    val privacyOptionsRequired: Boolean
    fun attach(container: FrameLayout)
    fun start()
    fun resume()
    fun pause()
    fun destroy()
    fun showPrivacyOptions()
}

object BannerAds {
    /** Set/reset in test setup/teardown to inject a fake with predictable banner dimensions. */
    var factory: ((Activity, (Boolean) -> Unit) -> BannerAdController)? = null

    fun create(activity: Activity, onPrivacyOptionsChanged: (Boolean) -> Unit = {}): BannerAdController {
        factory?.let { return it(activity, onPrivacyOptionsChanged) }
        // Host tests must never initialize the ads SDK or make external ad/consent requests.
        if (Build.FINGERPRINT == "robolectric") return DisabledBannerAds()
        return GoogleBannerAds(activity, onPrivacyOptionsChanged)
    }

    fun disabled(): BannerAdController = DisabledBannerAds()
}

private class DisabledBannerAds : BannerAdController {
    override val privacyOptionsRequired = false
    override fun attach(container: FrameLayout) { container.removeAllViews() }
    override fun start() = Unit
    override fun resume() = Unit
    override fun pause() = Unit
    override fun destroy() = Unit
    override fun showPrivacyOptions() = Unit
}

/** All View and UMP calls run on the Activity's main thread. Counting never waits for an ad. */
private class GoogleBannerAds(
    private val activity: Activity,
    private val onPrivacyOptionsChanged: (Boolean) -> Unit,
) : BannerAdController {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private var container: FrameLayout? = null
    private var adView: AdView? = null
    private var started = false
    @Volatile private var destroyed = false
    private var resumed = false
    private var initializationStarted = false
    private var sdkReady = false
    private var consentRequestComplete = false
    private var requestedWidthDp = 0
    private var requestedOrientation = 0
    private var privacyFormOpen = false
    private var lastLoadFailed = false
    private var failedAtElapsedTime = 0L

    override val privacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    private fun isClosed() = destroyed || activity.isFinishing || activity.isDestroyed

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        requestBannerIfReady()
    }

    override fun attach(container: FrameLayout) {
        if (isClosed()) return
        this.container?.removeOnLayoutChangeListener(layoutListener)
        // Theme changes rebuild the footer. Reparent the same loaded/pending banner instead
        // of creating another request when its available width has not changed.
        this.container?.removeAllViews()
        this.container = container
        // Empty wrap-content container has zero height but retains its measured width.
        container.removeAllViews()
        container.minimumHeight = 0
        container.visibility = View.VISIBLE
        if (!lastLoadFailed) adView?.let { addBannerToSlot(container, it) }
        container.addOnLayoutChangeListener(layoutListener)
        container.post { requestBannerIfReady() }
    }

    override fun start() {
        if (started || isClosed()) return
        started = true
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(activity, parameters, {
            if (isClosed()) return@requestConsentInfoUpdate
            notifyPrivacyOptions()
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                if (!isClosed()) {
                    consentRequestComplete = true
                    notifyPrivacyOptions()
                    initializeIfConsentAllows()
                }
            }
        }, {
            // An offline refresh can still use valid consent retained from an earlier session.
            if (!isClosed()) {
                consentRequestComplete = true
                notifyPrivacyOptions()
                initializeIfConsentAllows()
            }
        })
    }

    private fun notifyPrivacyOptions() {
        onPrivacyOptionsChanged(privacyOptionsRequired)
    }

    private fun initializeIfConsentAllows() {
        if (isClosed() || !consentRequestComplete || privacyFormOpen || !consentInformation.canRequestAds()) return
        if (sdkReady) {
            requestBannerIfReady()
            return
        }
        if (initializationStarted) return
        initializationStarted = true
        // Google's initialization can do work; keep it away from the counter's main thread.
        Thread({
            try {
                if (!destroyed) {
                    MobileAds.initialize(activity.applicationContext) {
                        activity.runOnUiThread {
                            if (!isClosed()) {
                                sdkReady = true
                                requestBannerIfReady()
                            }
                        }
                    }
                }
            } catch (_: RuntimeException) {
                // An unavailable SDK/service must never terminate an offline counting session.
                activity.runOnUiThread {
                    if (!isClosed()) initializationStarted = false
                }
            }
        }, "admob-initialize").apply { isDaemon = true; start() }
    }

    private fun requestBannerIfReady() {
        val slot = container ?: return
        if (isClosed() || !resumed || !sdkReady || !consentRequestComplete || privacyFormOpen ||
            !consentInformation.canRequestAds()) return
        val pixels = slot.width - slot.paddingLeft - slot.paddingRight
        if (pixels <= 0) return
        val widthDp = floor(pixels / activity.resources.displayMetrics.density.toDouble()).toInt()
        if (widthDp <= 0) return
        val orientation = activity.resources.configuration.orientation
        if (widthDp == requestedWidthDp && orientation == requestedOrientation) return
        clearAd()
        requestedWidthDp = widthDp
        requestedOrientation = orientation
        val banner = AdView(activity).apply {
            visibility = View.INVISIBLE
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        }
        adView = banner
        banner.adListener = object : AdListener() {
            override fun onAdLoaded() {
                if (isClosed() || adView !== banner || privacyFormOpen || !consentInformation.canRequestAds()) return
                val current = container ?: return
                banner.visibility = View.VISIBLE
                lastLoadFailed = false
                addBannerToSlot(current, banner)
                if (resumed) banner.resume() else banner.pause()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (adView === banner) {
                    lastLoadFailed = true
                    failedAtElapsedTime = SystemClock.elapsedRealtime()
                    container?.removeAllViews()
                    banner.pause()
                    // No empty banner space and no retries in a tight loop on an offline device.
                }
            }
        }
        // Attach before load as in Google's sample. INVISIBLE reserves the adaptive height
        // without presenting a clickable empty ad; failure removes this space entirely.
        addBannerToSlot(slot, banner)
        try {
            banner.loadAd(AdRequest.Builder().build())
        } catch (_: RuntimeException) {
            clearAd()
            // Keep this width marked attempted so layout events cannot create a retry loop.
            requestedWidthDp = widthDp
            requestedOrientation = orientation
            lastLoadFailed = true
            failedAtElapsedTime = SystemClock.elapsedRealtime()
        }
    }

    private fun addBannerToSlot(slot: FrameLayout, banner: AdView) {
        (banner.parent as? FrameLayout)?.removeView(banner)
        slot.removeAllViews()
        slot.addView(banner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
    }

    override fun resume() {
        if (isClosed()) return
        resumed = true
        if (lastLoadFailed && SystemClock.elapsedRealtime() - failedAtElapsedTime >= RETRY_COOLDOWN_MS) {
            // A later return to the app can retry transient network/no-fill failures. There
            // is no timer, automatic retry loop, or retry caused by a theme/layout change.
            clearAd()
        }
        adView?.resume()
        if (sdkReady) requestBannerIfReady() else initializeIfConsentAllows()
    }

    override fun pause() {
        resumed = false
        adView?.pause()
    }

    override fun showPrivacyOptions() {
        if (isClosed() || privacyFormOpen || !privacyOptionsRequired) return
        privacyFormOpen = true
        clearAd()
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            if (!isClosed()) {
                privacyFormOpen = false
                notifyPrivacyOptions()
                initializeIfConsentAllows()
            }
        }
    }

    private fun clearAd() {
        container?.removeAllViews()
        adView?.apply { adListener = object : AdListener() {}; pause(); destroy() }
        adView = null
        lastLoadFailed = false
        failedAtElapsedTime = 0L
        requestedWidthDp = 0
        requestedOrientation = 0
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        container?.removeOnLayoutChangeListener(layoutListener)
        clearAd()
        container = null
    }

    companion object {
        private const val RETRY_COOLDOWN_MS = 60_000L
    }
}
