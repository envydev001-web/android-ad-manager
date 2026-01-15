package com.example.admanager

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.*

enum class BannerType {
    BANNER, // 320 × 50//SMALL BANNER
    COLLAPSIBLE, //DEVICE DEPENDANT  ANDROID VERSION GREATER THAN 11
    ADAPTIVE,//DEVICE DEPENDANT
    MEDIUM_RECTANGLE,    // 300 × 250
    LARGE_BANNER,        // 320 × 100
    FULL_BANNER,         // 468 × 60
    LEADERBOARD          // 728 × 90
}

interface BannerAdListener {
    fun onAdLoaded() {}
    fun onAdFailed(error: String?) {}
    fun onAdClicked() {}
    fun onAdImpression() {}
}

class BannerAdManager(
    private val isPremium: Boolean = false
) {
    private val TAG = "BannerAdManager"
    private var adView: AdView? = null

    /**
     * Call MobileAds.initialize(context) once in Application.onCreate() before using this class
     * MobileAds.initialize(context) { }
     */

    fun loadBannerAd(
        activity: Activity,
        adUnitId: String,
        adContainer: FrameLayout,
        bannerType: BannerType = BannerType.ADAPTIVE,
        canRequestBannerAd: Boolean = true,
        listener: BannerAdListener? = null
    ) {
        if (isPremium || !canRequestBannerAd) {
            Log.d(TAG, "Premium user → hiding banner ads")
            adContainer.visibility = View.GONE
            return
        }

//        // If container has no width yet, wait for layout pass
//        if (bannerType == BannerType.ADAPTIVE && adContainer.width == 0) {
//            // Post to next frame when view has been measured
//            adContainer.post {
//                // re-call after layout
//                loadBannerAd(activity, adUnitId, adContainer, bannerType, canRequestBannerAd, listener)
//            }
//            return
//        }

        // Destroy old ad if present
        adView?.let {
            adView?.destroy()
            adView = null

        }

        adView = AdView(activity).apply {
            // IMPORTANT: use setAdSize(...) (AdView.setAdSize) not a Kotlin property assignment
            setAdSize(resolveAdSize(activity, adContainer, bannerType))
            this.adUnitId = adUnitId

            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner ${bannerType.name} loaded ✅")
                    adContainer.visibility = View.VISIBLE
                    listener?.onAdLoaded()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Banner Ad failed ❌: ${adError.message}")
                    adContainer.visibility = View.GONE
                    listener?.onAdFailed(adError.message)
                }

                override fun onAdClicked() {
                    listener?.onAdClicked()
                }

                override fun onAdImpression() {
                    listener?.onAdImpression()
                }
            }
        }

        adContainer.removeAllViews()
        adContainer.addView(adView)
        adContainer.visibility = View.GONE

        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    /** Pick ad size based on banner type */
    private fun resolveAdSize(
        activity: Activity,
        container: ViewGroup,
        bannerType: BannerType
    ): AdSize {
        return when (bannerType) {
            BannerType.ADAPTIVE -> getAdaptiveBannerSize(activity, container)
            BannerType.MEDIUM_RECTANGLE -> AdSize.MEDIUM_RECTANGLE // 300×250
            BannerType.BANNER -> AdSize.BANNER // 300×250
            BannerType.LARGE_BANNER -> AdSize.LARGE_BANNER         // 320×100
            BannerType.FULL_BANNER -> AdSize.FULL_BANNER           // 468×60
            BannerType.LEADERBOARD -> AdSize.LEADERBOARD           // 728×90
            BannerType.COLLAPSIBLE -> {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) { // Android 11+
                    getAdaptiveBannerSize(activity, container)
                } else {
                    // Fallback for Android 10 and below
                    AdSize.BANNER
                }
            }        // DEVICE DEPENDENT
        }
    }

    /** Adaptive banner size auto-detect */
    private fun getAdaptiveBannerSize(activity: Activity, container: ViewGroup): AdSize {
        val display = activity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)

        val density = outMetrics.density
        var adWidthPixels = container.width.toFloat()
        if (adWidthPixels == 0f) {
            // fallback to full screen width in pixels
            adWidthPixels = outMetrics.widthPixels.toFloat()
        }

        val adWidth = (adWidthPixels / density).toInt()
        // ensure >= 320 to avoid extremely small widths
        val finalWidth = if (adWidth <= 0) (outMetrics.widthPixels / density).toInt() else adWidth
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, finalWidth)
    }


}
