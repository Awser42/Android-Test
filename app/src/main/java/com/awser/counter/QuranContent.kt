package com.awser.counter

import android.content.Context
import org.json.JSONObject

/** Verified, attributed Arabic passages bundled for offline reading. No network is used. */
object QuranContent {
    data class Reading(val id: String, val title: String, val reference: String, val assetName: String)
    data class Verse(val number: Int, val text: String)

    const val ATTRIBUTION = "Arabic text: Tanzil Project • https://tanzil.net • CC BY 3.0"
    const val SOURCE_URL = "https://tanzil.net"
    const val LICENSE_URL = "https://creativecommons.org/licenses/by/3.0/"
    const val NOTICE_ASSET_NAME = "quran/NOTICE.txt"

    val readings: List<Reading> = listOf(
        Reading("yaseen", "Surah Yaseen", "Qur’an 36:1–83", "quran/yaseen.json"),
        Reading("ayat_kareema", "Ayat-e-Karima", "Qur’an 21:87 · Complete verse", "quran/ayat_kareema.json"),
        Reading("ayatul_kursi", "Ayatul Kursi", "Qur’an 2:255", "quran/ayatul_kursi.json"),
    )

    fun load(context: Context, id: String): List<Verse> {
        val json = readAsset(context, id)
        val rows = json.getJSONArray("verses")
        val result = (0 until rows.length()).map { index ->
            val entry = rows.getJSONObject(index)
            Verse(entry.getInt("number"), entry.getString("text"))
        }
        val expectedNumbers = when (id) {
            "yaseen" -> (1..83).toList()
            "ayat_kareema" -> listOf(87)
            "ayatul_kursi" -> listOf(255)
            else -> error("Unknown Quran reading")
        }
        check(result.map { it.number } == expectedNumbers && result.all { it.text.isNotBlank() }) {
            "The bundled Quran passage is incomplete."
        }
        return result
    }

    /** The source's chapter-opening basmala, displayed once outside the numbered verses. */
    fun basmala(context: Context, id: String): String? {
        val json = readAsset(context, id)
        return if (json.has("basmala")) json.getString("basmala").takeIf { it.isNotBlank() } else null
    }

    private fun readAsset(context: Context, id: String): JSONObject {
        val reading = readings.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown Quran reading: $id")
        return context.assets.open(reading.assetName).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }
}
