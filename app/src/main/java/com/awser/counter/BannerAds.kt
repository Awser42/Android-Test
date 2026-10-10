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
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlin.math.ceil
import kotlin.math.floor

/** Plain footer text; SDK details appear only through the optional details action. */
data class BannerAdStatus(val message: String, val detail: String = "", val canRetry: Boolean = false)

/** UI-facing controller; host tests can replace it without touching either Google SDK. */
interface BannerAdController {
    val privacyOptionsRequired: Boolean
    val status: BannerAdStatus get() = BannerAdStatus("")
    fun setStatusListener(listener: (BannerAdStatus) -> Unit) { listener(status) }
    fun retry() = Unit
    fun attach(container: FrameLayout)
    fun start()
    fun resume()
    fun pause()
    fun destroy()
    fun showPrivacyOptions()
}

object BannerAds {
    var factory: ((Activity, (Boolean) -> Unit) -> BannerAdController)? = null

    fun create(activity: Activity, onPrivacyOptionsChanged: (Boolean) -> Unit = {}): BannerAdController {
        factory?.let { return it(activity, onPrivacyOptionsChanged) }
        // Host tests never initialize either SDK or make external ad/consent requests.
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

/** Elapsed time keeps the failure cooldown independent of changes to the device clock. */
internal class BannerRetryCooldown(private val now: () -> Long = { SystemClock.elapsedRealtime() }) {
    private var failedAt: Long? = null
    fun markFailed() { failedAt = now() }
    fun clear() { failedAt = null }
    fun remainingMs(): Long = failedAt?.let { (60_000L - (now() - it)).coerceIn(0L, 60_000L) } ?: 0L
}

/** Classify only what Google's returned domain/code or explicit message establishes. */
internal fun bannerFailureStatus(code: Int, domain: String, message: String): BannerAdStatus {
    val text = when {
        message.contains("lightweight version", ignoreCase = true) &&
            message.contains("Google Play services", ignoreCase = true) -> "Update Google Play services to show ads."
        domain == "com.google.android.gms.ads" && code == AdRequest.ERROR_CODE_NETWORK_ERROR -> "Ad connection failed. Check your internet."
        domain == "com.google.android.gms.ads" && code == AdRequest.ERROR_CODE_NO_FILL -> "No ad available right now."
        domain == "com.google.android.gms.ads" && code == AdRequest.ERROR_CODE_INVALID_REQUEST -> "Ad setup needs attention."
        else -> "Ad unavailable right now."
    }
    return BannerAdStatus(text, "Ad service: $domain ($code)\n${message.take(600)}", true)
}

internal fun consentFailureStatus(code: Int, message: String): BannerAdStatus {
    val text = when (code) {
        FormError.ErrorCode.INTERNET_ERROR -> "Ad privacy check needs an internet connection."
        FormError.ErrorCode.TIME_OUT -> "Ad privacy check timed out."
        FormError.ErrorCode.INVALID_OPERATION -> "Ad privacy setup needs attention."
        else -> "Ad privacy check unavailable right now."
    }
    return BannerAdStatus(text, "Privacy service ($code)\n${message.take(600)}", true)
}

/** SDK boundary lets controller regressions run offline without initializing Google services. */
internal interface BannerAdSdk {
    val canRequestAds: Boolean
    val privacyOptionsRequired: Boolean
    fun refreshConsent(success: () -> Unit, failure: (BannerAdStatus) -> Unit)
    fun showConsentForm(complete: (BannerAdStatus?) -> Unit)
    fun showPrivacyForm(complete: (BannerAdStatus?) -> Unit)
    fun initialize(complete: () -> Unit)
    fun createBanner(widthDp: Int): BannerAdHandle
}

internal interface BannerAdHandle {
    val view: View
    fun load(loaded: () -> Unit, failed: (BannerAdStatus) -> Unit)
    fun resume()
    fun pause()
    fun destroy()
}

private class GoogleBannerSdk(private val activity: Activity) : BannerAdSdk {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    override val canRequestAds get() = consent.canRequestAds()
    override val privacyOptionsRequired get() = consent.privacyOptionsRequirementStatus ==
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    override fun refreshConsent(success: () -> Unit, failure: (BannerAdStatus) -> Unit) {
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), { success() },
            { failure(consentFailureStatus(it.errorCode, it.message)) })
    }
    override fun showConsentForm(complete: (BannerAdStatus?) -> Unit) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
            complete(it?.let { error -> consentFailureStatus(error.errorCode, error.message) })
        }
    }
    override fun showPrivacyForm(complete: (BannerAdStatus?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            complete(it?.let { error -> consentFailureStatus(error.errorCode, error.message) })
        }
    }
    override fun initialize(complete: () -> Unit) { MobileAds.initialize(activity.applicationContext) { complete() } }
    override fun createBanner(widthDp: Int): BannerAdHandle {
        val banner = AdView(activity).apply {
            visibility = View.INVISIBLE
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        }
        return object : BannerAdHandle {
            override val view: View get() = banner
            override fun load(loaded: () -> Unit, failed: (BannerAdStatus) -> Unit) {
                banner.adListener = object : AdListener() {
                    override fun onAdLoaded() { loaded() }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        failed(bannerFailureStatus(error.code, error.domain, error.message))
                    }
                }
                banner.loadAd(AdRequest.Builder().build())
            }
            override fun resume() { banner.resume() }
            override fun pause() { banner.pause() }
            override fun destroy() { banner.adListener = object : AdListener() {}; banner.destroy() }
        }
    }
}

/** View and UMP calls run on the main thread. Counting never waits for an ad. */
internal class GoogleBannerAds(
    private val activity: Activity,
    private val onPrivacyOptionsChanged: (Boolean) -> Unit,
    private val sdk: BannerAdSdk = GoogleBannerSdk(activity),
    now: () -> Long = { SystemClock.elapsedRealtime() },
    private val executeInitialization: (() -> Unit) -> Unit = { work ->
        Thread({ work() }, "admob-initialize").apply { isDaemon = true; start() }
    },
) : BannerAdController {
    private enum class RetryStage { CONSENT, INITIALIZE, BANNER }

    private var container: FrameLayout? = null
    private var adView: BannerAdHandle? = null
    private var started = false
    @Volatile private var destroyed = false
    @Volatile private var resumed = false
    private var initializationStarted = false
    private var sdkReady = false
    private var consentRequestStarted = false
    private var consentInfoInFlight = false
    private var consentRequestComplete = false
    private var consentFormPending = false
    private var consentFormOpen = false
    private var requestedWidthDp = 0
    private var requestedOrientation = 0
    private var privacyFormOpen = false
    private var retryStage: RetryStage? = null
    private val retryCooldown = BannerRetryCooldown(now)
    private var failureStatus: BannerAdStatus? = null
    private var statusListener: ((BannerAdStatus) -> Unit)? = null
    override var status = BannerAdStatus("Checking ad privacy…")
        private set

    override val privacyOptionsRequired: Boolean
        get() = sdk.privacyOptionsRequired

    private fun isClosed() = destroyed || activity.isFinishing || activity.isDestroyed

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        requestBannerIfReady()
    }

    override fun setStatusListener(listener: (BannerAdStatus) -> Unit) {
        if (isClosed()) return
        statusListener = listener
        listener(status)
    }

    private fun publish(next: BannerAdStatus) {
        if (isClosed() || status == next) return
        status = next
        statusListener?.invoke(next)
    }

    private fun fail(stage: RetryStage, next: BannerAdStatus) {
        clearAd()
        retryStage = stage
        failureStatus = next
        retryCooldown.markFailed()
        publish(next)
    }

    private fun clearFailure() {
        retryStage = null
        failureStatus = null
        retryCooldown.clear()
    }

    override fun attach(container: FrameLayout) {
        if (isClosed()) return
        this.container?.removeOnLayoutChangeListener(layoutListener)
        // Palette changes reparent the existing banner without another request.
        this.container?.removeAllViews()
        this.container = container
        container.removeAllViews()
        container.minimumHeight = 0
        container.visibility = View.VISIBLE
        adView?.let { addBannerToSlot(container, it) }
        container.addOnLayoutChangeListener(layoutListener)
        container.post { requestBannerIfReady() }
    }

    override fun start() {
        if (started || isClosed()) return
        started = true
        // onCreate precedes onResume. Forms and SDK work belong to the visible counter.
        if (resumed) requestConsentRefresh()
    }

    private fun requestConsentRefresh() {
        if (isClosed() || !resumed || consentInfoInFlight || consentFormOpen || privacyFormOpen) return
        consentRequestStarted = true
        consentRequestComplete = false
        consentInfoInFlight = true
        consentFormPending = false
        publish(BannerAdStatus("Checking ad privacy…"))
        try {
            sdk.refreshConsent({
                if (isClosed()) return@refreshConsent
                consentInfoInFlight = false
                notifyPrivacyOptions()
                consentFormPending = true
                showPendingConsentForm()
            }, { error ->
                if (!isClosed()) {
                    consentInfoInFlight = false
                    consentRequestComplete = true
                    notifyPrivacyOptions()
                    // A failed refresh may still use valid consent retained earlier.
                    if (sdk.canRequestAds) initializeIfConsentAllows()
                    else fail(RetryStage.CONSENT, error)
                }
            })
        } catch (error: RuntimeException) {
            consentInfoInFlight = false
            fail(RetryStage.CONSENT, BannerAdStatus("Ad privacy check unavailable right now.", exceptionDetail(error), true))
        }
    }

    private fun showPendingConsentForm() {
        if (isClosed() || !resumed || !consentFormPending || consentFormOpen || privacyFormOpen) return
        consentFormPending = false
        consentFormOpen = true
        try {
            sdk.showConsentForm { error ->
                if (!isClosed()) {
                    consentFormOpen = false
                    consentRequestComplete = true
                    notifyPrivacyOptions()
                    if (sdk.canRequestAds) initializeIfConsentAllows()
                    else fail(RetryStage.CONSENT, error
                        ?: BannerAdStatus("Ads are waiting for privacy choices.", "Google's privacy service has not allowed an ad request yet.", true))
                }
            }
        } catch (error: RuntimeException) {
            consentFormOpen = false
            fail(RetryStage.CONSENT, BannerAdStatus("Ad privacy check unavailable right now.", exceptionDetail(error), true))
        }
    }

    private fun notifyPrivacyOptions() { onPrivacyOptionsChanged(privacyOptionsRequired) }

    private fun initializeIfConsentAllows() {
        if (isClosed() || !resumed || retryStage != null || !consentRequestComplete || privacyFormOpen ||
            consentFormOpen || !sdk.canRequestAds) return
        if (sdkReady) {
            requestBannerIfReady()
            return
        }
        if (initializationStarted) return
        // The full Ads SDK selects its bundled implementation when Play services are absent.
        // Consent and foreground checks still apply; SDK callbacks report actual failures.
        initializationStarted = true
        publish(BannerAdStatus("Starting ads…"))
        executeInitialization {
            try {
                if (!destroyed && resumed) {
                    sdk.initialize {
                        activity.runOnUiThread {
                            if (!isClosed()) {
                                sdkReady = true
                                requestBannerIfReady()
                            }
                        }
                    }
                } else activity.runOnUiThread {
                    if (!isClosed()) {
                        initializationStarted = false
                        if (resumed) initializeIfConsentAllows()
                    }
                }
            } catch (error: RuntimeException) {
                activity.runOnUiThread {
                    if (!isClosed()) {
                        initializationStarted = false
                        fail(RetryStage.INITIALIZE, BannerAdStatus("Ad service unavailable right now.", exceptionDetail(error), true))
                    }
                }
            }
        }
    }

    private fun requestBannerIfReady() {
        val slot = container ?: return
        if (isClosed() || !resumed || !sdkReady || retryStage != null || !consentRequestComplete ||
            privacyFormOpen || consentFormOpen || !sdk.canRequestAds) return
        val pixels = slot.width - slot.paddingLeft - slot.paddingRight
        if (pixels <= 0) return
        val widthDp = floor(pixels / activity.resources.displayMetrics.density.toDouble()).toInt()
        if (widthDp <= 0) return
        val orientation = activity.resources.configuration.orientation
        if (widthDp == requestedWidthDp && orientation == requestedOrientation) return
        clearAd()
        requestedWidthDp = widthDp
        requestedOrientation = orientation
        publish(BannerAdStatus("Ad loading…"))
        try {
            val banner = sdk.createBanner(widthDp)
            adView = banner
            // Attach before load as in Google's sample. A pending banner reserves its
            // adaptive height; failure removes it and leaves the separate status row.
            addBannerToSlot(slot, banner)
            banner.load({
                if (isClosed() || adView !== banner || privacyFormOpen || consentFormOpen || !sdk.canRequestAds) return@load
                val current = container ?: return@load
                banner.view.visibility = View.VISIBLE
                addBannerToSlot(current, banner)
                publish(BannerAdStatus(""))
                if (resumed) banner.resume() else banner.pause()
            }, { error ->
                if (!isClosed() && adView === banner) fail(RetryStage.BANNER, error)
            })
        } catch (error: RuntimeException) {
            fail(RetryStage.BANNER, BannerAdStatus("Ad unavailable right now.", exceptionDetail(error), true))
        }
    }

    private fun addBannerToSlot(slot: FrameLayout, banner: BannerAdHandle) {
        (banner.view.parent as? FrameLayout)?.removeView(banner.view)
        slot.removeAllViews()
        slot.addView(banner.view, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    }

    override fun retry() {
        if (isClosed() || !resumed || consentInfoInFlight || consentFormOpen || privacyFormOpen) return
        val stage = retryStage ?: return // Never reload a loaded ad through manual taps.
        val remaining = retryCooldown.remainingMs()
        if (remaining > 0) {
            val seconds = ceil(remaining / 1000.0).toInt()
            failureStatus?.let { publish(it.copy(message = "${it.message} Retry in ${seconds}s.")) }
            return
        }
        clearFailure()
        clearAd()
        if (stage == RetryStage.CONSENT || !consentRequestComplete || !sdk.canRequestAds) requestConsentRefresh()
        else initializeIfConsentAllows()
    }

    override fun resume() {
        if (isClosed()) return
        resumed = true
        adView?.resume()
        if (!started || retryStage != null) return
        if (consentFormPending) showPendingConsentForm()
        else if (!consentRequestStarted) requestConsentRefresh()
        else if (consentRequestComplete) initializeIfConsentAllows()
    }

    override fun pause() {
        resumed = false
        adView?.pause()
    }

    override fun showPrivacyOptions() {
        if (isClosed() || !resumed || privacyFormOpen || consentFormOpen || !privacyOptionsRequired) return
        privacyFormOpen = true
        clearFailure()
        clearAd()
        publish(BannerAdStatus("Ad privacy options…"))
        try {
            sdk.showPrivacyForm { error ->
                if (!isClosed()) {
                    privacyFormOpen = false
                    consentRequestComplete = true
                    notifyPrivacyOptions()
                    if (sdk.canRequestAds) initializeIfConsentAllows()
                    else fail(RetryStage.CONSENT, error
                        ?: BannerAdStatus("Ads are waiting for privacy choices.", "Google's privacy service has not allowed an ad request yet.", true))
                }
            }
        } catch (error: RuntimeException) {
            privacyFormOpen = false
            fail(RetryStage.CONSENT, BannerAdStatus("Ad privacy options unavailable right now.", exceptionDetail(error), true))
        }
    }

    private fun clearAd() {
        container?.removeAllViews()
        adView?.apply { pause(); destroy() }
        adView = null
        requestedWidthDp = 0
        requestedOrientation = 0
    }

    private fun exceptionDetail(error: RuntimeException) = "${error.javaClass.simpleName}: ${error.message.orEmpty().take(600)}"

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        statusListener = null
        container?.removeOnLayoutChangeListener(layoutListener)
        clearAd()
        container = null
    }
}
