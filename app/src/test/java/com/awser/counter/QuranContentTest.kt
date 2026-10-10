package com.awser.counter

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuranContentTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun eachOfflineReadingContainsTheCompleteExpectedVerseRange() {
        assertEquals(listOf("yaseen", "ayat_kareema", "ayatul_kursi"), QuranContent.readings.map { it.id })
        assertEquals((1..83).toList(), QuranContent.load(context, "yaseen").map { it.number })
        assertEquals(listOf(87), QuranContent.load(context, "ayat_kareema").map { it.number })
        assertEquals(listOf(255), QuranContent.load(context, "ayatul_kursi").map { it.number })
        QuranContent.readings.forEach { reading ->
            assertTrue(reading.title.isNotBlank())
            assertTrue(reading.reference.isNotBlank())
            assertTrue(QuranContent.load(context, reading.id).all { it.text.isNotBlank() })
        }
    }

    @Test fun knownVerseBoundariesIncludeTheNarrativeOpeningOfTheCompleteKarimaVerse() {
        val yaseen = QuranContent.load(context, "yaseen")
        assertEquals("يسٓ", yaseen.first().text)
        assertEquals("وَٱلْقُرْءَانِ ٱلْحَكِيمِ", yaseen[1].text)
        assertEquals("فَسُبْحَٰنَ ٱلَّذِى بِيَدِهِۦ مَلَكُوتُ كُلِّ شَىْءٍ وَإِلَيْهِ تُرْجَعُونَ", yaseen.last().text)

        val karima = QuranContent.load(context, "ayat_kareema").single().text
        assertTrue(karima.startsWith("وَذَا ٱلنُّونِ إِذ ذَّهَبَ"))
        assertTrue(karima.endsWith("إِنِّى كُنتُ مِنَ ٱلظَّٰلِمِينَ"))
        val kursi = QuranContent.load(context, "ayatul_kursi").single().text
        assertTrue(kursi.startsWith("ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ"))
        assertTrue(kursi.endsWith("وَهُوَ ٱلْعَلِىُّ ٱلْعَظِيمُ"))
    }

    @Test fun everyVerseMatchesTheVerifiedUneditedTanzilSnapshot() {
        // Hashes of "number|text" rows joined by LF, verified against the pinned source.
        val expected = mapOf(
            "yaseen" to "1e1ad5682d4c84a8172f6f93abb224730b26c365eb2a42a982f059e986aaaf20",
            "ayat_kareema" to "7207b64ca05145cb4ba7f2e4792737f63ca06fac7d12cf29f54f916d8bbf87a7",
            "ayatul_kursi" to "3df9d2a6aebbed9381eaa409920cd15402afe8ed7b5f1ee813ff357fbdd4fb14",
        )
        expected.forEach { (id, checksum) ->
            val canonical = QuranContent.load(context, id).joinToString("\n") { "${it.number}|${it.text}" }
            val actual = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals("All Arabic codepoints and complete text preserved for $id", checksum, actual)
        }
    }

    @Test fun basmalaAppearsOnceOutsideTheNumberedYaseenVerses() {
        val basmala = QuranContent.basmala(context, "yaseen")
        assertEquals("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ", basmala)
        assertFalse(QuranContent.load(context, "yaseen").any { it.text.contains(basmala!!) })
        assertEquals("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ يسٓ", "$basmala ${QuranContent.load(context, "yaseen").first().text}")
        assertNull(QuranContent.basmala(context, "ayat_kareema"))
        assertNull(QuranContent.basmala(context, "ayatul_kursi"))
    }

    @Test fun everyDistributedTextFileRetainsItsCopyrightAndLicenseNotice() {
        QuranContent.readings.forEach { reading ->
            val asset = context.assets.open(reading.assetName).bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            assertEquals("Tanzil Project", asset.getString("source"))
            assertEquals(QuranContent.SOURCE_URL, asset.getString("sourceUrl"))
            assertEquals(QuranContent.LICENSE_URL, asset.getString("licenseUrl"))
            assertTrue(asset.getString("notice").contains("Copyright (C) 2007-2021 Tanzil Project"))
            assertTrue(asset.getString("notice").contains("CHANGING IT IS NOT ALLOWED"))
        }
        val notice = context.assets.open(QuranContent.NOTICE_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(notice.contains(QuranContent.SOURCE_URL))
        assertTrue(notice.contains("Risan Bagja Pradana"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedReadingIdsCannotSelectArbitraryAssetPaths() {
        QuranContent.load(context, "../../unknown")
    }
}
