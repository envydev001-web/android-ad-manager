package com.example.admanager

import android.app.Activity
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerFrameLayout


class AdWrapperManager private constructor(
    private val context: Activity,
    private var isPremium: Boolean
) {

    companion object {
        private const val TAG = "NativeAdManager"

        @Volatile
        private var instance: AdWrapperManager? = null

        fun getInstance(
            context: android.content.Context,
            isPremium: Boolean = false
        ): AdWrapperManager {
            return instance?.apply { this.isPremium = isPremium } ?: synchronized(this) {
                instance ?: AdWrapperManager(
                    context as Activity,
                    isPremium
                ).also {
                    instance = it
                }
            }
        }
    }

    fun loadAndShowAd(
        jsonAd: JsonAd,
        topContainer: FrameLayout,
        bottomContainer: FrameLayout,
        topshimmer: ShimmerFrameLayout,
        bottomshimmer: ShimmerFrameLayout,
        isNative: Boolean,
        isPremium: Boolean
    ) {
        // 2. Choose the container
        val adContainer = if (jsonAd.locationup) topContainer else bottomContainer
        val shimmer = if (jsonAd.locationup) topshimmer else bottomshimmer
        adContainer.visibility = View.VISIBLE

        // 3. Map size code to actual dimensions
        val (height, width) = when (jsonAd.size.toInt()) {
            1 -> 120 to null//small
            2 -> 120 to null//adaptive
            3 -> 520 to null//rectangle
            4 -> 120 to null//collapsable
            5 -> 150 to null//small native
            6 -> 555 to null//large native
            else -> 100 to null // default fallback
        }
        val bannertype = when (jsonAd.size.toInt()) {
            1 -> BannerType.BANNER//small banner
            2 -> BannerType.ADAPTIVE
            3 -> BannerType.MEDIUM_RECTANGLE
            4 -> BannerType.COLLAPSIBLE // collapsible uses adaptive
            else -> BannerType.ADAPTIVE
        }
        if (!jsonAd.show) {
            topContainer.visibility = View.GONE
            bottomContainer.visibility = View.GONE

//            return
        }
        // 4. Apply size to container
        val params = adContainer.layoutParams
        val paramsshimmar = shimmer.layoutParams
        params.height = height
        paramsshimmar.height = height
        if (width != null) {
            params.width = width
            paramsshimmar.width = width
        }
        adContainer.layoutParams = params
        shimmer.layoutParams = paramsshimmar
        adContainer.visibility = View.VISIBLE
        shimmer.visibility = View.VISIBLE
        // 5. Set container background color
        var colorStr: String? = jsonAd.bgcolor // from your JsonAd object


// Trim, remove non-breaking spaces, fallback to black if null
        if (colorStr != null) {
            colorStr = colorStr.trim { it <= ' ' }.replace("\u00A0", "")
        } else {
            colorStr = "#fff"
        }


// Try-catch to avoid crash on invalid color
        try {
            adContainer.setBackgroundColor(Color.parseColor(colorStr))
        } catch (e: IllegalArgumentException) {
            Log.e("AD_COLOR", "Invalid color: " + colorStr, e)
            adContainer.setBackgroundColor(Color.WHITE) // fallback
        }


        // 6. Load the ad
        if (isNative) {
            NativeAdManager.getInstance(context, isPremium)
                .loadAndShowAd(jsonAd.id,jsonAd.show)
        } else {
            shimmer.visibility = View.GONE
            val bannerAdManager = BannerAdManager()
            bannerAdManager.loadBannerAd(context, jsonAd.id, adContainer,bannertype, canRequestBannerAd = jsonAd.show)
        }
    }


}