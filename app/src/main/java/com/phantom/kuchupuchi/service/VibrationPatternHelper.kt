package com.phantom.kuchupuchi.service

import androidx.annotation.Keep

@Keep
enum class VibrationPattern(
    val id: String,
    val displayName: String,
    val description: String,
) {
    CONSTANT("CONSTANT", "Smooth Constant", "Continuous smooth vibration"),
    PULSE("PULSE", "Pulsing Beat", "Steady pulsating bursts"),
    HEARTBEAT("HEARTBEAT", "Heartbeat Rhythm", "Double-thump heartbeat pattern"),
    WAVE("WAVE", "Ascending Wave", "Swell and fade wave vibration"),
    RHYTHM("RHYTHM", "Rhythmic Pulse", "Dynamic rhythmic pulses");

    companion object {
        fun fromId(id: String?): VibrationPattern {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: HEARTBEAT
        }
    }
}

data class VibrationWaveform(
    val timings: LongArray,
    val amplitudes: IntArray,
    val repeat: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VibrationWaveform) return false
        return (timings.contentEquals(other.timings)) &&
            (amplitudes.contentEquals(other.amplitudes)) &&
            (repeat == other.repeat)
    }

    override fun hashCode(): Int {
        var result = timings.contentHashCode()
        result = 31 * result + amplitudes.contentHashCode()
        result = 31 * result + repeat
        return result
    }
}

object VibrationPatternHelper {

    @Suppress("UNUSED_PARAMETER")
    fun computeAmplitude(volume: Float): Int {
        return 255
    }

    fun createWaveform(patternId: String?, volume: Float): VibrationWaveform {
        val pattern = VibrationPattern.fromId(patternId)
        val amplitude = computeAmplitude(volume)

        return when (pattern) {
            VibrationPattern.CONSTANT -> {
                VibrationWaveform(
                    timings = longArrayOf(0, 500),
                    amplitudes = intArrayOf(0, amplitude),
                    repeat = 0,
                )
            }
            VibrationPattern.PULSE -> {
                VibrationWaveform(
                    timings = longArrayOf(0, 200, 200),
                    amplitudes = intArrayOf(0, amplitude, 0),
                    repeat = 0,
                )
            }
            VibrationPattern.HEARTBEAT -> {
                VibrationWaveform(
                    timings = longArrayOf(0, 120, 100, 160, 400),
                    amplitudes = intArrayOf(0, amplitude, 0, amplitude, 0),
                    repeat = 0,
                )
            }
            VibrationPattern.WAVE -> {
                VibrationWaveform(
                    timings = longArrayOf(0, 150, 150, 150, 150),
                    amplitudes = intArrayOf(0, amplitude, amplitude, amplitude, amplitude),
                    repeat = 0,
                )
            }
            VibrationPattern.RHYTHM -> {
                VibrationWaveform(
                    timings = longArrayOf(0, 150, 100, 150, 100, 300),
                    amplitudes = intArrayOf(0, amplitude, 0, amplitude, 0, amplitude),
                    repeat = 0,
                )
            }
        }
    }
}
