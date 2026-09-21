package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs on Robolectric because pinyin comes from Android's ICU data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PinyinTest {
    @Test fun chineseNamesSpellInPinyin() {
        assertEquals(listOf("wei", "xin"), Pinyin.syllables("微信"))
        assertEquals(listOf("zhi", "fu", "bao"), Pinyin.syllables("支付宝"))
        assertTrue(Pinyin.syllables("Spotify").isEmpty())
    }

    @Test fun headingsUseThePinyinInitial() {
        assertEquals("W", Pinyin.heading("微信"))
        assertEquals("Z", Pinyin.heading("支付宝"))
        assertEquals("S", Pinyin.heading("spotify"))
        assertEquals("#", Pinyin.heading("1Password"))
    }

    @Test fun lettersFindChineseNames() {
        assertTrue(Pinyin.matches("微信", "weixin"))
        assertTrue(Pinyin.matches("微信", "wei"))
        assertTrue(Pinyin.matches("微信", "xin"))
        assertTrue(Pinyin.matches("支付宝", "zfb"))
        assertFalse(Pinyin.matches("微信", "zfb"))
        assertFalse(Pinyin.matches("微信", "微"))
        assertFalse(Pinyin.matches("Spotify", "spo"))
    }

    @Test fun spotlightRanksPinyinWithTheRest() {
        val names = listOf("Spotify", "支付宝", "微信", "WeatherBug")
        assertEquals(listOf("支付宝"), rankByLabel(names, "zfb") { it })
        assertEquals("微信", rankByLabel(names, "weixin") { it }.first())
        assertEquals(listOf("微信"), rankByLabel(names, "wx") { it })
        assertEquals(listOf("微信"), rankByLabel(names, "微") { it })
    }
}
