package com.phantom.kuchupuchi.updater

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun testParseVersionCode() {
        val dummyContext = object : ContextWrapper(null) {}
        val updateManager = UpdateManager(dummyContext)

        assertEquals(2, updateManager.parseVersionCode("v2"))
        assertEquals(2, updateManager.parseVersionCode("2"))
        assertEquals(10, updateManager.parseVersionCode("v10"))
        assertEquals(123, updateManager.parseVersionCode("v1.2.3"))
        assertEquals(0, updateManager.parseVersionCode("no_digits"))
    }
}
