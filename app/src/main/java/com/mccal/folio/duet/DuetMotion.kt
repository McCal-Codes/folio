package com.mccal.folio.duet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/**
 * Tilt that moves Duo Clip's folding half, while the hinge moves: the glass shifts as the phone is turned, as if seen
 * from a slightly different place. The idea, the dead zone, the smoothing and the settle-then-hold reference are from Duo
 * Fold Live's "motion glass" (MIT, Duo Fold Live contributors; see third_party/duet/PROVENANCE.md). Only relative tilt is
 * read, from the game rotation vector, which needs no permission; nothing is kept, sent or written.
 *
 * [x] and [y] are Compose state, so a drawing block that reads them redraws as the phone moves. Run it only while a fold
 * plays: [run] true starts the sensor, false stops it and puts both back to zero.
 */
internal class DuetMotion(context: Context) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR, false)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR, false)

    var x by mutableFloatStateOf(0f); private set
    var y by mutableFloatStateOf(0f); private set

    private var running = false
    private var baseline = false
    private var startNs = 0L
    private var lastNs = 0L
    private var stableSince = 0L
    private val matrix = FloatArray(9)
    private val reference = FloatArray(9)
    private val delta = FloatArray(3)

    fun run(on: Boolean) {
        if (on == running) return
        if (!on) {
            manager?.unregisterListener(this)
            running = false; baseline = false; lastNs = 0L
            x = 0f; y = 0f
            return
        }
        val s = sensor ?: return
        baseline = false; lastNs = 0L
        running = manager?.registerListener(this, s, SAMPLE_US) == true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.timestamp <= lastNs || event.values.any { !it.isFinite() }) return
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        // A gap, or the very first sample, starts the reference again.
        if (lastNs == 0L || event.timestamp - lastNs > GAP_NS) {
            matrix.copyInto(reference); startNs = event.timestamp; stableSince = event.timestamp
            baseline = false; lastNs = event.timestamp; x = 0f; y = 0f
            return
        }
        SensorManager.getAngleChange(delta, matrix, reference)
        if (!delta[1].isFinite() || !delta[2].isFinite()) return
        val dt = ((event.timestamp - lastNs) / 1_000_000f).coerceIn(1f, 100f)
        lastNs = event.timestamp
        if (!baseline) {
            // Ignore the tug of the hand starting the fold: follow it until it settles, then hold that as neutral.
            if (delta.any { abs(it) > SETTLE_RAD }) { matrix.copyInto(reference); stableSince = event.timestamp }
            if (DuetMotionMath.settled(event.timestamp - startNs, event.timestamp - stableSince)) {
                matrix.copyInto(reference); baseline = true
            }
            return
        }
        x = DuetMotionMath.smooth(x, DuetMotionMath.offset(DuetMotionMath.deadZone(delta[2])), dt, SMOOTH_MS)
        y = DuetMotionMath.smooth(y, -DuetMotionMath.offset(DuetMotionMath.deadZone(delta[1])), dt, SMOOTH_MS)
    }

    private companion object {
        const val SAMPLE_US = 16_667
        const val GAP_NS = 500_000_000L
        const val SETTLE_RAD = .006108652f
        const val SMOOTH_MS = 90f
    }
}

/** The arithmetic behind [DuetMotion], kept apart so it can be tested without a sensor. */
internal object DuetMotionMath {
    /** Zero for tiny turns, the normal slope beyond 3 degrees, and smooth in between. */
    fun deadZone(radians: Float): Float {
        if (!radians.isFinite()) return 0f
        val d = .02617994f
        val a = abs(radians)
        if (a <= d) return 0f
        val t = ((a - d) / d).coerceIn(0f, 1f)
        return sign(radians) * (a - d) * t * t * (3f - 2f * t)
    }

    /** A turn as a share of the shader's range: 30 degrees is .5, and it stops at 60. */
    fun offset(radians: Float): Float = if (radians.isFinite()) (radians / .5235988f).coerceIn(-2f, 2f) * .5f else 0f

    fun smooth(current: Float, target: Float, dtMs: Float, ms: Float): Float =
        if (ms <= 0f) target else current + (target - current) * (1f - exp(-dtMs / ms))

    /** The reference is ready after 350 ms, or after 120 ms once the hand has been still for 80 (both in nanoseconds here). */
    fun settled(ageNs: Long, stillNs: Long): Boolean = ageNs >= 350_000_000L || (ageNs >= 120_000_000L && stillNs >= 80_000_000L)

    /** Turns the tilt into screen axes for the display's rotation (Surface.ROTATION_*). */
    fun rotate(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        1 -> y to -x
        2 -> -x to -y
        3 -> -y to x
        else -> x to y
    }

    /** The folding half's distance from the hinge is scaled by this, for a tilt along the fold: 30 degrees is about 1.41, and it stays within 1/4 to 4. */
    fun gain(along: Float): Float = Math.pow(2.0, along.coerceIn(-2f, 2f).toDouble()).toFloat()
}
