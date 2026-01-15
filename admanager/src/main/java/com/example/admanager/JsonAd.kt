package com.example.admanager

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.Button
import androidx.annotation.Keep
import androidx.core.view.ViewCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


@Keep
data class JsonAd(
    val type: String,
    val id: String,
    val show: Boolean,
    val locationup: Boolean,
    val size: String,
    val screenname: String,
    val bgcolor: String,
    val ctncolor: String,
    val btncolor: String
)


fun parseAdConfig(jsonString: String): ArrayList<JsonAd> {
    if (jsonString.isBlank()) return arrayListOf()

    var cleanJson = jsonString.trim()

    // 🔥 Case 1: JSON is wrapped as a quoted string
    if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
        cleanJson = Gson().fromJson(cleanJson, String::class.java)
    }

    // 🔥 Case 2: Remove non-breaking spaces
    cleanJson = cleanJson.replace("\u00A0", " ")

    val type = object : TypeToken<ArrayList<JsonAd>>() {}.type
    return Gson().fromJson(cleanJson, type)
}

fun setSafeButtonTint(button: Button, color: String?) {
    if (button == null) return

    try {
        val clean = if (color == null)
            "#000000"
        else
            color.trim { it <= ' ' }.replace("\u00A0", "")

        val tint = ColorStateList.valueOf(Color.parseColor(clean))
        ViewCompat.setBackgroundTintList(button, tint)
    } catch (e: Exception) {
        ViewCompat.setBackgroundTintList(
            button,
            ColorStateList.valueOf(Color.BLACK)
        )
    }
}
