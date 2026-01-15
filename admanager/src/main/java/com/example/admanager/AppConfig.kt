package com.example.admanager

import android.util.Log
import com.google.firebase.database.FirebaseDatabase

var adslist: ArrayList<JsonAd>? = null
fun filterAdsByScreenName(adsList: List<JsonAd>, screenName: String): JsonAd? {
    val lowerScreenName = screenName.lowercase()
    return adsList.filter { it.screenname.lowercase() == lowerScreenName }?.get(0)
}

data class AppConfig(
    val inAppKey: String = "weekly_sub",
    val adShowInterval: Int = 15000,
    val sequencerCount: String = "3",
    val interstitial_ad_id: String = "",
    val interstitial_ad_id_splash_2nd_case: String = "",
    val sequencer: String = "ONE"
)

object AppConfigManager {
    private val db = FirebaseDatabase.getInstance().reference.child("appConfig")

    fun fetch(onResult: (AppConfig) -> Unit) {
        db.get()
            .addOnSuccessListener { snapshot ->
                val map = snapshot.value as? Map<String, Any>
                if (map != null) {
                    val config = AppConfig(
                        adShowInterval = (map["adShowInterval"] as? Long)?.toInt() ?: 1,
                        inAppKey = map["inAppKey"] as? String ?: "weekly_sub",
                        sequencerCount = map["sequencerCount"] as? String ?: "3",
                        interstitial_ad_id = map["interstitial_ad_id"] as? String ?: "",
                        interstitial_ad_id_splash_2nd_case = map["interstitial_ad_id_splash_2nd_case"] as? String ?: "",
                        sequencer = map["sequencer"] as? String ?: "ONE",
                    )
                    onResult(config)
                } else {
                    Log.e("AppConfigManager", "No config found, using defaults")
                    onResult(AppConfig())
                }
            }
            .addOnFailureListener { e ->
                Log.e("AppConfigManager", "Error fetching config", e)
                onResult(AppConfig())
            }
    }
}




