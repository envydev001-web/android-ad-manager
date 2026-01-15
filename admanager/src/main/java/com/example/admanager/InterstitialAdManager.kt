package com.example.admanager

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

var isShowingAd = false

/* ---------------- Config ---------------- */
data class AdConfig(
    val loadTimeoutMs: Long = 20000L,
    var showIntervalMs: Long = 15000L,
    var clickThreshold: Int = 3,
    val adLoadDelayMs: Long = 550L,
    val httpTimeoutMillis: Int = 30000
) {
    companion object {
        @Volatile
        private var instance: AdConfig? = null

        fun getInstance(): AdConfig {
            return instance ?: synchronized(this) {
                instance ?: AdConfig().also { instance = it }
            }
        }

        fun setAdShowTimer(value: Long) {
            instance?.showIntervalMs = value
        }

        fun updatethreshold(value: Int) {
            instance?.clickThreshold = value
        }
    }
}


private fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        activeNetwork != null && activeNetwork.isConnected
    } catch (e: Exception) {
        false
    }
}

/* ---------------- Event listener ---------------- */
interface AdEventListener {
    fun onAdLoaded() {}
    fun onAdShowed() {}
    fun onAdDismissed() {}
    fun onAdFailed(error: String?) {}
}

/* ---------------- State ---------------- */
sealed class AdState {
    object Idle : AdState()
    object Loading : AdState()
    data class Loaded(val ad: InterstitialAd) : AdState()
    object Showing : AdState()
    object Failed : AdState()
}

/* ---------------- Manager ---------------- */
class InterstitialAdManager private constructor(
    private val context: Context,
    private val config: AdConfig = AdConfig.getInstance()
) {
    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val TEST_AD_ID = "ca-app-pub-3940256099942544/1033173712"

        @Volatile
        private var instance: InterstitialAdManager? = null

        fun getInstance(
            context: Context,
            config: AdConfig = AdConfig.getInstance()
        ): InterstitialAdManager {
            return instance ?: synchronized(this) {
                instance ?: InterstitialAdManager(
                    context.applicationContext,
                    config
                ).also { instance = it }
            }
        }

        fun destroyInstance() {
            instance?.cleanup()
            instance = null
        }

    }

    // Handler + runnables (we only cancel our specific runnables)
    private val handler = Handler(Looper.getMainLooper())
    private var loadTimeoutRunnable: Runnable? = null
    private var delayedShowRunnable: Runnable? = null

    // UI
    private var loadingDialog: AlertDialog? = null

    // Counters / state
    private val clickCount = AtomicInteger(2)
    public var lastAdShownTimeStamp = AtomicLong(14000L)
    private var adState: AdState = AdState.Idle
    private var currentAdId: String? = null

    // Two listeners so load lifecycle and show lifecycle don't fight
    private var loadListener: AdEventListener? = null
    private var showListener: AdEventListener? = null

    /* ---------------- FullScreenCallback ---------------- */
    /* ---------------- FullScreenCallback ---------------- */
    private val fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            logDebug { "➡ onAdShowedFullScreenContent()" }
            adState = AdState.Showing
            isShowingAd = true   // ✅ mark ad as showing
            clickCount.incrementAndGet()
            showListener?.onAdShowed()
        }

        override fun onAdDismissedFullScreenContent() {
            logDebug { "➡ onAdDismissedFullScreenContent()" }
            adState = AdState.Idle
            isShowingAd = false  // ✅ reset flag when dismissed
            resetCounters()
            showListener?.onAdDismissed()
            // clear only the show listener (load listener already used during load)
            showListener = null
        }

        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            logDebug { "➡ onAdFailedToShowFullScreenContent(): ${adError.message}" }
            adState = AdState.Failed
            isShowingAd = false  // ✅ also reset here
            showListener?.onAdFailed("Show failed: ${adError.message}")
            showListener = null
            clickCount.incrementAndGet()
        }
    }


    /* ---------------- Public API ---------------- */

    /**
     * Load an interstitial ad.
     *
     * @param activity used by the SDK for load lifecycle (keep alive).
     * @param adId ad unit id
     * @param showAd if true, attempt to show immediately after load (if activity valid)
     * @param isSplashAd if true, timeout will be enforced (fast fail)
     * @param isTimedAndClickBasedAd control for click/timer gating (keeps existing behaviour)
     * @param listener load lifecycle listener (used for loading events)
     * @param isPremiumUser skip if premium
     * @param showProgress show a progress dialog while loading
     */
    fun loadInterstitialAd(
        activity: Activity,
        adId: String = TEST_AD_ID,
        showAd: Boolean = false,
        isSplashAd: Boolean = false,
        isTimedAndClickBasedAd: Boolean = true,
        listener: AdEventListener? = null,
        isPremiumUser: Boolean = false,
        adCanRequest: Boolean = true,
        showProgress: Boolean = false
    ) {
        logDebug { "▶ loadInterstitialAd(adId=$adId, showAd=$showAd, splash=$isSplashAd)" }
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "❌ No internet connection")
            listener?.onAdFailed("No internet connection")
            return
        }
        // Always set the load listener: this listener is for loading UI (shimmer/progress).
        loadListener = listener

        // If caller wants to auto-show after loading and they passed a listener,
        // we also set showListener so that show events are delivered to same listener.
        if (showAd && listener != null) {
            showListener = listener
        }

        if (isPremiumUser || !adCanRequest) {
            logDebug { "🚫 Premium user → No ads" }
            loadListener?.onAdFailed("Premium user – no ads")
            // if we also set showListener above, notify showListener as well (but avoid duplicate if same object)
            if (showAd && showListener !== loadListener) showListener?.onAdFailed("Premium user – no ads")
            clearLoadTimeout()
            return
        }


        // gating logic (clicks/timer)
        if (!shouldProcessAdRequest(adId, isTimedAndClickBasedAd, isSplashAd)) {
            logDebug { "🚫 Ad request skipped (timer/click not satisfied)" }
            clickCount.incrementAndGet()
            loadListener?.onAdFailed("Ad request skipped (timer/click not satisfied)")
            if (showAd && showListener !== loadListener) showListener?.onAdFailed("Ad request skipped (timer/click not satisfied)")
            return
        }

        if (isLoadingInProgress()) {
            logDebug { "⏳ Ad load already in progress → ignoring new request" }
            return
        }

        if (isAdAlreadyLoaded() && !isSplashAd) {
            logDebug { "✅ Ad already loaded → handling existing ad" }
            // Notify load listener that ad is available
            loadListener?.onAdLoaded()
            // If requested to show, schedule showing after small delay (to emulate previous adLoadDelayMs)
            if (showAd) {
                delayedShowRunnable?.let { handler.removeCallbacks(it) }
                delayedShowRunnable = Runnable {
                    showAvailableAd(activity, isPremiumUser, showAd, showListener)
                }.also { handler.postDelayed(it, config.adLoadDelayMs) }
            }
            return
        }

        // begin loading
        adState = AdState.Loading
        currentAdId = adId
        if (showProgress) showLoadingDialog(activity)

        // schedule timeout only for splash ads (preloads should not be forcibly failed)
        scheduleLoadTimeout(isSplashAd)

        val adRequest = createAdRequest()
        InterstitialAd.load(
            activity,
            adId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    logDebug { "❌ onAdFailedToLoad(): ${adError.message}" }
                    dismissLoadingDialog()
                    clearLoadTimeout()

                    // allow re-try quickly by moving back to Idle rather than staying in Failed
                    adState = AdState.Idle

                    // notify load listener
                    loadListener?.onAdFailed(adError.message)
                    // if this load was requested with showAd, notify show listener as well (if different)
                    if (showAd && showListener != null && showListener !== loadListener) {
                        showListener?.onAdFailed(adError.message)
                        showListener = null
                    }
                    loadListener = null
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    logDebug { "✅ onAdLoaded()" }

                    clearLoadTimeout()

                    // set loaded state and attach fullscreen callback
                    adState = AdState.Loaded(ad)
                    ad.fullScreenContentCallback = fullScreenContentCallback

                    // notify load listener (used for loading UI)
                    loadListener?.onAdLoaded()
                    // keep loadListener cleared once used
                    loadListener = null

                    // If caller requested to show immediately, attempt to show now.
                    // showAvailableAd will validate activity and premium user etc.
                    if (showAd) {
                        showAvailableAd(activity, isPremiumUser, true, showListener)
                        // do NOT clear showListener here — it will be cleared by fullscreen callbacks after show/dismiss
                    }
                    dismissLoadingDialog()
                }
            }
        )
    }

    /**
     * Show the loaded ad (no loading happens here).
     * - If ad is loaded => will attempt to show and showListener will receive show/dismiss callbacks.
     * - If ad is NOT loaded => showListener.onAdFailed(...) is invoked immediately.
     *
     * Note: this method clears the previous showListener first to avoid duplicates.
     */
    fun showAvailableAd(
        activity: Activity,
        isPremiumUser: Boolean,
        showAd: Boolean,
        listener: AdEventListener? = null
    ): Boolean {
        logDebug { "▶ showAvailableAd(showAd=$showAd)" }
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "❌ No internet connection")
            listener?.onAdFailed("No internet connection")
            false
        }
        // Clear previous show listener always (prevent duplicates). Then set new one if provided.
        clearShowListener()
        if (listener != null) showListener = listener

        return when (val currentState = adState) {
            is AdState.Loaded -> {
                if (isPremiumUser) {
                    logDebug { "🚫 Premium user – Ad skipped (ad kept loaded)" }
                    showListener?.onAdFailed("Premium user – Ad skipped")
                    showListener = null
                    false
                } else if (activity.isFinishing || activity.isDestroyed) {
                    logDebug { "🚫 Activity not valid – keeping ad loaded for later" }
                    showListener?.onAdFailed("Activity not valid – try again later")
                    showListener = null
                    false
                } else {
                    if (showAd) {
                        logDebug { "🎬 Showing interstitial ad" }
                        // ✅ Apply fullscreen-safe window flags before showing ad
                        currentState.ad.show(activity)
                        true
                    } else {
                        logDebug { "ℹ️ Ad is loaded but showAd=false → not showing" }
                        showListener?.onAdFailed("Ad available but not shown (showAd=false)")
                        showListener = null
                        false
                    }
                }
            }

            else -> {
                logDebug { "🚫 No ad available to show" }
                showListener?.onAdFailed("No ad available")
                showListener = null
                false
            }
        }
    }

    /**
     * Show the loaded ad with optional progress dialog
     * - If ad is loaded => will attempt to show and showListener will receive show/dismiss callbacks.
     * - If ad is NOT loaded => showListener.onAdFailed(...) is invoked immediately.
     *
     * @param activity The activity to show the ad in
     * @param isPremiumUser Whether user is premium (will skip ad)
     * @param showProgress Whether to show a progress dialog while preparing the ad
     * @param progressMessage Custom message for progress dialog (optional)
     * @param listener Listener for show events
     * @return Boolean indicating if ad show was attempted
     */
    fun showAvailableAd(
        activity: Activity,
        isPremiumUser: Boolean,
        showAd: Boolean = true,
        showProgress: Boolean = false,
        listener: AdEventListener? = null
    ): Boolean {
        logDebug { "▶ showAvailableAd(showAd=$showAd, showProgress=$showProgress)" }
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "❌ No internet connection")
            listener?.onAdFailed("No internet connection")
            false
        }
        // Clear previous show listener always (prevent duplicates). Then set new one if provided.
        clearShowListener()
        if (listener != null) showListener = listener

        // Show progress dialog if requested
        if (showProgress) {
            showLoadingDialog(activity)
        }

        return when (val currentState = adState) {
            is AdState.Loaded -> {
                if (isPremiumUser) {
                    logDebug { "🚫 Premium user – Ad skipped (ad kept loaded)" }
                    dismissLoadingDialog()
                    showListener?.onAdFailed("Premium user – Ad skipped")
                    showListener = null
                    false
                } else if (activity.isFinishing || activity.isDestroyed) {
                    logDebug { "🚫 Activity not valid – keeping ad loaded for later" }
                    dismissLoadingDialog()
                    showListener?.onAdFailed("Activity not valid – try again later")
                    showListener = null
                    false
                } else {
                    if (showAd) {
                        logDebug { "🎬 Showing interstitial ad" }

                        // Schedule dialog dismissal right before showing ad
                        handler.postDelayed({

                            currentState.ad.show(activity)
                            dismissLoadingDialog()
                        }, 300) // Small delay to ensure smooth transition

                        true
                    } else {
                        logDebug { "ℹ️ Ad is loaded but showAd=false → not showing" }
                        dismissLoadingDialog()
                        showListener?.onAdFailed("Ad available but not shown (showAd=false)")
                        showListener = null
                        false
                    }
                }
            }

            else -> {
                logDebug { "🚫 No ad available to show" }
                dismissLoadingDialog()
                showListener?.onAdFailed("No ad available")
                showListener = null
                false
            }
        }
    }

    /* ---------------- Convenience wrappers (old API preserved) ---------------- */

    fun loadSplashAd(
        activity: Activity,
        adId: String,
        showAd: Boolean,
        eventListener: AdEventListener? = null,
        isPremiumUser: Boolean = false,
        showProgress: Boolean = true
    ) {
        loadInterstitialAd(
            activity = activity,
            adId = adId,
            showAd = showAd,
            isSplashAd = true,
            isTimedAndClickBasedAd = false,
            listener = eventListener,
            isPremiumUser = isPremiumUser,
            showProgress = showProgress
        )
    }

    fun loadRegularAd(
        activity: Activity,
        adId: String,
        showAd: Boolean = false,
        adCanRequest: Boolean = true,
        eventListener: AdEventListener? = null,
        isPremiumUser: Boolean = false,
        showProgress: Boolean = false
    ) {

        loadInterstitialAd(
            activity = activity,
            adId = adId,
            showAd = showAd,
            isSplashAd = false,
            isTimedAndClickBasedAd = true,
            listener = eventListener,
            isPremiumUser = isPremiumUser,
            adCanRequest,
            showProgress = showProgress
        )
    }

    fun loadUnrestrictedAd(
        activity: Activity,
        adId: String,
        showAd: Boolean = false,
        eventListener: AdEventListener? = null,
        isPremiumUser: Boolean = false,
        showProgress: Boolean = false
    ) {
        loadInterstitialAd(
            activity = activity,
            adId = adId,
            showAd = showAd,
            isSplashAd = false,
            isTimedAndClickBasedAd = false,
            listener = eventListener,
            isPremiumUser = isPremiumUser,
            showProgress = showProgress
        )
    }

    /* ---------------- Utilities / state helpers ---------------- */

    fun isAdAvailable(): Boolean = adState is AdState.Loaded
    fun isLoadingInProgress(): Boolean = adState == AdState.Loading
    fun isAdAlreadyLoaded(): Boolean = adState is AdState.Loaded

    fun resetCounters() {
        logDebug { "🔄 resetCounters()" }
        clickCount.set(0)
        lastAdShownTimeStamp.set(System.currentTimeMillis())
    }

    fun setCounters(value: Int, timeDiffFromRemote: Long) {
        logDebug { "▶ shouldShowBasedOnTimerAndClicks(clickCount=$value, threshold=${config.clickThreshold}), timer=${timeDiffFromRemote}" }
        clickCount.set(value)
        lastAdShownTimeStamp.set(timeDiffFromRemote)
    }

    fun clearListener() {
        logDebug { "▶ clearListener()" }
        loadListener = null
        showListener = null
    }

    /** clear only the show listener */
    private fun clearShowListener() {
        showListener = null
    }

    /* ---------------- Internal helpers ---------------- */

    private fun shouldProcessAdRequest(
        adId: String,
        isTimedAd: Boolean,
        isSplashAd: Boolean
    ): Boolean {
        logDebug { "▶ shouldProcessAdRequest(splash=$isSplashAd, timed=$isTimedAd)" }
        if (isSplashAd) return true
        return shouldShowBasedOnTimerAndClicks(isTimedAd) && !isLoadingInProgress()
    }

    private fun shouldShowBasedOnTimerAndClicks(isTimerAd: Boolean): Boolean {
        logDebug {
            "▶ shouldShowBasedOnTimerAndClicks(clicks=${clickCount.get()}, threshold=${config.clickThreshold}, " +
                    "elapsed=${System.currentTimeMillis() - lastAdShownTimeStamp.get()}ms, minInterval=${config.showIntervalMs})"
        }
        if (!isTimerAd) return true
        return clickCount.get() >= config.clickThreshold &&
                System.currentTimeMillis() - lastAdShownTimeStamp.get() > config.showIntervalMs
    }

    private fun createAdRequest(): AdRequest {
        logDebug { "▶ createAdRequest()" }
        return AdRequest.Builder().setHttpTimeoutMillis(config.httpTimeoutMillis).build()
    }

    /* ---------------- Loading Dialog Handling ---------------- */
    // replace your AlertDialog field with this:
    private var progressDialog: ProgressDialog? = null

    private fun showLoadingDialog(activity: Activity) {
        handler.post {
            try {
                // safety checks
                if (activity.isFinishing || activity.isDestroyed) return@post
                if (progressDialog?.isShowing == true) return@post

                progressDialog = ProgressDialog(activity).apply {
                    show()
                    setContentView(R.layout.ad_loading_dialog)
                    setCancelable(false)
                    window?.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                    )
                    window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                }

                // Only mark true if dialog is actually showing
                if (progressDialog?.isShowing == true) {
                    isShowingAd = true
                    logDebug { "📺 ProgressDialog shown — isShowingAd=true" }
                } else {
                    // fallback: ensure we don't leave the flag true by mistake
                    isShowingAd = false
                    logDebug { "⚠️ ProgressDialog failed to show" }
                }
            } catch (e: Exception) {
                logDebug { "Failed to show ProgressDialog: ${e.message}" }
                progressDialog = null
                isShowingAd = false
            }
        }
    }

    private fun dismissLoadingDialog(delayMillis: Long = 500L) {
        handler.postDelayed({
            try {
                if (progressDialog?.isShowing == true) {
                    progressDialog?.dismiss()
                }
            } catch (e: Exception) {
                logDebug { "Failed to dismiss ProgressDialog: ${e.message}" }
            } finally {
                progressDialog = null
                isShowingAd = false
                logDebug { "❌ ProgressDialog dismissed (after ${delayMillis}ms) — isShowingAd=false" }
            }
        }, delayMillis)
    }


    /**
     * Schedule a timeout for loading. Only scheduled for splash ads (fast fail),
     * preloads will not be forcibly failed by timeout.
     */
    private fun scheduleLoadTimeout(isSplashAd: Boolean) {
        clearLoadTimeout()
        if (!isSplashAd) return

        loadTimeoutRunnable = Runnable {
            if (adState == AdState.Loading) {
                adState = AdState.Idle
                logDebug { "⏳ Load timeout after ${config.loadTimeoutMs}ms" }
                loadListener?.onAdFailed("Load timeout after ${config.loadTimeoutMs}ms")
                // if we were planning to show after load, notify show listener too (only if different)
                if (showListener != null && showListener !== loadListener) {
                    showListener?.onAdFailed("Load timeout")
                    showListener = null
                }
                loadListener = null
                dismissLoadingDialog()
            }
        }
        handler.postDelayed(loadTimeoutRunnable!!, config.loadTimeoutMs)
    }

    private fun clearLoadTimeout() {
        loadTimeoutRunnable?.let { handler.removeCallbacks(it) }
        loadTimeoutRunnable = null
    }

    private fun cancelDelayedShow() {
        delayedShowRunnable?.let { handler.removeCallbacks(it) }
        delayedShowRunnable = null
    }

    fun cleanup() {
        logDebug { "🧹 cleanup()" }
        clearLoadTimeout()
        cancelDelayedShow()
        dismissLoadingDialog()
        loadListener = null
        showListener = null
        currentAdId = null
        clickCount.set(0)
        lastAdShownTimeStamp.set(0L)
        adState = AdState.Idle
    }

    private inline fun logDebug(message: () -> String) {
        // keep debug logs only in debug builds
        try {
            if (BuildConfig.DEBUG) Log.d(TAG, message())
        } catch (_: Throwable) {
            // swallow if BuildConfig not available in library module
            Log.d(TAG, message())
        }
    }
}
