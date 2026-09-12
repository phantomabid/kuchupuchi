package com.phantom.kuchupuchi

import com.phantom.kuchupuchi.config.FlavorConfig
import com.phantom.kuchupuchi.service.FloatingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlavorConfigTest {

    @Test
    fun flavorConfig_pathsAreNotNull() {
        assertNotNull(FlavorConfig.getSelfDbPath())
        assertNotNull(FlavorConfig.getPartnerDbPath())
        assertNotNull(FlavorConfig.selfDbPath)
        assertNotNull(FlavorConfig.partnerDbPath)
        assertNotNull(FlavorConfig.firebaseDbUrl)
        assertNotEquals(0, FlavorConfig.getIconResId())
    }

    @Test
    fun flavorConfig_birdIconResIds() {
        assertNotEquals(0, FlavorConfig.getSelfIconResId("active"))
        assertNotEquals(0, FlavorConfig.getSelfIconResId("away"))
        assertNotEquals(0, FlavorConfig.getSelfIconResId("offline"))
        assertNotEquals(0, FlavorConfig.getPartnerIconResId("active"))
        assertNotEquals(0, FlavorConfig.getPartnerIconResId("away"))
        assertNotEquals(0, FlavorConfig.getPartnerIconResId("offline"))

        assertNotEquals(FlavorConfig.getSelfIconResId("active"), FlavorConfig.getSelfIconResId("away"))
        assertNotEquals(FlavorConfig.getSelfIconResId("active"), FlavorConfig.getSelfIconResId("offline"))
        assertNotEquals(FlavorConfig.getPartnerIconResId("active"), FlavorConfig.getPartnerIconResId("away"))
        assertNotEquals(FlavorConfig.getPartnerIconResId("active"), FlavorConfig.getPartnerIconResId("offline"))
    }

    @Test
    fun floatingService_touchStateListener() {
        var receivedState: Boolean? = null
        FloatingService.touchStateListener = { active ->
            receivedState = active
        }

        FloatingService.setTouchActive(true)
        assertTrue(FloatingService.isTouchActive)
        assertEquals(true, receivedState)

        FloatingService.setTouchActive(false)
        assertFalse(FloatingService.isTouchActive)
        assertEquals(false, receivedState)

        FloatingService.touchStateListener = null
    }
}
