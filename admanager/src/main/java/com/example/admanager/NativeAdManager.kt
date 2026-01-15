package com.example.admanager

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.view.isEmpty
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import java.lang.ref.WeakReference

interface NativeAdListener {
    fun onAdLoaded() {}
    fun onAdFailed(error: String?) {}
    fun onAdClicked() {}
    fun onAdClosed() {}
}

class NativeAdManager private constructor(
    private val context: Context,
    private var isPremium: Boolean
) {

    companion object {
        private const val TAG = "NativeAdManager"

        @Volatile
        private var instance: NativeAdManager? = null

        fun getInstance(context: Context, isPremium: Boolean = false): NativeAdManager {
            return instance?.apply { this.isPremium = isPremium } ?: synchronized(this) {
                instance ?: NativeAdManager(context.applicationContext, isPremium).also {
                    instance = it
                }
            }
        }

        fun destroyInstance() {
            instance?.destroy()
            instance = null
        }
    }

    private var nativeAd: NativeAd? = null
    private var adListener: NativeAdListener? = null
    private var isLoading = false
    private var isAdInflated = false

    private var currentAdUnit: String? = null

    private var containerRef: WeakReference<FrameLayout>? = null
    private var jsonAd: WeakReference<JsonAd>? = null
    private var shimmerRef: WeakReference<ShimmerFrameLayout>? = null
    private var layoutRes_: Int? = null

    fun setAdListener(listener: NativeAdListener) {
        this.adListener = listener
    }

    /**
     * Check network availability
     */
    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Register container for this screen — must not be null.
     */
    fun registerContainer(
        adtoshow: JsonAd,
        container: FrameLayout?,
        shimmer: ShimmerFrameLayout?,
        layoutRes: Int,
        canRequestNativeAd: Boolean = true
    ) {
        if (container == null || layoutRes <= 0) {
            Log.w(TAG, "No valid container yet — will store ad for later inflation")
            containerRef = null
            shimmerRef = null
            layoutRes_ = null
            return
        }


        containerRef = WeakReference(container)
        jsonAd = WeakReference(adtoshow)
        shimmerRef = WeakReference(shimmer)
        layoutRes_ = layoutRes

        if (isPremium || !isNetworkAvailable()) {
            // Premium users or no internet: hide container and shimmer
            container.visibility = View.GONE
            shimmer?.stopShimmer()
            shimmer?.visibility = View.GONE
            return
        }
        if (!canRequestNativeAd) {
            // Premium users or no internet: hide container and shimmer
            container.visibility = View.GONE
            shimmer?.stopShimmer()
            shimmer?.visibility = View.GONE
            return
        }



        if (nativeAd != null && !isAdInflated) {
            Log.d(TAG, "Ad already loaded, inflating into registered container")
            shimmer?.stopShimmer()
            shimmer?.visibility = View.GONE
            inflateIfPossible()
        } else {
            // Ad not loaded yet
            if (container.childCount == 0) {
                shimmer?.visibility = View.VISIBLE
                shimmer?.startShimmer()
                Log.d(TAG, "No view in container — shimmer started for loading")
            } else {
                shimmer?.stopShimmer()
                shimmer?.visibility = View.GONE
                Log.d(TAG, "Container already has content — shimmer not started")
            }
        }
    }

    fun preloadAd(adUnitId: String, canRequestNativeAd: Boolean = true) {
        if (isPremium || !isNetworkAvailable()) {
            Log.d(TAG, "Premium user or no network — skipping preload")
            hideContainer()
            return
        }
        if (!canRequestNativeAd) {
            Log.d(TAG, "Premium user or no network — skipping preload")
            hideContainer()
            return
        }

        if (isLoading) {
            Log.d(TAG, "Already loading an ad — skipping preload")
            return
        }

        if (nativeAd != null) {
            Log.d(TAG, "Ad already preloaded — skipping preload")
            return
        }

        Log.d(TAG, "Preloading native ad: $adUnitId")
        currentAdUnit = adUnitId
        isLoading = true

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                Log.d(TAG, "Native ad preloaded successfully")
                isLoading = false
                nativeAd?.destroy()
                nativeAd = ad
                isAdInflated = false
                adListener?.onAdLoaded()
                inflateIfPossible()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Failed to preload native ad: ${error.message}")
                    isLoading = false
                    adListener?.onAdFailed(error.message)
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun loadAndShowAd(adUnitId: String, canRequestNativeAd: Boolean = true) {
        val container = containerRef?.get()
        val shimmer = shimmerRef?.get()
        val layout = layoutRes_

        if (container == null || layout == null || isPremium || !isNetworkAvailable()) {
            Log.d(TAG, "Premium user or no network — hiding container")
            hideContainer()
            return
        }


        if (!canRequestNativeAd) {
            Log.d(TAG, "$canRequestNativeAd — hiding container")
            hideContainer()
            return
        }

        if (isLoading) {
            Log.d(TAG, "Ad request already in progress — skipping")
            return
        }

        if (nativeAd != null && !isAdInflated) {
            Log.d(TAG, "Ad already loaded, inflating to active view")
            inflateIfPossible()
            return
        }

        currentAdUnit = adUnitId
        isLoading = true

        if (container.childCount == 0) {
            shimmer?.startShimmer()
            shimmer?.visibility = View.VISIBLE
        } else {
            shimmer?.stopShimmer()
            shimmer?.visibility = View.GONE
        }

        container.visibility = View.VISIBLE
        Log.d(TAG, "Requesting native ad: $adUnitId")

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                Log.d(TAG, "Native ad loaded successfully")
                isLoading = false
                nativeAd?.destroy()
                nativeAd = ad
                isAdInflated = false
                adListener?.onAdLoaded()
                inflateIfPossible()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Native ad failed to load: ${error.message}")
                    isLoading = false
                    hideContainer()
                    adListener?.onAdFailed(error.message)
                }

                override fun onAdClicked() {
                    adListener?.onAdClicked()
                }

                override fun onAdClosed() {
                    adListener?.onAdClosed()
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun showPreloadedAd() {
        if (isPremium || !isNetworkAvailable()) {
            Log.d(TAG, "Premium user or no network — skipping ad display")
            hideContainer()
            return
        }

        val ad = nativeAd
        val container = containerRef?.get()
        val layout = layoutRes_

        if (ad == null || container == null || layout == null || isAdInflated) {
            return
        }

        Log.d(TAG, "Showing preloaded ad in active container")
        inflateIfPossible()
    }

    private fun inflateIfPossible() {
        val ad = nativeAd ?: return
        val container = containerRef?.get() ?: return
        val shimmer = shimmerRef?.get()
        val layout = layoutRes_ ?: return

        if (isPremium || !isNetworkAvailable()) {
            hideContainer()
            return
        }
        if (isAdInflated) {
            return
        }
        if (!container.isEmpty()) {
            shimmer?.stopShimmer()
            shimmer?.visibility = View.GONE

        }

        Log.d(TAG, "Inflating native ad layout")
        shimmer?.stopShimmer()
        shimmer?.visibility = View.GONE

        val inflater = LayoutInflater.from(context)
        val adView = inflater.inflate(layout, container, false) as NativeAdView

        populateAdView( ad, adView)

        container.removeAllViews()
        container.addView(adView)
        container.visibility = View.VISIBLE
        isAdInflated = true
    }

    private fun hideContainer() {
        shimmerRef?.get()?.apply {
            stopShimmer()
            visibility = View.GONE
        }
        containerRef?.get()?.visibility = View.GONE
    }

    private fun populateAdView( nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById<TextView?>(R.id.ad_headline)?.apply {
            text = nativeAd.headline
        }
        adView.findViewById<TextView?>(R.id.ad_notification_view)?.apply {
            setBackgroundColor(Color.parseColor(jsonAd?.get()?.ctncolor))
        }
        adView.findViewById<NativeAdView?>(R.id.nativeAdView)
            ?.setBackgroundColor(Color.parseColor(jsonAd?.get()?.bgcolor))

        adView.bodyView = adView.findViewById<TextView?>(R.id.ad_body)?.apply {
            text = nativeAd.body
            visibility = if (nativeAd.body == null) View.GONE else View.VISIBLE
        }

        adView.iconView = adView.findViewById<ImageView?>(R.id.ad_app_icon)?.apply {
            setImageDrawable(nativeAd.icon?.drawable)
            visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
        }

        adView.callToActionView = adView.findViewById<Button?>(R.id.ad_call_to_action)?.apply {
            text = nativeAd.callToAction
            visibility = if (nativeAd.callToAction == null) View.GONE else View.VISIBLE

        }
        setSafeButtonTint(adView.callToActionView as Button, jsonAd?.get()?.btncolor);
        adView.mediaView = adView.findViewById<MediaView?>(R.id.ad_media)?.apply {
            setMediaContent(nativeAd.mediaContent)
        }

        adView.setNativeAd(nativeAd)
    }

    fun destroy() {
        nativeAd?.destroy()
        nativeAd = null
        isLoading = false
        isAdInflated = false
        containerRef = null
        shimmerRef = null
        layoutRes_ = null
    }
}
