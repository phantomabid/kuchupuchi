package com.phantom.kuchupuchi

import com.phantom.kuchupuchi.service.VibrationPattern
import com.phantom.kuchupuchi.service.VibrationPatternHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VibrationPatternTest {

    @Test
    fun vibrationPattern_fromId() {
        assertEquals(VibrationPattern.CONSTANT, VibrationPattern.fromId("CONSTANT"))
        assertEquals(VibrationPattern.CONSTANT, VibrationPattern.fromId("constant"))
        assertEquals(VibrationPattern.PULSE, VibrationPattern.fromId("PULSE"))
        assertEquals(VibrationPattern.HEARTBEAT, VibrationPattern.fromId("HeartBeat"))
        assertEquals(VibrationPattern.WAVE, VibrationPattern.fromId("WAVE"))
        assertEquals(VibrationPattern.RHYTHM, VibrationPattern.fromId("RHYTHM"))
        assertEquals(VibrationPattern.HEARTBEAT, VibrationPattern.fromId("UNKNOWN"))
        assertEquals(VibrationPattern.HEARTBEAT, VibrationPattern.fromId(null))
    }

    @Test
    fun computeAmplitude_alwaysReturnsMax255() {
        assertEquals(255, VibrationPatternHelper.computeAmplitude(0.0f))
        assertEquals(255, VibrationPatternHelper.computeAmplitude(0.5f))
        assertEquals(255, VibrationPatternHelper.computeAmplitude(1.0f))
        assertEquals(255, VibrationPatternHelper.computeAmplitude(-0.5f))
    }

    @Test
    fun createWaveform_constantPattern() {
        val waveform = VibrationPatternHelper.createWaveform("CONSTANT", 1.0f)
        assertNotNull(waveform)
        assertEquals(0, waveform.repeat)
        assertArrayEquals(longArrayOf(0, 500), waveform.timings)
        assertArrayEquals(intArrayOf(0, 255), waveform.amplitudes)
    }

    @Test
    fun createWaveform_pulsePattern() {
        val waveform = VibrationPatternHelper.createWaveform("PULSE", 0.8f)
        assertNotNull(waveform)
        assertEquals(0, waveform.repeat)
        assertArrayEquals(longArrayOf(0, 200, 200), waveform.timings)
        assertArrayEquals(intArrayOf(0, 255, 0), waveform.amplitudes)
    }

    @Test
    fun createWaveform_heartbeatPattern() {
        val waveform = VibrationPatternHelper.createWaveform("HEARTBEAT", 1.0f)
        assertNotNull(waveform)
        assertEquals(0, waveform.repeat)
        assertArrayEquals(longArrayOf(0, 120, 100, 160, 400), waveform.timings)
        assertArrayEquals(intArrayOf(0, 255, 0, 255, 0), waveform.amplitudes)
    }

    @Test
    fun createWaveform_wavePattern() {
        val waveform = VibrationPatternHelper.createWaveform("WAVE", 1.0f)
        assertNotNull(waveform)
        assertEquals(0, waveform.repeat)
        assertArrayEquals(longArrayOf(0, 150, 150, 150, 150), waveform.timings)
        assertArrayEquals(intArrayOf(0, 255, 255, 255, 255), waveform.amplitudes)
    }

    @Test
    fun createWaveform_rhythmPattern() {
        val waveform = VibrationPatternHelper.createWaveform("RHYTHM", 0.5f)
        assertNotNull(waveform)
        assertEquals(0, waveform.repeat)
        assertArrayEquals(longArrayOf(0, 150, 100, 150, 100, 300), waveform.timings)
        assertArrayEquals(intArrayOf(0, 255, 0, 255, 0, 255), waveform.amplitudes)
    }
}
