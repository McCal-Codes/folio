package com.mccal.folio

import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Control Center's volume and brightness sliders follow the phone: the mapping, the finger-wins rule and the observer's lifetime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemLevelsTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    // --- mapping ---

    @Test fun `a stream volume maps onto 0 to 1 between its min and max`() {
        assertEquals(0f, volumeFraction(0, 0, 15, muted = false), 0f)
        assertEquals(1f, volumeFraction(15, 0, 15, muted = false), 0f)
        assertEquals(.5f, volumeFraction(8, 1, 15, muted = false), .04f)
        assertEquals(0f, volumeFraction(1, 1, 7, muted = false), 0f)
    }

    @Test fun `a muted stream or a broken range reads as empty, and strays are clamped`() {
        assertEquals(0f, volumeFraction(10, 0, 15, muted = true), 0f)
        assertEquals(0f, volumeFraction(5, 3, 3, muted = false), 0f)
        assertEquals(1f, volumeFraction(99, 0, 15, muted = false), 0f)
        assertEquals(0f, volumeFraction(-4, 0, 15, muted = false), 0f)
    }

    @Test fun `the step for a slider position is the inverse and stays in range`() {
        assertEquals(0, volumeStep(0f, 0, 15))
        assertEquals(15, volumeStep(1f, 0, 15))
        assertEquals(7, volumeStep(.5f, 0, 15))
        assertEquals(1, volumeStep(0f, 1, 7))
        assertEquals(7, volumeStep(2f, 1, 7))
    }

    @Test fun `brightness maps the raw setting onto 0 to 1`() {
        assertEquals(0f, brightnessFraction(0), 0f)
        assertEquals(1f, brightnessFraction(255), 0f)
        assertEquals(1f, brightnessFraction(900), 0f)
        assertEquals(.5f, brightnessFraction(128), .01f)
    }

    // --- the finger wins ---

    @Test fun `an outside change moves the slider when nobody is touching it`() {
        val level = LiveLevel(.2f)
        assertTrue(level.fromOutside(.6f))
        assertEquals(.6f, level.value, 0f)
    }

    @Test fun `a change smaller than a step, or the same value again, changes nothing`() {
        val level = LiveLevel(.5f)
        assertFalse(level.fromOutside(.5f))
        assertFalse(level.fromOutside(.5f + LiveLevel.EPSILON / 2))
        assertEquals(.5f, level.value, 0f)
    }

    @Test fun `outside changes are ignored while the slider is held and the latest lands on release`() {
        val level = LiveLevel(.5f)
        level.hold()
        level.fromUser(.9f)
        assertFalse(level.fromOutside(.1f))
        assertFalse(level.fromOutside(.3f))
        assertEquals(.9f, level.value, 0f)
        assertEquals(.3f, level.release()!!, 0f)
        assertEquals(.3f, level.value, 0f)
        assertFalse(level.held)
    }

    @Test fun `releasing with nothing pending keeps what the finger set`() {
        val level = LiveLevel(.5f)
        level.hold()
        level.fromUser(.8f)
        assertNull(level.release())
        assertEquals(.8f, level.value, 0f)
    }

    @Test fun `a new hold forgets what arrived during the last one`() {
        val level = LiveLevel(.5f)
        level.hold(); level.fromOutside(.1f); level.hold()
        assertNull(level.release())
        assertEquals(.5f, level.value, 0f)
    }

    // --- the observer ---

    private fun audio() = context.getSystemService(AudioManager::class.java)

    @Test fun `a volume broadcast for music reads the stream once, however many arrive`() {
        val readings = mutableListOf<SystemReading>()
        val levels = SystemLevels(context) { readings += it }
        levels.start()
        audio().setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
        repeat(3) {
            context.sendBroadcast(Intent(SystemLevels.VOLUME_CHANGED_ACTION).putExtra(SystemLevels.EXTRA_STREAM_TYPE, AudioManager.STREAM_MUSIC))
        }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        // Bursts are coalesced into one reading (the poll tick is 250 ms away and idle() does not advance the clock).
        assertEquals(1, readings.size)
        assertEquals(volumeFraction(5, audio().getStreamMinVolume(AudioManager.STREAM_MUSIC), audio().getStreamMaxVolume(AudioManager.STREAM_MUSIC), false),
            readings.single().volume, 0f)
        levels.stop()
    }

    @Test fun `another stream's volume broadcast does not wake the slider`() {
        val readings = mutableListOf<SystemReading>()
        val levels = SystemLevels(context) { readings += it }
        levels.start()
        context.sendBroadcast(Intent(SystemLevels.VOLUME_CHANGED_ACTION).putExtra(SystemLevels.EXTRA_STREAM_TYPE, AudioManager.STREAM_RING))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(readings.isEmpty())
        levels.stop()
    }

    @Test fun `the poll reads about four times a second while started, and stop ends it and the receiver`() {
        val readings = mutableListOf<SystemReading>()
        val levels = SystemLevels(context) { readings += it }
        levels.start()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1000))
        assertEquals(4, readings.size)
        levels.stop()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2000))
        context.sendBroadcast(Intent(SystemLevels.VOLUME_CHANGED_ACTION))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("nothing may run after stop()", 4, readings.size)
        assertTrue(shadowOf(context).registeredReceivers.none { it.broadcastReceiver.javaClass.name.contains("SystemLevels") })
    }

    @Test fun `the poll pauses with the screen and resumes with it`() {
        val readings = mutableListOf<SystemReading>()
        val levels = SystemLevels(context) { readings += it }
        levels.start()
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2000))
        assertTrue(readings.isEmpty())
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
        assertTrue(readings.isNotEmpty())
        levels.stop()
    }

    @Test fun `a brightness change in Settings is picked up`() {
        val readings = mutableListOf<SystemReading>()
        val levels = SystemLevels(context) { readings += it }
        levels.start()
        android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 51)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(.2f, readings.last().brightness, .005f)
        levels.stop()
    }

    // --- the brightness mode (the Auto chip) ---

    @Test fun `the brightness mode reads as auto, manual or unknown`() {
        assertEquals(BrightnessMode.AUTO, brightnessModeOf(android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC))
        assertEquals(BrightnessMode.MANUAL, brightnessModeOf(android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL))
        assertEquals("a phone that does not say", BrightnessMode.UNKNOWN, brightnessModeOf(null))
        assertEquals(BrightnessMode.UNKNOWN, brightnessModeOf(7))
    }

    @Test fun `a drag has to switch to manual unless the phone is known to be on automatic`() {
        assertTrue(SystemReading(.5f, .5f, BrightnessMode.MANUAL).brightnessManual)
        assertTrue(SystemReading(.5f, .5f, BrightnessMode.UNKNOWN).brightnessManual)
        assertFalse(SystemReading(.5f, .5f, BrightnessMode.AUTO).brightnessManual)
    }

    @Test fun `automatic brightness switched on or off from outside is picked up`() {
        val settings = android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE
        android.provider.Settings.System.putInt(context.contentResolver, settings, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val readings = mutableListOf<SystemReading>()
        val levels = SystemLevels(context) { readings += it }
        assertEquals(BrightnessMode.MANUAL, levels.read().brightnessMode)
        levels.start()
        android.provider.Settings.System.putInt(context.contentResolver, settings, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(BrightnessMode.AUTO, readings.last().brightnessMode)
        android.provider.Settings.System.putInt(context.contentResolver, settings, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(BrightnessMode.MANUAL, readings.last().brightnessMode)
        levels.stop()
    }
}
