package com.phantom.kuchupuchi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PresenceTest {

    private fun parsePresenceData(dataMap: Map<String, Any?>?): Pair<String, Long> {
        if (dataMap == null) return "offline" to 0L

        val statusStr = dataMap["status"] as? String ?: "offline"
        val status = when (statusStr.lowercase(Locale.ROOT)) {
            "active" -> "active"
            "away" -> "away"
            "offline" -> "offline"
            else -> "offline"
        }

        val lastSeenRaw = dataMap["lastSeen"]
        val lastSeen = when (lastSeenRaw) {
            is Long -> lastSeenRaw
            is Double -> lastSeenRaw.toLong()
            is Number -> lastSeenRaw.toLong()
            else -> 0L
        }

        return status to lastSeen
    }

    @Test
    fun parsePresenceData_activeStatus() {
        val map = mapOf<String, Any?>(
            "status" to "active",
            "lastSeen" to 1700000000000L,
        )
        val (status, lastSeen) = parsePresenceData(map)
        assertEquals("active", status)
        assertEquals(1700000000000L, lastSeen)
    }

    @Test
    fun parsePresenceData_awayStatus() {
        val map = mapOf<String, Any?>(
            "status" to "away",
            "lastSeen" to 1700000500000L,
        )
        val (status, lastSeen) = parsePresenceData(map)
        assertEquals("away", status)
        assertEquals(1700000500000L, lastSeen)
    }

    @Test
    fun parsePresenceData_offlineStatus() {
        val map = mapOf<String, Any?>(
            "status" to "offline",
            "lastSeen" to 1700001000000L,
        )
        val (status, lastSeen) = parsePresenceData(map)
        assertEquals("offline", status)
        assertEquals(1700001000000L, lastSeen)
    }

    @Test
    fun parsePresenceData_nullOrMissingDataDefaultsToOffline() {
        val (status, lastSeen) = parsePresenceData(null)
        assertEquals("offline", status)
        assertEquals(0L, lastSeen)
    }
}
