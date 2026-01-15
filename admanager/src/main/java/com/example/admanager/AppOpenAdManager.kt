package com.example.admanager

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import java.util.Date

class AppOpenAdManager(
    private val adUnitId: String
) {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    var isShowingAd = false
    private var loadTime: Long = 0
    private val LOG_TAG = "AppOpenAdManager"

    /**
     * Load the App Open Ad
     */
    fun loadAd(context: Context) {
        if (isLoadingAd || isAdAvailable()) return

        isLoadingAd = true
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            context,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(LOG_TAG, "Ad was loaded.")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                }

                override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                    Log.d(LOG_TAG, "Ad failed to load: ${loadAdError.message}")
                    isLoadingAd = false
                }
            }
        )
    }

    /**
     * Show the App Open Ad if available
     */
    fun showAdIfAvailable(activity: Activity, onShowAdCompleteListener: OnShowAdCompleteListener) {

        if (isShowingAd) {
            Log.d(LOG_TAG, "The app open ad is already showing.")
            return
        }

        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.ad_loading_dialog)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
        }
        dialog.setCancelable(false)

        if (!isAdAvailable()) {
            Log.d(LOG_TAG, "Ad is not ready yet.")
            onShowAdCompleteListener.onShowAdComplete()
            loadAd(activity)
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(LOG_TAG, "Ad dismissed fullscreen content.")
                appOpenAd = null
                isShowingAd = false
                safeDismissDialog(activity, dialog)
                onShowAdCompleteListener.onShowAdComplete()
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.d(LOG_TAG, "Failed to show ad: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                safeDismissDialog(activity, dialog)
                onShowAdCompleteListener.onShowAdComplete()
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(LOG_TAG, "Ad showed fullscreen content.")
            }
        }

        isShowingAd = true
        if (!activity.isFinishing) dialog.show()
        appOpenAd?.show(activity)
    }

    /**
     * Check if ad is available and not expired
     */
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    /**
     * Check if ad load time is less than given hours
     */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference: Long = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /**
     * Safe dialog dismiss to avoid Window leaks
     */
    private fun safeDismissDialog(activity: Activity, dialog: Dialog) {
        try {
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    if (dialog.isShowing && dialog.window?.decorView?.windowToken != null) {
                        dialog.dismiss()
                    }
                }
            }
        } catch (ex: Exception) {
            Log.d(LOG_TAG, "Error dismissing dialog: ${ex.message}")
        }
    }

    /**
     * Listener for ad show completion
     */
    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }
}
